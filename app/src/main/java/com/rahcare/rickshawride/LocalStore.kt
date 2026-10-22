package com.rahcare.rickshawride

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AppConfig {
    /**
     * true হলে সব তথ্য শুধু এই ফোনেই জমা থাকে (Firebase ছাড়া পরীক্ষার মোড)।
     * Firebase-এ ফিরতে চাইলে এটি false করুন (আর কোড বদলাতে হবে না)।
     */
    val LOCAL_MODE: Boolean = false
}

/**
 * পরীক্ষার মোডের "ডাটাবেস": সব রাইড, ফোন নম্বর ও চালকের অবস্থান এই ফোনের ভেতরেই থাকে।
 * Repo-র সব কাজের সমান কাজ এখানে আছে, তাই বাকি অ্যাপ বুঝতেই পারে না তফাত।
 * একা পরীক্ষার জন্য দুটি "ডেমো" সহায়ক আছে:
 *  - ডেমো চালক: যাত্রীর ডাকা রাইড কিছুক্ষণ পর নিজে নেয় এবং গন্তব্য পর্যন্ত যায়।
 *  - ডেমো যাত্রী: চালকের জন্য নকল রিকোয়েস্ট বানায়।
 * সবকিছু মূল থ্রেডে চলে, তাই আলাদা লক লাগে না।
 */
