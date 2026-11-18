package de.fernfahrer.fahrtenbuch.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Ordner für automatische Sicherungen (vom Nutzer einmal gewählt). */
interface BackupFolder {
    data class Entry(val id: String, val name: String)

    fun list(): List<Entry>
    fun write(name: String, bytes: ByteArray)
    fun delete(entry: Entry)
}

/**
 * Ordner aus der Android-Ordnerauswahl (Storage Access Framework): funktioniert mit Ordnern auf dem
 * Handy, der SD-Karte und Cloud-Speichern wie Google Drive.
 */
class SafBackupFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val dirId: String = DocumentsContract.getTreeDocumentId(tree)

    override fun list(): List<BackupFolder.Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(children, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(BackupFolder.Entry(c.getString(0), c.getString(1).orEmpty())) }
        } ?: error("Ordner kann nicht gelesen werden")
    }

    override fun write(name: String, bytes: ByteArray) {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, dirId)
        val uri = DocumentsContract.createDocument(resolver, dir, "application/json", name)
            ?: error("Datei konnte nicht angelegt werden")
        resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Datei konnte nicht geschrieben werden")
    }

    override fun delete(entry: BackupFolder.Entry) {
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, entry.id))
    }

    companion object {
        /** Anzeigename des gewählten Ordners (z. B. „Fahrtenbuch“) oder null. */
        fun displayName(resolver: ContentResolver, tree: Uri): String? = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }
}
