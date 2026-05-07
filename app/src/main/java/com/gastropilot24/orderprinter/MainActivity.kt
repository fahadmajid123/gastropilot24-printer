package com.gastropilot24.orderprinter

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var idleScreen: LinearLayout
    private lateinit var orderScreen: LinearLayout
    private lateinit var acceptedScreen: LinearLayout
    private lateinit var rejectedScreen: LinearLayout

    private lateinit var statusText: TextView
    private lateinit var orderCountText: TextView
    private lateinit var revenueText: TextView
    private lateinit var orderIdText: TextView
    private lateinit var orderTypeText: TextView
    private lateinit var customerNameText: TextView
    private lateinit var customerPhoneText: TextView
    private lateinit var orderItemsText: TextView
    private lateinit var orderTotalText: TextView
    private lateinit var acceptedTitleText: TextView
    private lateinit var acceptedSubText: TextView

    private lateinit var demoButton: Button
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private val printerManager by lazy {
        PrinterManager(this) { status -> Log.d("MainActivity", status) }
    }

    private var orderCount = 0
    private var totalRevenue = 0.0
    private var currentOrderId = 0
    private var currentOrderTotal = 0.0
    private var demoOrderIndex = 0

    private val demoOrders = listOf(
        DemoOrder(42, "Max Mustermann", "+43 664 123456", "LIEFERUNG",
            "2x Margherita Pizza  €17.80\n1x Cola 0.5l  €3.50", 21.30),
        DemoOrder(43, "Anna Gruber", "+43 699 987654", "ABHOLUNG",
            "1x Wiener Schnitzel  €14.90\n1x Bier 0.5l  €4.20\n1x Apfelstrudel  €5.50", 24.60),
        DemoOrder(44, "Thomas Huber", "+43 676 555333", "LIEFERUNG",
            "3x Cheeseburger  €23.70\n2x Pommes groß  €7.90\n3x Fanta  €6.90", 38.50),
        DemoOrder(45, "Sarah Müller", "+43 650 444222", "ABHOLUNG",
            "1x Spaghetti Carbonara  €12.90\n1x Tiramisu  €5.80\n1x Wasser  €2.50", 21.20)
    )

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForNewOrders()
            handler.postDelayed(this, 6000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        idleScreen = findViewById(R.id.idleScreen)
        orderScreen = findViewById(R.id.orderScreen)
        acceptedScreen = findViewById(R.id.acceptedScreen)
        rejectedScreen = findViewById(R.id.rejectedScreen)

        statusText = findViewById(R.id.statusText)
        orderCountText = findViewById(R.id.orderCountText)
        revenueText = findViewById(R.id.revenueText)
        orderIdText = findViewById(R.id.orderIdText)
        orderTypeText = findViewById(R.id.orderTypeText)
        customerNameText = findViewById(R.id.customerNameText)
        customerPhoneText = findViewById(R.id.customerPhoneText)
        orderItemsText = findViewById(R.id.orderItemsText)
        orderTotalText = findViewById(R.id.orderTotalText)
        acceptedTitleText = findViewById(R.id.acceptedTitleText)
        acceptedSubText = findViewById(R.id.acceptedSubText)

        demoButton = findViewById(R.id.demoButton)
        acceptButton = findViewById(R.id.acceptButton)
        rejectButton = findViewById(R.id.rejectButton)

        demoButton.setOnClickListener { showDemoOrder() }
        acceptButton.setOnClickListener { onAccept() }
        rejectButton.setOnClickListener { onReject() }

        try { printerManager.connectPrinter() } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerManager.disconnectPrinter() } catch (e: Exception) {}
    }

    private fun showDemoOrder() {
        val demo = demoOrders[demoOrderIndex % demoOrders.size]
        demoOrderIndex++
        showOrder(demo.id, demo.customer, demo.phone, demo.type, demo.items, demo.total)
    }

    private fun showOrder(id: Int, customer: String, phone: String, type: String, items: String, total: Double) {
        currentOrderId = id
        currentOrderTotal = total
        orderIdText.text = "Bestellung #$id"
        orderTypeText.text = type
        customerNameText.text = customer
        customerPhoneText.text = phone
        orderItemsText.text = items
        orderTotalText.text = "€${"%.2f".format(total)}"

        idleScreen.visibility = View.GONE
        orderScreen.visibility = View.VISIBLE
        acceptedScreen.visibility = View.GONE
        rejectedScreen.visibility = View.GONE
    }

    private fun onAccept() {
        orderCount++
        totalRevenue += currentOrderTotal
        orderCountText.text = orderCount.toString()
        revenueText.text = "€${"%.0f".format(totalRevenue)}"

        acceptedTitleText.text = "Bestellung #$currentOrderId angenommen"
        acceptedSubText.text = "Bon wird gedruckt..."

        idleScreen.visibility = View.GONE
        orderScreen.visibility = View.GONE
        acceptedScreen.visibility = View.VISIBLE
        rejectedScreen.visibility = View.GONE

        try { printerManager.printTestPage() } catch (e: Exception) {}

        handler.postDelayed({
            acceptedSubText.text = "Bon gedruckt ✓"
            handler.postDelayed({ showIdle() }, 1500)
        }, 1500)

        if (BuildConfig.SUPABASE_URL.isNotEmpty()) {
            markAsPrinted(currentOrderId)
        }
    }

    private fun onReject() {
        idleScreen.visibility = View.GONE
        orderScreen.visibility = View.GONE
        acceptedScreen.visibility = View.GONE
        rejectedScreen.visibility = View.VISIBLE

        handler.postDelayed({ showIdle() }, 2000)
    }

    private fun showIdle() {
        idleScreen.visibility = View.VISIBLE
        orderScreen.visibility = View.GONE
        acceptedScreen.visibility = View.GONE
        rejectedScreen.visibility = View.GONE
        statusText.text = "Online – warte auf Bestellungen"
    }

    private fun checkForNewOrders() {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_KEY
        if (url.isEmpty() || key.isEmpty()) return
        if (orderScreen.visibility == View.VISIBLE) return

        val request = Request.Builder()
            .url("$url/rest/v1/orders?printed=eq.false&order=id.asc&limit=1")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val orders = parseOrders(body)
                    if (orders.isNotEmpty()) {
                        val o = orders.first()
                        val items = o.items.joinToString("\n") { "${it.quantity}x ${it.name}  €${"%.2f".format(it.price * it.quantity)}" }
                        runOnUiThread {
                            showOrder(o.id, o.customerName, o.phone, o.deliveryType, items, o.total)
                        }
                    }
                } catch (e: Exception) {}
            }
        })
    }

    private fun markAsPrinted(id: Int) {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_KEY
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

    data class DemoOrder(val id: Int, val customer: String, val phone: String,
                         val type: String, val items: String, val total: Double)
}
