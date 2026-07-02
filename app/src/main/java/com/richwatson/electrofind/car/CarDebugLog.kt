package com.richwatson.electrofind.car

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CarDebugLog {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private const val MAX_BYTES = 500_000L

    @Synchronized
    fun log(context: Context, message: String) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "car_debug.log")
            if (file.exists() && file.length() > MAX_BYTES) {
                file.delete()
            }
            file.appendText("${LocalDateTime.now().format(fmt)} $message\n")
        } catch (_: Exception) {
        }
    }
}
