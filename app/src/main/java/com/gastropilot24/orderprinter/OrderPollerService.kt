package com.gastropilot24.orderprinter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class OrderPollerService : Service() {

    private lateinit var printerManager: PrinterManager
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private var isRunning = false

    private val supabaseUrl by lazy { BuildConfig.SUPABASE_URL }
    private val supabaseKey by lazy { BuildConfig.SUPABASE_KEY }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                try { checkForNewOrders() } catch (e: Exception) {
                    Log.e(TAG, "Poll Fehler: ${e.message}")
                }
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            printerManager = PrinterManager(this)
            printerManager.connectPrinter()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate Fehler: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification("Warte auf Bestellungen..."))
            isRunning = true
            handler.post(pollRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand Fehler: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        try { printerManager.disconnectPrinter() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForNewOrders() {
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) return

        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/orders?printed=eq.false&order=id.asc")
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Netzwerkfehler: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                if (!response.isSuccessful) return
                try {
                    val orders = parseOrders(body)
                    for (order in orders) {
                        printerManager.printOrder(order)
                        markOrderAsPrinted(order.id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse Fehler: ${e.message}")
                }
            }
        })
    }

    private fun markOrderAsPrinted(orderId: Int) {
        val body = JSONObject().put("printed", true).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/orders?id=eq.$orderId")
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .patch(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "GastroPilot24", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java))
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("GastroPilot24 Drucker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    companion object {
        private const val TAG = "OrderPollerService"
        private const val CHANNEL_ID = "gp24_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL = 6000L
    }
}
