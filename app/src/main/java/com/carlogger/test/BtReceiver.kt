package com.carlogger.test

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Declared in the manifest: tests whether the system wakes the app
// when the phone connects to the car, even if the app is closed.
class BtReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val name = try {
            dev?.name
        } catch (e: SecurityException) {
            "NO-PERMISSION"
        }
        val mac = try {
            dev?.address
        } catch (e: SecurityException) {
            "NO-PERMISSION"
        }
        Logger.add(context, "[manifest] ${action.substringAfterLast('.')} name=$name mac=$mac")

        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            try {
                context.startForegroundService(Intent(context, LogService::class.java))
                Logger.add(context, "[manifest] auto-start service: OK")
            } catch (e: Exception) {
                Logger.add(context, "[manifest] auto-start service: FAILED ${e.javaClass.simpleName}")
            }
        }
    }
}
