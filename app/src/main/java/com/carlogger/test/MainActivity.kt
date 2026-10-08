package com.carlogger.test

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var paired: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (12 * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 3, pad, pad)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        fun addBtn(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { action() }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        addBtn("Start") { startForegroundService(Intent(this, LogService::class.java)) }
        addBtn("Stop") { stopService(Intent(this, LogService::class.java)) }
        addBtn("Copy") {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("log", Logger.read(this)))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        }
        addBtn("Clear") {
            Logger.clear(this)
            refresh()
        }

        val help = TextView(this)
        help.text = "1) Allow permissions  2) Tap Start  3) Connect to the car and press the steering wheel buttons  4) Tap Copy and paste the log to Claude"
        help.textSize = 12f

        paired = TextView(this)
        paired.textSize = 13f
        paired.setPadding(0, pad, 0, pad)

        logView = TextView(this)
        logView.typeface = Typeface.MONOSPACE
        logView.textSize = 11f
        logView.setTextIsSelectable(true)

        scroll = ScrollView(this)
        scroll.addView(logView)

        root.addView(row)
        root.addView(help)
        root.addView(paired)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        requestPermissions(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS),
            1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        paired.text = pairedText()
    }

    override fun onResume() {
        super.onResume()
        Logger.listener = { refresh() }
        paired.text = pairedText()
        refresh()
    }

    override fun onPause() {
        Logger.listener = null
        super.onPause()
    }

    private fun refresh() {
        val t = Logger.read(this)
        logView.text = if (t.length > 30000) t.takeLast(30000) else t
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun pairedText(): String {
        return try {
            val bm = getSystemService(BluetoothManager::class.java)
            val list = bm.adapter?.bondedDevices
            if (list == null || list.isEmpty()) {
                "Paired devices: none (or Bluetooth permission not granted)"
            } else {
                "Paired devices:\n" + list.joinToString("\n") { "- ${it.name}  [${it.address}]" }
            }
        } catch (e: SecurityException) {
            "Paired devices: Bluetooth permission not granted"
        }
    }
}
