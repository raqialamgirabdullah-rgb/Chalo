package com.rahcare.rickshawride

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val pt: Pt)

/**
 * জায়গার নাম লিখে খোঁজা (গুগল ম্যাপের মতো) — OpenStreetMap-এর বিনামূল্যের Nominatim সেবা।
 * নিয়ম: নিজস্ব User-Agent লাগে, সেকেন্ডে একটার বেশি অনুরোধ নয় (তাই খুঁজুন বাটন/এন্টারে খোঁজা হয়, প্রতি অক্ষরে নয়)।
 */
object Places {
    private const val UA = "Chalo/1.0 (https://github.com/raqialamgirabdullah-rgb/Chalo)"
    private const val BASE = "https://nominatim.openstreetmap.org"

    private fun get(url: String): String {
        val cn = URL(url).openConnection() as HttpURLConnection
        try {
            cn.setRequestProperty("User-Agent", UA)
            cn.setRequestProperty("Accept-Language", "bn,en")
            cn.connectTimeout = 10000
            cn.readTimeout = 15000
            if (cn.responseCode != 200) throw Exception("HTTP ${cn.responseCode}")
            return cn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            cn.disconnect()
        }
    }

    // লম্বা ঠিকানার প্রথম কয়েকটা অংশ রাখি, যাতে ছোট জায়গায় ধরে
    private fun short(s: String): String = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(", ")

    /** নাম/ঠিকানা লিখে খোঁজে; বাংলাদেশের ভেতরে, কাছের জায়গা আগে। ব্যর্থ হলে exception ছোড়ে। */
    suspend fun search(q: String, near: Pt?): List<Place> = withContext(Dispatchers.IO) {
        val e = URLEncoder.encode(q.trim(), "UTF-8")
        var url = "$BASE/search?format=jsonv2&q=$e&limit=6&countrycodes=bd&accept-language=bn,en"
        if (near != null) {
            val d = 0.5
            url += String.format(Locale.US, "&viewbox=%.5f,%.5f,%.5f,%.5f", near.lng - d, near.lat + d, near.lng + d, near.lat - d)
        }
        val arr = JSONArray(get(url))
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o: JSONObject = arr.getJSONObject(i)
            val lat = o.optString("lat").toDoubleOrNull() ?: continue
            val lon = o.optString("lon").toDoubleOrNull() ?: continue
            val name = short(o.optString("display_name", o.optString("name", "")))
            if (name.isNotEmpty()) out.add(Place(name, Pt(lat, lon)))
        }
        out
    }

    /** একটা বিন্দুর ঠিকানা-লেখা; না পেলে null। */
    suspend fun reverse(pt: Pt): String? = withContext(Dispatchers.IO) {
        try {
            val url = String.format(Locale.US, "$BASE/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&accept-language=bn,en", pt.lat, pt.lng)
            val o = JSONObject(get(url))
            val n = short(o.optString("display_name", ""))
            if (n.isEmpty()) null else n
        } catch (e: Exception) {
            null
        }
    }
}
