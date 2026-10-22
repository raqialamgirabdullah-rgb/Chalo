package com.rahcare.rickshawride

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class RickshawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val cfg = Configuration.getInstance()
        // OpenStreetMap-এর নিয়ম: অ্যাপের নিজস্ব user agent দিতে হয়।
        cfg.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
    }
}
