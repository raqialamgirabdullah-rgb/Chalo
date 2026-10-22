package com.rahcare.rickshawride

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.math.roundToInt

/** তিন মিনিটে কেউ না নিলে রিকোয়েস্ট বাতিল। */
const val REQUEST_TIMEOUT_MS = 180_000L

/** চালক শুধু এই দূরত্বের (কিমি) মধ্যের রিকোয়েস্ট দেখবে। */
const val DRIVER_RADIUS_KM = 3.0

enum class VehicleType(val key: String, val bn: String) {
    RICKSHAW("rickshaw", "রিকশা"),
    AUTO_RICKSHAW("auto_rickshaw", "অটোরিকশা"),
    BICYCLE("bicycle", "সাইকেল"),
    VAN("van", "ভ্যান"),
    AUTO_VAN("auto_van", "অটোভ্যান");

    companion object {
        fun fromKey(key: String?): VehicleType? = values().firstOrNull { it.key == key }
    }
}

/**
 * ভাড়ার হার — সব এক জায়গায়। বদলাতে চাইলে শুধু নিচের সংখ্যাগুলো বদলান।
 * ভাড়া = base + perKm × কিমি
 */
object FareConfig {
    data class Rate(val base: Int, val perKm: Int)

    val rates: Map<VehicleType, Rate> = mapOf(
        VehicleType.RICKSHAW to Rate(base = 20, perKm = 15),
        VehicleType.AUTO_RICKSHAW to Rate(base = 40, perKm = 20),
        VehicleType.BICYCLE to Rate(base = 15, perKm = 10),
        VehicleType.VAN to Rate(base = 30, perKm = 15),
        VehicleType.AUTO_VAN to Rate(base = 50, perKm = 25),
    )

    fun estimate(type: VehicleType, km: Double): Int {
        val r = rates.getValue(type)
        return (r.base + r.perKm * km).roundToInt()
    }
}

data class Pt(val lat: Double, val lng: Double)

data class Profile(
    val name: String = "",
    val phone: String = "",
    val role: String = "",
    val vehicleType: String = "",
)

data class Contact(val name: String, val phone: String)

object RideStatus {
    const val REQUESTED = "requested"
    const val ACCEPTED = "accepted"
    const val ARRIVED = "arrived"
    const val ONGOING = "ongoing"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
    val ACTIVE = setOf(REQUESTED, ACCEPTED, ARRIVED, ONGOING)
}

data class Ride(
    val id: String,
    val status: String,
    val passengerId: String,
    val passengerName: String,
    val driverId: String?,
    val driverName: String?,
    val vehicleType: String,
    val pickup: Pt,
    val dest: Pt,
    val distanceKm: Double,
    val fare: Int,
    val createdAtMs: Long,
    val cancelledBy: String?,
) {
    val isActive: Boolean get() = status in RideStatus.ACTIVE
}

fun DocumentSnapshot.toRide(): Ride? {
    val status = getString("status") ?: return null
    val passengerId = getString("passengerId") ?: return null
    val pLat = getDouble("pickupLat") ?: return null
    val pLng = getDouble("pickupLng") ?: return null
    val dLat = getDouble("destLat") ?: return null
    val dLng = getDouble("destLng") ?: return null
    val created = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?.toDate()?.time ?: System.currentTimeMillis()
    return Ride(
        id = id,
        status = status,
        passengerId = passengerId,
        passengerName = getString("passengerName") ?: "",
        driverId = getString("driverId"),
        driverName = getString("driverName"),
        vehicleType = getString("vehicleType") ?: "",
        pickup = Pt(pLat, pLng),
        dest = Pt(dLat, dLng),
        distanceKm = getDouble("distanceKm") ?: 0.0,
        fare = getLong("fare")?.toInt() ?: 0,
        createdAtMs = created,
        cancelledBy = getString("cancelledBy"),
    )
}
