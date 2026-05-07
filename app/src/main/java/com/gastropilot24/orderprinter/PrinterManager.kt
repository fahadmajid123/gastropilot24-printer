package com.gastropilot24.orderprinter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.sunmi.peripheral.printer.IWoyouService

class PrinterManager(private val context: Context) {

    private var printerService: IWoyouService? = null
    private var isConnected = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                printerService = IWoyouService.Stub.asInterface(service)
                isConnected = true
                Log.d(TAG, "Drucker verbunden")
            } catch (e: Exception) {
                Log.e(TAG, "Verbindungsfehler: ${e.message}")
                isConnected = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printerService = null
            isConnected = false
            Log.d(TAG, "Drucker getrennt")
        }
    }

    fun connectPrinter() {
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Bind Fehler: ${e.message}")
        }
    }

    fun disconnectPrinter() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {}
    }

    fun printOrder(order: Order) {
        val printer = printerService
        if (!isConnected || printer == null) {
            Log.w(TAG, "Drucker nicht verfügbar")
            return
        }
        try {
            printer.lineWrap(1, null)
            printer.printText("NEUE BESTELLUNG\n", null)
            printer.printText("================\n", null)
            printer.printText("Bestellung #${order.id}\n", null)
            printer.printText("${order.createdAt}\n", null)
            printer.printText("Kunde: ${order.customerName}\n", null)
            if (order.phone.isNotEmpty()) printer.printText("Tel: ${order.phone}\n", null)
            printer.printText("----------------\n", null)
            for (item in order.items) {
                printer.printText("${item.quantity}x ${item.name}  EUR ${String.format("%.2f", item.price * item.quantity)}\n", null)
            }
            printer.printText("================\n", null)
            printer.printText("GESAMT: EUR ${String.format("%.2f", order.total)}\n", null)
            printer.printText("Art: ${order.deliveryType}\n", null)
            if (order.notes.isNotEmpty()) printer.printText("Info: ${order.notes}\n", null)
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
