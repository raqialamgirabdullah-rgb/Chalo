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

    // ---------- লগইন ----------
    suspend fun ensureSignedIn(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        val res = auth.signInAnonymously().await()
        return res.user?.uid ?: throw IllegalStateException("সাইন-ইন হয়নি")
    }

    // ---------- প্রোফাইল ----------
    suspend fun loadProfile(uid: String): Profile? {
        val s = db.collection("users").document(uid).get().await()
        if (!s.exists()) return null
        return Profile(
            name = s.getString("name") ?: "",
            phone = s.getString("phone") ?: "",
            role = s.getString("role") ?: "",
            vehicleType = s.getString("vehicleType") ?: "",
        )
    }

    suspend fun saveProfile(uid: String, p: Profile) {
        val data = hashMapOf<String, Any>(
            "name" to p.name,
            "phone" to p.phone,
            "role" to p.role,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (p.role == "driver") data["vehicleType"] = p.vehicleType
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    // ---------- রাইড ডাকা ----------
    suspend fun createRide(
        uid: String,
        p: Profile,
        v: VehicleType,
        pickup: Pt,
        dest: Pt,
        km: Double,
        fare: Int,
    ): String {
        val ref = db.collection("rides").document()
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
    suspend fun acceptRide(rideId: String, uid: String, p: Profile): Boolean {
        val ref = db.collection("rides").document(rideId)
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
    suspend fun setRideStatus(rideId: String, status: String) {
        val field = when (status) {
            RideStatus.ARRIVED -> "arrivedAt"
            RideStatus.ONGOING -> "startedAt"
            RideStatus.COMPLETED -> "completedAt"
            else -> throw IllegalArgumentException("অজানা অবস্থা: $status")
        }
        db.collection("rides").document(rideId)
            .update(mapOf("status" to status, field to FieldValue.serverTimestamp()))
            .await()
    }

    /**
     * বাতিল। onlyIfRequested = true হলে শুধু তখনই বাতিল হয় যখন কেউ এখনো নেয়নি
     * (৩ মিনিটের স্বয়ংক্রিয় বাতিলে এটা দরকার)।
     */
    suspend fun cancelRide(rideId: String, by: String, onlyIfRequested: Boolean = false): Boolean {
        val ref = db.collection("rides").document(rideId)
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
    fun observeMyRides(field: String, uid: String): Flow<List<Ride>> = callbackFlow {
        val reg = db.collection("rides").whereEqualTo(field, uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toRide() })
            }
        awaitClose { reg.remove() }
    }

    fun observeRequested(vehicleKey: String): Flow<List<Ride>> = callbackFlow {
        val reg = db.collection("rides")
            .whereEqualTo("status", RideStatus.REQUESTED)
            .whereEqualTo("vehicleType", vehicleKey)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toRide() })
            }
        awaitClose { reg.remove() }
    }

    fun observeContact(rideId: String, who: String): Flow<Contact?> = callbackFlow {
        val reg = db.collection("rides").document(rideId)
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

    fun observeDriverLoc(driverId: String): Flow<Pt?> = callbackFlow {
        val reg = db.collection("drivers").document(driverId)
            .addSnapshotListener { s, err ->
                if (err != null) return@addSnapshotListener
                val lat = s?.getDouble("lat")
                val lng = s?.getDouble("lng")
                trySend(if (lat != null && lng != null) Pt(lat, lng) else null)
            }
        awaitClose { reg.remove() }
    }

    suspend fun setDriverOffline(uid: String) {
        db.collection("drivers").document(uid)
            .set(
                mapOf("online" to false, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            ).await()
    }
}
