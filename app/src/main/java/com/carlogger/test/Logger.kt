package com.carlogger.test

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var listener: (() -> Unit)? = null

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "log.txt")

    @Synchronized
    fun add(ctx: Context, msg: String) {
        val line = fmt.format(Date()) + "  " + msg + "\n"
        try {
            file(ctx).appendText(line)
        } catch (e: Exception) {
        }
        main.post { listener?.invoke() }
    }

    @Synchronized
    fun read(ctx: Context): String {
        return try {
            val f = file(ctx)
            if (f.exists()) f.readText() else ""
        } catch (e: Exception) {
            ""
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (e: Exception) {
        }
        main.post { listener?.invoke() }
    }
}
