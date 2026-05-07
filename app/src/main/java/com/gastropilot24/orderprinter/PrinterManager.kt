package com.gastropilot24.orderprinter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.sunmi.peripheral.printer.IWoyouService

class PrinterManager(
    private val context: Context,
    private val onStatusChanged: ((String) -> Unit)? = null
) {

    private var printerService: IWoyouService? = null
    var isConnected = false
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected aufgerufen")
            try {
                printerService = IWoyouService.Stub.asInterface(service)
                isConnected = true
                Log.d(TAG, "✅ Drucker AIDL verbunden")
                onStatusChanged?.invoke("Drucker: verbunden ✅")

                // printerInit nach erfolgreicher Verbindung versuchen
                try {
                    printerService?.printerInit(null)
                    Log.d(TAG, "printerInit OK")
                } catch (e: Exception) {
                    Log.w(TAG, "printerInit fehlgeschlagen (ignoriert): ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ onServiceConnected Fehler: ${e.message}")
                isConnected = false
                onStatusChanged?.invoke("Drucker: Verbindung fehlgeschlagen ❌")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printerService = null
            isConnected = false
            Log.d(TAG, "Drucker getrennt")
            onStatusChanged?.invoke("Drucker: getrennt ⚠️")
        }
    }

    fun connectPrinter() {
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "bindService result: $bound")
            if (!bound) {
                Log.e(TAG, "❌ bindService = false – Service nicht gefunden!")
                onStatusChanged?.invoke("Drucker: Service nicht gefunden ❌")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bind Fehler: ${e.message}")
            onStatusChanged?.invoke("Drucker: Bind Fehler ❌")
        }
    }

    fun disconnectPrinter() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {}
    }

    fun printTestPage() {
        val printer = printerService
        Log.d(TAG, "printTestPage: isConnected=$isConnected, printer=${printer != null}")
        if (!isConnected || printer == null) {
            Log.w(TAG, "Drucker nicht verfügbar für Test")
            onStatusChanged?.invoke("Drucker nicht verbunden!")
            return
        }
        try {
            printer.printerInit(null)
            printer.printText("=== TESTDRUCK ===\n", null)
            printer.printText("GastroPilot24\n", null)
            printer.printText("Drucker funktioniert!\n", null)
            printer.printText("=================\n", null)
            printer.printAndFeedPaper(60, null)
            Log.d(TAG, "✅ Testdruck gesendet")
            onStatusChanged?.invoke("Testdruck gesendet ✅")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Testdruck Fehler: ${e.javaClass.simpleName}: ${e.message}")
            onStatusChanged?.invoke("Druckfehler: ${e.message}")
        }
    }

    fun printOrder(order: Order) {
        val printer = printerService
        Log.d(TAG, "printOrder #${order.id}: isConnected=$isConnected, printer=${printer != null}")
        if (!isConnected || printer == null) {
            Log.w(TAG, "Drucker nicht verfügbar für Bestellung #${order.id}")
            return
        }
        try {
            printer.printerInit(null)
            printer.printText("*** NEUE BESTELLUNG ***\n", null)
            printer.printText("========================\n", null)
            printer.printText("Bestellung #${order.id}\n", null)
            printer.printText("${order.createdAt}\n", null)
            printer.printText("Kunde: ${order.customerName}\n", null)
            if (order.phone.isNotEmpty()) printer.printText("Tel: ${order.phone}\n", null)
            printer.printText("------------------------\n", null)
            for (item in order.items) {
                printer.printText(
                    "${item.quantity}x ${item.name}  EUR ${String.format("%.2f", item.price * item.quantity)}\n",
                    null
                )
            }
            printer.printText("========================\n", null)
            printer.printText("GESAMT: EUR ${String.format("%.2f", order.total)}\n", null)
            printer.printText("Art: ${order.deliveryType}\n", null)
            if (order.notes.isNotEmpty()) printer.printText("Info: ${order.notes}\n", null)
            printer.printAndFeedPaper(60, null)
            Log.d(TAG, "✅ Bestellung #${order.id} gedruckt")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Druckfehler Bestellung #${order.id}: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PrinterManager"
    }
}
