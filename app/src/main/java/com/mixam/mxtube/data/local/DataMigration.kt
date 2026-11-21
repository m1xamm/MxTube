package com.mixam.mxtube.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

internal object DataMigration {
    private const val TAG = "MxDataMigration"
    private const val AUTHORITY = "com.m1xamm.mxtube.migration"
    private const val PREFS = "mx_migration_state"
    private const val DONE = "imported"

    fun importOnce(context: Context) {
        val state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(DONE, false)) return
        val uri = Uri.parse("content://$AUTHORITY/data")
        val archive = File(context.cacheDir, "mxtube-incoming.zip")
        val root = context.dataDir
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return
                archive.outputStream().use { output -> input.copyTo(output) }
            }
            ZipInputStream(archive.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val target = File(root, name)
                    val safe = !name.startsWith("/") && !name.contains("..") && target.canonicalPath.startsWith(root.canonicalPath)
                    if (safe) {
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { output -> zip.copyTo(output) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            state.edit().putBoolean(DONE, true).apply()
            Log.i(TAG, "imported data from $AUTHORITY")
        } catch (error: Exception) {
            Log.w(TAG, "import failed", error)
        } finally {
            archive.delete()
        }
    }
}
