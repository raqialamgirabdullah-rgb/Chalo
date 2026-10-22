package com.rahcare.rickshawride

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore ও Auth-এর সব কাজ এই এক ফাইলে। */
object Repo {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()


    // =====================================================================
    // সামনের দরজা: AppConfig.LOCAL_MODE অনুযায়ী এই ফোনের স্টোর অথবা Firestore ব্যবহার হয়।
    // =====================================================================

    fun currentUid(): String? =
        if (AppConfig.LOCAL_MODE) LocalStore.uid() else auth.currentUser?.uid

    suspend fun ensureSignedIn(): String =
        if (AppConfig.LOCAL_MODE) LocalStore.uid() else fsEnsureSignedIn()

    suspend fun loadProfile(uid: String): Profile? =
        if (AppConfig.LOCAL_MODE) LocalStore.loadProfile() else fsLoadProfile(uid)

    suspend fun saveProfile(uid: String, p: Profile) {
        if (AppConfig.LOCAL_MODE) LocalStore.saveProfile(p) else fsSaveProfile(uid, p)
    }

    suspend fun createRide(
        uid: String,
        p: Profile,
        v: VehicleType,
        pickup: Pt,
        dest: Pt,
        km: Double,
        fare: Int,
    ): String =
        if (AppConfig.LOCAL_MODE) LocalStore.createRide(uid, p, v, pickup, dest, km, fare)
        else fsCreateRide(uid, p, v, pickup, dest, km, fare)

    suspend fun acceptRide(rideId: String, uid: String, p: Profile): Boolean =
        if (AppConfig.LOCAL_MODE) LocalStore.acceptRide(rideId, uid, p) else fsAcceptRide(rideId, uid, p)

    suspend fun setRideStatus(rideId: String, status: String) {
        if (AppConfig.LOCAL_MODE) LocalStore.setRideStatus(rideId, status) else fsSetRideStatus(rideId, status)
    }

    suspend fun cancelRide(rideId: String, by: String, onlyIfRequested: Boolean = false): Boolean =
        if (AppConfig.LOCAL_MODE) LocalStore.cancelRide(rideId, by, onlyIfRequested)
        else fsCancelRide(rideId, by, onlyIfRequested)

    fun observeMyRides(field: String, uid: String): Flow<List<Ride>> =
        if (AppConfig.LOCAL_MODE) LocalStore.observeMyRides(field, uid) else fsObserveMyRides(field, uid)

    fun observeRequested(vehicleKey: String): Flow<List<Ride>> =
        if (AppConfig.LOCAL_MODE) LocalStore.observeRequested(vehicleKey) else fsObserveRequested(vehicleKey)

    fun observeContact(rideId: String, who: String): Flow<Contact?> =
        if (AppConfig.LOCAL_MODE) LocalStore.observeContact(rideId, who) else fsObserveContact(rideId, who)

    fun observeDriverLoc(driverId: String): Flow<Pt?> =
        if (AppConfig.LOCAL_MODE) LocalStore.observeDriverLoc(driverId) else fsObserveDriverLoc(driverId)

    suspend fun setDriverOffline(uid: String) {
        if (AppConfig.LOCAL_MODE) LocalStore.removeDriverLoc(uid) else fsSetDriverOffline(uid)
    }

