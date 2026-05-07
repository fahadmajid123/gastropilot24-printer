package com.gastropilot24.orderprinter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        startButton.setOnClickListener {
            startPollerService()
            statusText.text = "✅ Aktiv – warte auf Bestellungen..."
        }

        stopButton.setOnClickListener {
            stopPollerService()
            statusText.text = "⛔ Gestoppt"
        }

        // Automatisch starten
        startPollerService()
        statusText.text = "✅ Aktiv – warte auf Bestellungen..."
    }

    private fun startPollerService() {
        val intent = Intent(this, OrderPollerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopPollerService() {
        stopService(Intent(this, OrderPollerService::class.java))
    }
}
