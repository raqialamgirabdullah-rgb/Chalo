package com.rahcare.rickshawride

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Firebase প্রজেক্টের তথ্য — চলো-র নিজস্ব আলাদা প্রজেক্ট (chalo-8483a), RahCare ওয়েবসাইটের সাথে কিছু মেশে না।
 * এগুলো google-services.json-এর মান; অ্যাপের কোডে এগুলো খোলা থাকাই নিয়ম, গোপন পাসওয়ার্ড নয়।
 * তবু Google Cloud Console-এ এই API key শুধু এই প্যাকেজের জন্য সীমিত করে রাখা ভালো।
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyCXoKc0N0gZuUBkMUBv8TmC1d-of1WigaI"
    const val APP_ID = "1:166813770190:android:983202fcd1b2e23bdc4b9f"
    const val PROJECT_ID = "chalo-8483a"
    const val STORAGE_BUCKET = "chalo-8483a.firebasestorage.app"
    const val SENDER_ID = "166813770190"
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
