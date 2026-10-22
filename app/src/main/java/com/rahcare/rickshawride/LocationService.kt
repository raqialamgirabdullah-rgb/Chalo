package com.rahcare.rickshawride

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow

/** চালকের অনলাইন অবস্থা ও সর্বশেষ অবস্থান, অ্যাপের সব জায়গা থেকে দেখা যায়। */
object LocationHub {
    val running = MutableStateFlow(false)
    val location = MutableStateFlow<Pt?>(null)
}

class LocationService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var vehicle: String = ""
    private var lastWritten: Pt? = null
    private var lastWriteAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("chalo", MODE_PRIVATE)
        val fromIntent = intent?.getStringExtra(EXTRA_VEHICLE)
        if (fromIntent != null) prefs.edit().putString("vehicle", fromIntent).apply()
        vehicle = prefs.getString("vehicle", "") ?: ""

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPerm || FirebaseAuth.getInstance().currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (callback == null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 7_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .build()
            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val l = result.lastLocation ?: return
                    onNewLocation(l)
                }
            }
            try {
                client.requestLocationUpdates(request, cb, Looper.getMainLooper())
                callback = cb
                LocationHub.running.value = true
            } catch (e: SecurityException) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun onNewLocation(l: Location) {
        val pt = Pt(l.latitude, l.longitude)
        LocationHub.location.value = pt

        // Firestore-এর ফ্রি সীমা বাঁচাতে: ৮ মিটারের বেশি নড়লে অথবা ৩০ সেকেন্ডে একবার লেখা হয়।
        val now = SystemClock.elapsedRealtime()
        val last = lastWritten
        val moved = last == null || Geo.straightKm(last, pt) * 1000.0 >= 8.0
        if (!moved && now - lastWriteAt < 30_000L) return
        lastWritten = pt
        lastWriteAt = now

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = hashMapOf<String, Any>(
            "online" to true,
            "lat" to pt.lat,
            "lng" to pt.lng,
            "vehicleType" to vehicle,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        FirebaseFirestore.getInstance().collection("drivers").document(uid)
            .set(data, SetOptions.merge())
    }

    override fun onDestroy() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        LocationHub.running.value = false
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("drivers").document(uid)
                .set(
                    mapOf("online" to false, "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge(),
                )
        }
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "অনলাইন অবস্থা", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_foreground)
            .setContentTitle("চলো — আপনি অনলাইন আছেন")
            .setContentText("নতুন রাইডের জন্য আপনার অবস্থান জানানো হচ্ছে")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val EXTRA_VEHICLE = "vehicle"
        private const val CHANNEL_ID = "online_status"
        private const val NOTIF_ID = 1001
    }
}
