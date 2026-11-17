package de.fernfahrer.fahrtenbuch.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Liest und schreibt Datensicherungs-Dateien (vom Nutzer gewählter Speicherort). */
object BackupFiles {
    suspend fun write(resolver: ContentResolver, uri: Uri, data: BackupData) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(Backup.toJson(data).toByteArray()) }
            ?: error("Datei konnte nicht geöffnet werden")
    }

    /** Wirft [BackupFormatException], wenn die Datei keine gültige Sicherung ist. */
    suspend fun read(resolver: ContentResolver, uri: Uri): BackupData {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Datei konnte nicht geöffnet werden")
        }
        return Backup.fromJson(text)
    }
}
