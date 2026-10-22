package com.rahcare.rickshawride

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    // নতুন সংস্করণ নিজে থেকে খোঁজে: অ্যাপ খুললে ও খোলা থাকলে ২০ সেকেন্ড পরপর
    private val checker = object : Runnable {
        override fun run() {
            val app = applicationContext
            Thread { Updater.checkNow(app) }.start()
            handler.postDelayed(this, 20_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChaloTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        Root(vm)
                        // মানচিত্রের ওপর, ওপরের শিরোনামের নিচে, মাঝখানে (নিচের বাটনগুলো ঢাকে না)
                        UpdateButton(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // "Allow from this source" দিয়ে ফিরলে আপডেট নিজেই শুরু হয়
        val pu = UpdState.pendingUrl
        if (pu != null && packageManager.canRequestPackageInstalls()) {
            UpdState.pendingUrl = null
            Updater.install(this, pu)
        }
        Updater.refreshLabel(this)
        handler.removeCallbacks(checker)
        checker.run()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checker)
    }
}
