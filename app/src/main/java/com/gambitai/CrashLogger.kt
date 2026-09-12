package com.gambitai

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogger(context: Context) : Thread.UncaughtExceptionHandler {

    private val logFile: File = File(
        context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile?.parentFile,
        "Download/gambitai_crash_log.txt"
    )
    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val sb = StringBuilder()
            sb.append("=====================================\n")
            sb.append("وقت الكراش: $timestamp\n")
            sb.append("الخيط: ${thread.name}\n")
            sb.append("نوع الخطأ: ${throwable::class.java.name}\n")
            sb.append("الرسالة: ${throwable.message}\n")
            sb.append("تفاصيل كاملة (Stack Trace):\n")
            sb.append(throwable.stackTraceToString())
            sb.append("\n=====================================\n\n")

            logFile.parentFile?.mkdirs()
            logFile.appendText(sb.toString())
        } catch (e: Exception) {
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
