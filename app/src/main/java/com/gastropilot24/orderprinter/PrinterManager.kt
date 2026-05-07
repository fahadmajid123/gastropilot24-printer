package com.gastropilot24.orderprinter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.sunmi.peripheral.printer.ICallback
import com.sunmi.peripheral.printer.IWoyouService

class PrinterManager(private val context: Context) {

    private var printerService: IWoyouService? = null
    private var isConnected = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printerService = IWoyouService.Stub.asInterface(service)
            isConnected = true
            printerService?.printerInit(null)
            Log.d(TAG, "Drucker verbunden")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printerService = null
            isConnected = false
            Log.d(TAG, "Drucker getrennt - versuche Neuverbindung...")
            connectPrinter()
        }
    }

    fun connectPrinter() {
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Verbindungsfehler: ${e.message}")
        }
    }

    fun disconnectPrinter() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Trennungsfehler: ${e.message}")
        }
    }

    fun isConnected() = isConnected

    fun printOrder(order: Order) {
        if (!isConnected || printerService == null) {
            Log.e(TAG, "Drucker nicht verbunden")
            return
        }

        try {
            val printer = printerService!!

            printer.printerInit(null)
            printer.lineWrap(1, null)

            // Header
            printer.printTextWithFont("NEUE BESTELLUNG\n", null, 28f, null)
            printer.printText("================================\n", null)

            // Bestellnummer & Zeit
            printer.printText("Bestellung #${order.id}\n", null)
            printer.printText("${order.createdAt}\n", null)
            printer.printText("--------------------------------\n", null)

            // Kundenname
            printer.printText("Kunde: ${order.customerName}\n", null)
            if (order.phone.isNotEmpty()) {
                printer.printText("Tel: ${order.phone}\n", null)
            }
            printer.printText("--------------------------------\n", null)

            // Bestellpositionen
            printer.printTextWithFont("BESTELLUNG:\n", null, 22f, null)
            for (item in order.items) {
                printer.printText("${item.quantity}x ${item.name}\n", null)
                printer.printText("   EUR ${String.format("%.2f", item.price * item.quantity)}\n", null)
            }
            printer.printText("================================\n", null)

            // Gesamt
            printer.printTextWithFont("GESAMT: EUR ${String.format("%.2f", order.total)}\n", null, 26f, null)

            // Lieferart
            printer.printText("Art: ${order.deliveryType}\n", null)

            if (order.notes.isNotEmpty()) {
                printer.printText("Anmerkung: ${order.notes}\n", null)
            }

            printer.printText("================================\n", null)
            printer.printText("     Vielen Dank!              \n", null)
            printer.printAndFeedPaper(60, null)

            Log.d(TAG, "Bestellung #${order.id} gedruckt")

        } catch (e: Exception) {
            Log.e(TAG, "Druckfehler: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PrinterManager"
    }
}