object LocalStore {
    private const val DEMO_DRIVER_ID = "demo-driver"
    private const val DEMO_PASSENGER_ID = "demo-passenger"
    const val DEMO_DRIVER_NAME = "ডেমো চালক (পরীক্ষা)"
    const val DEMO_PASSENGER_NAME = "ডেমো যাত্রী (পরীক্ষা)"

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rides = MutableStateFlow<List<Ride>>(emptyList())
    private val contacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    private val driverLocs = MutableStateFlow<Map<String, Pt>>(emptyMap())

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("chalo_local", Context.MODE_PRIVATE)
        prefs = p
        try {
            val arr = JSONArray(p.getString("rides", "[]") ?: "[]")
            val list = ArrayList<Ride>()
            for (i in 0 until arr.length()) list.add(rideFromJson(arr.getJSONObject(i)))
            rides.value = list

            val obj = JSONObject(p.getString("contacts", "{}") ?: "{}")
            val map = HashMap<String, Contact>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val c = obj.getJSONObject(k)
                map[k] = Contact(c.getString("name"), c.getString("phone"))
            }
            contacts.value = map
        } catch (e: Exception) {
            // পুরোনো তথ্য পড়া না গেলে খালি অবস্থায় শুরু
        }
    }

    // ---------- পরিচয় ও প্রোফাইল ----------
    fun uid(): String {
        val p = prefs ?: return "local-unknown"
        val existing = p.getString("uid", null)
        if (existing != null) return existing
        val fresh = "local-" + UUID.randomUUID().toString()
        p.edit().putString("uid", fresh).apply()
        return fresh
    }

    fun loadProfile(): Profile? {
        val p = prefs ?: return null
        if (!p.contains("pf_role")) return null
        return Profile(
            name = p.getString("pf_name", "") ?: "",
            phone = p.getString("pf_phone", "") ?: "",
            role = p.getString("pf_role", "") ?: "",
            vehicleType = p.getString("pf_vehicle", "") ?: "",
        )
    }

    fun saveProfile(pf: Profile) {
        prefs?.edit()
            ?.putString("pf_name", pf.name)
            ?.putString("pf_phone", pf.phone)
            ?.putString("pf_role", pf.role)
            ?.putString("pf_vehicle", pf.vehicleType)
            ?.apply()
    }

    // ---------- রাইড ----------
    private fun get(id: String): Ride? = rides.value.firstOrNull { it.id == id }

    private fun update(id: String, change: (Ride) -> Ride): Boolean {
        val list = rides.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val copy = list.toMutableList()
        copy[idx] = change(list[idx])
        rides.value = copy
        persist()
        return true
    }

    private fun putContact(rideId: String, who: String, c: Contact) {
        val m = HashMap(contacts.value)
        m["$rideId:$who"] = c
        contacts.value = m
        persist()
    }

    private fun newId(): String = "r" + UUID.randomUUID().toString().substring(0, 8)

    fun createRide(uid: String, p: Profile, v: VehicleType, pickup: Pt, dest: Pt, km: Double, fare: Int): String {
        val id = newId()
        val r = Ride(
            id = id,
            status = RideStatus.REQUESTED,
            passengerId = uid,
            passengerName = p.name,
            driverId = null,
            driverName = null,
            vehicleType = v.key,
            pickup = pickup,
            dest = dest,
            distanceKm = km,
            fare = fare,
            createdAtMs = System.currentTimeMillis(),
            cancelledBy = null,
        )
        rides.value = (listOf(r) + rides.value).take(200)
        putContact(id, "passenger", Contact(p.name, p.phone))
        startDemoDriver(id)
        return id
    }

    fun acceptRide(rideId: String, uid: String, p: Profile): Boolean {
        val r = get(rideId) ?: return false
        if (r.status != RideStatus.REQUESTED) return false
        update(rideId) { it.copy(status = RideStatus.ACCEPTED, driverId = uid, driverName = p.name) }
        putContact(rideId, "driver", Contact(p.name, p.phone))
        return true
    }

    fun setRideStatus(rideId: String, status: String) {
        update(rideId) { it.copy(status = status) }
    }

    fun cancelRide(rideId: String, by: String, onlyIfRequested: Boolean): Boolean {
        val r = get(rideId) ?: return false
        val allowed = if (onlyIfRequested) {
            r.status == RideStatus.REQUESTED
        } else {
            r.status != RideStatus.COMPLETED && r.status != RideStatus.CANCELLED
        }
        if (!allowed) return false
        return update(rideId) { it.copy(status = RideStatus.CANCELLED, cancelledBy = by) }
    }

    // ---------- লাইভ শোনা ----------
    fun observeMyRides(field: String, uid: String): Flow<List<Ride>> =
        rides.map { list ->
            list.filter { if (field == "passengerId") it.passengerId == uid else it.driverId == uid }
        }

    fun observeRequested(vehicleKey: String): Flow<List<Ride>> =
        rides.map { list -> list.filter { it.status == RideStatus.REQUESTED && it.vehicleType == vehicleKey } }

    fun observeContact(rideId: String, who: String): Flow<Contact?> =
        contacts.map { it["$rideId:$who"] }

    fun observeDriverLoc(driverId: String): Flow<Pt?> =
        driverLocs.map { it[driverId] }

    // ---------- চালকের অবস্থান ----------
    fun setDriverLoc(uid: String, pt: Pt) {
        val m = HashMap(driverLocs.value)
        m[uid] = pt
        driverLocs.value = m
    }

    fun removeDriverLoc(uid: String) {
        val m = HashMap(driverLocs.value)
        m.remove(uid)
        driverLocs.value = m
    }

    // ---------- ডেমো যাত্রী: চালকের পরীক্ষার জন্য নকল রিকোয়েস্ট ----------
    fun createDemoRequest(near: Pt, vehicleKey: String) {
        val v = VehicleType.fromKey(vehicleKey) ?: VehicleType.RICKSHAW
        val pickup = Pt(near.lat + 0.0045, near.lng + 0.0030) // প্রায় ০.৬ কিমি দূরে
        val dest = Pt(near.lat + 0.0160, near.lng + 0.0120) // প্রায় ২ কিমির বেশি দূরে
        val km = Geo.straightKm(pickup, dest) * HaversineDistanceCalculator.ROAD_FACTOR
        val id = newId()
        val r = Ride(
            id = id,
            status = RideStatus.REQUESTED,
            passengerId = DEMO_PASSENGER_ID,
            passengerName = DEMO_PASSENGER_NAME,
            driverId = null,
            driverName = null,
            vehicleType = v.key,
            pickup = pickup,
            dest = dest,
            distanceKm = km,
            fare = FareConfig.estimate(v, km),
            createdAtMs = System.currentTimeMillis(),
            cancelledBy = null,
        )
        rides.value = (listOf(r) + rides.value).take(200)
        putContact(id, "passenger", Contact(DEMO_PASSENGER_NAME, "01800000000"))
    }

    // ---------- ডেমো চালক: যাত্রীর পরীক্ষার জন্য ----------
    private fun startDemoDriver(rideId: String) {
        scope.launch {
            delay(6_000)
            val r = get(rideId) ?: return@launch
            if (r.status != RideStatus.REQUESTED) return@launch

            update(rideId) {
                it.copy(status = RideStatus.ACCEPTED, driverId = DEMO_DRIVER_ID, driverName = DEMO_DRIVER_NAME)
            }
            putContact(rideId, "driver", Contact(DEMO_DRIVER_NAME, "01700000000"))

            // পিকআপ থেকে প্রায় ০.৭ কিমি দূর থেকে এসে পৌঁছায়
            val start = Pt(r.pickup.lat + 0.005, r.pickup.lng + 0.005)
            if (!walk(rideId, start, r.pickup, 8, RideStatus.ACCEPTED)) return@launch
            setRideStatus(rideId, RideStatus.ARRIVED)

            delay(4_000)
            if (get(rideId)?.status != RideStatus.ARRIVED) return@launch
            setRideStatus(rideId, RideStatus.ONGOING)

            if (!walk(rideId, r.pickup, r.dest, 12, RideStatus.ONGOING)) return@launch
            setRideStatus(rideId, RideStatus.COMPLETED)
            removeDriverLoc(DEMO_DRIVER_ID)
        }
    }

    /** ধাপে ধাপে চলে। মাঝপথে রাইডের অবস্থা বদলে গেলে (যেমন বাতিল) থেমে যায় ও false দেয়। */
    private suspend fun walk(rideId: String, from: Pt, to: Pt, steps: Int, whileStatus: String): Boolean {
        setDriverLoc(DEMO_DRIVER_ID, from)
        for (i in 1..steps) {
            delay(1_500)
            if (get(rideId)?.status != whileStatus) {
                removeDriverLoc(DEMO_DRIVER_ID)
                return false
            }
            val f = i.toDouble() / steps
            setDriverLoc(
                DEMO_DRIVER_ID,
                Pt(from.lat + (to.lat - from.lat) * f, from.lng + (to.lng - from.lng) * f),
            )
        }
        return true
    }

    // ---------- সংরক্ষণ ----------
    private fun persist() {
        val p = prefs ?: return
        val arr = JSONArray()
        for (r in rides.value) arr.put(rideToJson(r))
        val obj = JSONObject()
        for ((k, c) in contacts.value) {
            obj.put(k, JSONObject().put("name", c.name).put("phone", c.phone))
        }
        p.edit().putString("rides", arr.toString()).putString("contacts", obj.toString()).apply()
    }

    private fun rideToJson(r: Ride): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("status", r.status)
        o.put("passengerId", r.passengerId)
        o.put("passengerName", r.passengerName)
        o.put("driverId", r.driverId ?: JSONObject.NULL)
        o.put("driverName", r.driverName ?: JSONObject.NULL)
        o.put("vehicleType", r.vehicleType)
        o.put("pickupLat", r.pickup.lat)
        o.put("pickupLng", r.pickup.lng)
        o.put("destLat", r.dest.lat)
        o.put("destLng", r.dest.lng)
        o.put("distanceKm", r.distanceKm)
        o.put("fare", r.fare)
        o.put("createdAtMs", r.createdAtMs)
        o.put("cancelledBy", r.cancelledBy ?: JSONObject.NULL)
        return o
    }

    private fun rideFromJson(o: JSONObject): Ride = Ride(
        id = o.getString("id"),
        status = o.getString("status"),
        passengerId = o.getString("passengerId"),
        passengerName = o.getString("passengerName"),
        driverId = if (o.isNull("driverId")) null else o.getString("driverId"),
        driverName = if (o.isNull("driverName")) null else o.getString("driverName"),
        vehicleType = o.getString("vehicleType"),
        pickup = Pt(o.getDouble("pickupLat"), o.getDouble("pickupLng")),
        dest = Pt(o.getDouble("destLat"), o.getDouble("destLng")),
        distanceKm = o.getDouble("distanceKm"),
        fare = o.getInt("fare"),
        createdAtMs = o.getLong("createdAtMs"),
        cancelledBy = if (o.isNull("cancelledBy")) null else o.getString("cancelledBy"),
    )
}
