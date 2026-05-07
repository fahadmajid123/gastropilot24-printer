package com.gastropilot24.orderprinter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class OrderPollerService : LifecycleService() {

    private lateinit var printerManager: PrinterManager
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private var isRunning = false

    private val supabaseUrl by lazy { BuildConfig.SUPABASE_URL }
    private val supabaseKey by lazy { BuildConfig.SUPABASE_KEY }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                checkForNewOrders()
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        printerManager = PrinterManager(this)
        printerManager.connectPrinter()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification("Warte auf Bestellungen..."))
        isRunning = true
        handler.post(pollRunnable)
        Log.d(TAG, "Service gestartet")
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        printerManager.disconnectPrinter()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun checkForNewOrders() {
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            Log.e(TAG, "Supabase nicht konfiguriert")
            return
        }

        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/orders?printed=eq.false&order=id.asc")
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API Fehler: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                if (!response.isSuccessful) {
                    Log.e(TAG, "API Fehler ${response.code}: $body")
                    return
                }

                try {
                    val orders = parseOrders(body)
                    if (orders.isNotEmpty()) {
                        Log.d(TAG, "${orders.size} neue Bestellung(en) gefunden")
                        for (order in orders) {
                            printerManager.printOrder(order)
                            markOrderAsPrinted(order.id)
                        }
                        updateNotification("Letzte Bestellung: #${orders.last().id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse Fehler: ${e.message}")
                }
            }
        })
    }

    private fun markOrderAsPrinted(orderId: Int) {
        val json = JSONObject().put("printed", true).toString()
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/orders?id=eq.$orderId")
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .addHeader("Content-Type", "application/json")
            .patch(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Markierung fehlgeschlagen: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Bestellung #$orderId als gedruckt markiert")
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GastroPilot24 Bestellungen",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GastroPilot24 Drucker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "OrderPollerService"
        private const val CHANNEL_ID = "gastropilot24_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL = 5000L // 5 Sekunden
    }
}
