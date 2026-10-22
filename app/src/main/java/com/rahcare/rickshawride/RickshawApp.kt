package com.rahcare.rickshawride

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Firebase প্রজেক্টের তথ্য — এখানে RahCare ওয়েবসাইটের একই প্রজেক্ট (rah-care) ব্যবহার হচ্ছে।
 * অন্য প্রজেক্টে সরাতে চাইলে শুধু নিচের পাঁচটি মান বদলালেই হবে (আর কিছু নয়)।
 * এগুলো ওয়েবসাইটের common.js-এ খোলাই আছে, গোপন পাসওয়ার্ড নয়।
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyDAicuq_q2KoKLx01Yejo3jEx64n5tZOJA"
    const val APP_ID = "1:205338868264:web:c5cb43f269346374c2531d"
    const val PROJECT_ID = "rah-care"
    const val STORAGE_BUCKET = "rah-care.firebasestorage.app"
    const val SENDER_ID = "205338868264"
}

class RickshawApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (AppConfig.LOCAL_MODE) {
            LocalStore.init(this)
        } else if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(FirebaseConfig.API_KEY)
                .setApplicationId(FirebaseConfig.APP_ID)
                .setProjectId(FirebaseConfig.PROJECT_ID)
                .setStorageBucket(FirebaseConfig.STORAGE_BUCKET)
                .setGcmSenderId(FirebaseConfig.SENDER_ID)
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        val cfg = Configuration.getInstance()
        // OpenStreetMap-এর নিয়ম: অ্যাপের নিজস্ব user agent দিতে হয়।
        cfg.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
    }
}
