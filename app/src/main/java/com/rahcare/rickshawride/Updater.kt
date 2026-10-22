package com.rahcare.rickshawride

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.HttpURLConnection
import java.net.URL

/** আপডেট বোতামের অবস্থা (AutoReply-এর মতোই: বোতামটাই প্রগ্রেস বার হয়ে যায়)। */
object UpdState {
    var info by mutableStateOf<Updater.Info?>(null)
    var busy by mutableStateOf(false)
    var pct by mutableIntStateOf(-1)
    var label by mutableStateOf("Update")
    var stage = 0
    var pendingUrl: String? = null
}

/** GitHub-এর নতুন রিলিজ ধরে এবং অ্যাপের ভেতর থেকেই ইনস্টল করে — বারবার ডাউনলোড করতে হয় না। */
object Updater {
    const val REPO = "raqialamgirabdullah-rgb/Chalo"
    private const val APK = "RickshawRide.apk"
    private val main = Handler(Looper.getMainLooper())

    class Info(val v: Int, val url: String)

    fun curVer(c: Context): Int {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    }

    // "latest release" লিংক কোন ট্যাগে যায় সেটা দেখে (API নয়, তাই রেট লিমিট নেই); ট্যাগের শেষ সংখ্যাই সংস্করণ
    private fun fetch(): Info {
        val cn = URL("https://github.com/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            cn.instanceFollowRedirects = false
            cn.connectTimeout = 10000
            cn.readTimeout = 10000
            val tag = (cn.getHeaderField("Location") ?: "").substringAfterLast('/')
            val v = tag.substringAfterLast('.').toInt()
            return Info(v, "https://github.com/$REPO/releases/download/$tag/$APK")
        } finally {
            cn.disconnect()
        }
    }

    /** ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকুন; বর্তমানের চেয়ে নতুন সংস্করণ থাকলে UpdState.info বসে। */
    fun checkNow(c: Context) {
        val found = try {
            val i = fetch()
            if (i.v > curVer(c)) i else null
        } catch (e: Exception) {
            return // নেট না থাকলে আগের অবস্থাই থাকুক
        }
        main.post { UpdState.info = found }
    }

    private fun resetUi(c: Context) {
        UpdState.busy = false
        UpdState.stage = 0
        UpdState.pct = -1
        UpdState.label = if (c.packageManager.canRequestPackageInstalls()) "Update" else "Allow update"
    }

    fun refreshLabel(c: Context) {
        if (!UpdState.busy) UpdState.label = if (c.packageManager.canRequestPackageInstalls()) "Update" else "Allow update"
    }

    /** APK নামিয়ে PackageInstaller দিয়ে ইনস্টল করে; ফোন নিশ্চিত করার পর্দা দেখাবে। */
    fun install(c: Context, url: String) {
        if (!c.packageManager.canRequestPackageInstalls()) {
            // অনুমতি দিয়ে ফিরে এলেই আপডেট নিজে থেকে শুরু হবে (আবার চাপতে হবে না)
            UpdState.pendingUrl = url
            Toast.makeText(c, "পরের পর্দায় \"Allow from this source\" চালু করে ফিরে আসুন — আপডেট নিজেই শুরু হবে", Toast.LENGTH_LONG).show()
            c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")))
            return
        }
        if (UpdState.busy) return
        UpdState.busy = true
        UpdState.stage = 1
        UpdState.pct = 0
        UpdState.label = "Downloading… 0%"
        val app = c.applicationContext
        Thread {
            val inst = app.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = inst.createSession(p)
            try {
                val s = inst.openSession(id)
                val cn = URL(url).openConnection() as HttpURLConnection
                cn.connectTimeout = 15000
                cn.readTimeout = 30000
                if (cn.responseCode != 200) throw Exception("HTTP ${cn.responseCode}")
                val total = cn.contentLengthLong
                cn.inputStream.use { ins ->
                    s.openWrite("a", 0, -1).use { out ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (pct != last) {
                                    last = pct
                                    main.post { UpdState.pct = pct; UpdState.label = "Downloading… $pct%" }
                                }
                            }
                        }
                        s.fsync(out)
                    }
                }
                val pi = PendingIntent.getBroadcast(
                    app, id, Intent(app, UpdateReceiver::class.java).setPackage(app.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                main.post {
                    UpdState.stage = 2
                    UpdState.pct = 100
                    UpdState.label = "Installing…"
                    main.postDelayed({ if (UpdState.stage == 2) resetUi(app) }, 60000L)
                }
                s.commit(pi.intentSender)
            } catch (e: Exception) {
                try { inst.abandonSession(id) } catch (x: Exception) {}
                main.post {
                    resetUi(app)
                    Toast.makeText(app, "ডাউনলোড বা ইনস্টল শুরু করা গেল না (${e.javaClass.simpleName}: ${e.message?.take(70)}) — আবার চাপুন", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

/** PackageInstaller-এর ফল এখানে আসে; নিশ্চিত করার পর্দা দরকার হলে সেটা খোলে। */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val ui = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (ui != null) {
                    ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { c.startActivity(ui) } catch (e: Exception) {
                        Toast.makeText(c, "নিশ্চিত করার পর্দা খুলল না — অ্যাপে ফিরে আবার Update চাপুন", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(
                c, "আপডেট হয়নি: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""), Toast.LENGTH_LONG,
            ).show()
        }
    }
}
