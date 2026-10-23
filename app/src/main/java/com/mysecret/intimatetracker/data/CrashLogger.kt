package com.mysecret.intimatetracker.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简崩溃记录：把未捕获异常的堆栈写到 filesDir/logs/，最多保留 5 份。
 * 全部留在本机，不上传；「关于」页可查看与清除。
 */
object CrashLogger {

    private const val DIR = "logs"
    private const val KEEP = 5

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun logDir(context: Context): File = File(context.filesDir, DIR)

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = logDir(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(
            buildString {
                appendLine("time: ${Date()}")
                appendLine("thread: ${thread.name}")
                appendLine(throwable.stackTraceToString())
            },
        )
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    /** 最新在前。 */
    fun readAll(context: Context): List<String> =
        logDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.name }
            ?.mapNotNull { runCatching { it.readText() }.getOrNull() }
            ?: emptyList()

    fun clear(context: Context) {
        logDir(context).listFiles()?.forEach { it.delete() }
    }
}
