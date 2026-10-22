package com.rahcare.rickshawride

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** দুই বিন্দুর সরলরেখার দূরত্ব (কিমি) — haversine সূত্র। */
    fun straightKm(a: Pt, b: Pt): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2.0)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}

/**
 * রাস্তা ধরে দূরত্বের জন্য এই ইন্টারফেস। এখন সরলরেখা × ১.৩ ব্যবহার হচ্ছে।
 * পরে OSRM যোগ করতে চাইলে নতুন একটি ক্লাস লিখে DistanceProvider.calculator-এ বসালেই হবে,
 * বাকি কোড বদলাতে হবে না।
 */
interface DistanceCalculator {
    suspend fun roadKm(from: Pt, to: Pt): Double
}

object HaversineDistanceCalculator : DistanceCalculator {
    const val ROAD_FACTOR = 1.3
    override suspend fun roadKm(from: Pt, to: Pt): Double = Geo.straightKm(from, to) * ROAD_FACTOR
}

object DistanceProvider {
    var calculator: DistanceCalculator = HaversineDistanceCalculator
}

// ---- বাংলা সংখ্যা সহায়ক ----

private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

fun String.toBnDigits(): String = buildString {
    for (c in this@toBnDigits) append(if (c in '0'..'9') BN_DIGITS[c - '0'] else c)
}

fun String.toEnDigits(): String = buildString {
    for (c in this@toEnDigits) append(if (c in '০'..'৯') ('0' + (c - '০')) else c)
}

fun Int.bn(): String = toString().toBnDigits()

fun Double.kmText(): String = String.format(Locale.US, "%.1f", this).toBnDigits()

fun isValidBdPhone(p: String): Boolean = Regex("^(\\+?88)?01[3-9][0-9]{8}$").matches(p)
