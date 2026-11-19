package com.mixam.mxtube.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

fun installCrashLogger(context: Context) {
    val app = context.applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            File(app.filesDir, "last_crash.txt").writeText(writer.toString())
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            throw throwable
        }
    }
}
