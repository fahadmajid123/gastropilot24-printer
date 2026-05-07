package com.gastropilot24.orderprinter

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var printerStatusText: TextView
    private lateinit var lastOrderText: TextView
    private lateinit var testPrintButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    private val printerManager by lazy {
        PrinterManager(this) { status ->
            runOnUiThread { printerStatusText.text = status }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForNewOrders()
            handler.postDelayed(this, 6000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        printerStatusText = findViewById(R.id.printerStatusText)
        lastOrderText = findViewById(R.id.lastOrderText)
        testPrintButton = findViewById(R.id.testPrintButton)

        testPrintButton.setOnClickListener {
            Log.d("MainActivity", "Test Druck gedrückt, isConnected=${printerManager.isConnected}")
            printerManager.printTestPage()
        }

        try {
            printerManager.connectPrinter()
        } catch (e: Exception) {
            Log.e("MainActivity", "connectPrinter Fehler: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
        statusText.text = "Aktiv - warte auf Bestellungen..."
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerManager.disconnectPrinter() } catch (e: Exception) {}
    }

    private fun checkForNewOrders() {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_KEY
        if (url.isEmpty() || key.isEmpty()) {
            runOnUiThread { statusText.text = "Fehler: Supabase nicht konfiguriert" }
            return
        }

        val request = Request.Builder()
            .url("$url/rest/v1/orders?printed=eq.false&order=id.asc")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { statusText.text = "Verbindungsfehler..." }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val orders = parseOrders(body)
                    if (orders.isNotEmpty()) {
                        for (order in orders) {
                            printerManager.printOrder(order)
                            markAsPrinted(order.id, url, key)
                        }
                        runOnUiThread {
                            lastOrderText.text = "Letzte: #${orders.last().id} - ${orders.last().customerName}"
                            statusText.text = "${orders.size} Bestellung(en) gedruckt"
                        }
                    } else {
                        runOnUiThread { statusText.text = "Aktiv - warte auf Bestellungen..." }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Parse Fehler: ${e.message}")
                }
            }
        })
    }

    private fun markAsPrinted(id: Int, url: String, key: String) {
        val body = JSONObject().put("printed", true).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$url/rest/v1/orders?id=eq.$id")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .patch(body)
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }
}
