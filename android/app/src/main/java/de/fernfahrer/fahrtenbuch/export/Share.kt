package de.fernfahrer.fahrtenbuch.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Share {
    /** Ordner für Exportdateien (im Cache, wird über den FileProvider freigegeben). */
    fun exportDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Öffnet das Android-Teilen-Menü für eine Datei. */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension.replace('_', ' '))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}