    /** চালকের নতুন অবস্থান পাঠানো (অপেক্ষা না করে)। */
    fun writeDriverLocation(uid: String, pt: Pt, vehicle: String) {
        if (AppConfig.LOCAL_MODE) {
            LocalStore.setDriverLoc(uid, pt)
            return
        }
        val data = hashMapOf<String, Any>(
            "online" to true,
            "lat" to pt.lat,
            "lng" to pt.lng,
            "vehicleType" to vehicle,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        db.collection("chalo_drivers").document(uid).set(data, SetOptions.merge())
    }

    /** সার্ভিস বন্ধ হওয়ার সময় চালককে অফলাইন চিহ্নিত করা (অপেক্ষা না করে)। */
    fun writeDriverOfflineQuiet(uid: String) {
        if (AppConfig.LOCAL_MODE) {
            LocalStore.removeDriverLoc(uid)
            return
        }
        db.collection("chalo_drivers").document(uid).set(
            mapOf("online" to false, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    /** শুধু পরীক্ষার মোডে: চালকের জন্য নকল যাত্রীর রিকোয়েস্ট। */
    fun createDemoRequest(near: Pt, vehicleKey: String) {
        if (AppConfig.LOCAL_MODE) LocalStore.createDemoRequest(near, vehicleKey)
    }

    // ---------- লগইন ----------
    private suspend fun fsEnsureSignedIn(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        val res = auth.signInAnonymously().await()
        return res.user?.uid ?: throw IllegalStateException("সাইন-ইন হয়নি")
    }

    // ---------- প্রোফাইল ----------
    private suspend fun fsLoadProfile(uid: String): Profile? {
        val s = db.collection("chalo_users").document(uid).get().await()
        if (!s.exists()) return null
        return Profile(
            name = s.getString("name") ?: "",
            phone = s.getString("phone") ?: "",
            role = s.getString("role") ?: "",
            vehicleType = s.getString("vehicleType") ?: "",
        )
    }

    private suspend fun fsSaveProfile(uid: String, p: Profile) {
        val data = hashMapOf<String, Any>(
            "name" to p.name,
            "phone" to p.phone,
            "role" to p.role,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (p.role == "driver") data["vehicleType"] = p.vehicleType
        db.collection("chalo_users").document(uid).set(data, SetOptions.merge()).await()
    }

    // ---------- রাইড ডাকা ----------
    private suspend fun fsCreateRide(
        uid: String,
        p: Profile,
        v: VehicleType,
        pickup: Pt,
        dest: Pt,
        km: Double,
        fare: Int,
    ): String {
        val ref = db.collection("chalo_rides").document()
        val ride = hashMapOf<String, Any?>(
            "status" to RideStatus.REQUESTED,
            "passengerId" to uid,
            "passengerName" to p.name,
            "driverId" to null,
            "vehicleType" to v.key,
            "pickupLat" to pickup.lat,
            "pickupLng" to pickup.lng,
            "destLat" to dest.lat,
            "destLng" to dest.lng,
            "distanceKm" to km,
            "fare" to fare,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        val batch = db.batch()
        batch.set(ref, ride)
        // ফোন নম্বর আলাদা জায়গায়: রাইড গৃহীত হওয়ার পরেই শুধু চালক দেখতে পাবে।
        batch.set(
            ref.collection("contacts").document("passenger"),
            mapOf("uid" to uid, "name" to p.name, "phone" to p.phone),
        )
        batch.commit().await()
        return ref.id
    }

    /**
     * চালক রাইড গ্রহণ করে। Firestore transaction ব্যবহার হয়েছে,
     * তাই একসাথে কয়েকজন চাপলেও শুধু একজন পায়। সফল হলে true।
     */
    private suspend fun fsAcceptRide(rideId: String, uid: String, p: Profile): Boolean {
        val ref = db.collection("chalo_rides").document(rideId)
        val driverContact = ref.collection("contacts").document("driver")
        return try {
            db.runTransaction(Transaction.Function<Void?> { tx ->
                val status = tx.get(ref).getString("status")
                if (status != RideStatus.REQUESTED) {
                    throw FirebaseFirestoreException(
                        "রাইড আর খালি নেই",
                        FirebaseFirestoreException.Code.ABORTED,
                    )
                }
                tx.update(
                    ref,
                    mapOf(
                        "status" to RideStatus.ACCEPTED,
                        "driverId" to uid,
                        "driverName" to p.name,
                        "driverVehicle" to p.vehicleType,
                        "acceptedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                tx.set(driverContact, mapOf("uid" to uid, "name" to p.name, "phone" to p.phone))
                null
            }).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** চালকের ধাপ: arrived → ongoing → completed */
    private suspend fun fsSetRideStatus(rideId: String, status: String) {
        val field = when (status) {
            RideStatus.ARRIVED -> "arrivedAt"
            RideStatus.ONGOING -> "startedAt"
            RideStatus.COMPLETED -> "completedAt"
            else -> throw IllegalArgumentException("অজানা অবস্থা: $status")
        }
        db.collection("chalo_rides").document(rideId)
            .update(mapOf("status" to status, field to FieldValue.serverTimestamp()))
            .await()
    }

    /**
     * বাতিল। onlyIfRequested = true হলে শুধু তখনই বাতিল হয় যখন কেউ এখনো নেয়নি
     * (৩ মিনিটের স্বয়ংক্রিয় বাতিলে এটা দরকার)।
     */
    private suspend fun fsCancelRide(rideId: String, by: String, onlyIfRequested: Boolean = false): Boolean {
        val ref = db.collection("chalo_rides").document(rideId)
        return try {
            val done = db.runTransaction(Transaction.Function<Boolean> { tx ->
                val status = tx.get(ref).getString("status")
                val allowed = if (onlyIfRequested) {
                    status == RideStatus.REQUESTED
                } else {
                    status != RideStatus.COMPLETED && status != RideStatus.CANCELLED
                }
                if (allowed) {
                    tx.update(
                        ref,
                        mapOf(
                            "status" to RideStatus.CANCELLED,
                            "cancelledAt" to FieldValue.serverTimestamp(),
                            "cancelledBy" to by,
                        ),
                    )
                }
                allowed
            }).await()
            done == true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- লাইভ শোনা ----------
    private fun fsObserveMyRides(field: String, uid: String): Flow<List<Ride>> = callbackFlow {
        val reg = db.collection("chalo_rides").whereEqualTo(field, uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toRide() })
            }
        awaitClose { reg.remove() }
    }

    private fun fsObserveRequested(vehicleKey: String): Flow<List<Ride>> = callbackFlow {
        val reg = db.collection("chalo_rides")
            .whereEqualTo("status", RideStatus.REQUESTED)
            .whereEqualTo("vehicleType", vehicleKey)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toRide() })
            }
        awaitClose { reg.remove() }
    }

    private fun fsObserveContact(rideId: String, who: String): Flow<Contact?> = callbackFlow {
        val reg = db.collection("chalo_rides").document(rideId)
            .collection("contacts").document(who)
            .addSnapshotListener { s, err ->
                if (err != null) return@addSnapshotListener
                if (s != null && s.exists()) {
                    trySend(Contact(s.getString("name") ?: "", s.getString("phone") ?: ""))
                } else {
                    trySend(null)
                }
            }
        awaitClose { reg.remove() }
    }

    private fun fsObserveDriverLoc(driverId: String): Flow<Pt?> = callbackFlow {
        val reg = db.collection("chalo_drivers").document(driverId)
            .addSnapshotListener { s, err ->
                if (err != null) return@addSnapshotListener
                val lat = s?.getDouble("lat")
                val lng = s?.getDouble("lng")
                trySend(if (lat != null && lng != null) Pt(lat, lng) else null)
            }
        awaitClose { reg.remove() }
    }

    private suspend fun fsSetDriverOffline(uid: String) {
        db.collection("chalo_drivers").document(uid)
            .set(
                mapOf("online" to false, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
    }
}
