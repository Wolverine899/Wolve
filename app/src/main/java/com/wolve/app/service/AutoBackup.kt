package com.wolve.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.wolve.app.data.BackupZip
import com.wolve.app.data.Graph
import java.util.Calendar

/** Daily backup (around 1 am) into a folder the user chose. Point the folder at Drive or Dropbox for off-device copies. */
object AutoBackup {
    private const val KEEP = 7

    fun enabled(): Boolean = Graph.store.gb("auto_backup", false) && Graph.store.gs("auto_backup_uri", "").isNotEmpty()

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 21, Intent(ctx, AutoBackupReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun scheduleIfEnabled(ctx: Context) {
        if (enabled()) schedule(ctx) else cancel(ctx)
    }

    fun schedule(ctx: Context) {
        try {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, 1)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(ctx))
        } catch (e: Exception) {
        }
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        } catch (e: Exception) {
        }
    }

    /** Writes one backup now and removes older ones beyond the newest [KEEP]. Returns false if the folder is not usable. */
    fun runNow(ctx: Context): Boolean {
        val uriStr = Graph.store.gs("auto_backup_uri", "")
        if (uriStr.isEmpty()) return false
        return try {
            val tree = Uri.parse(uriStr)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date())
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/zip", "wolve-backup-$stamp.zip") ?: return false
            ctx.contentResolver.openOutputStream(doc)?.use { it.write(BackupZip.build(Graph.store)) }
            prune(ctx, tree, rootId)
            Graph.store.ss("auto_backup_last", stamp)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun prune(ctx: Context, tree: Uri, rootId: String) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val found = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(
            children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith("wolve-backup-") && name.endsWith(".zip")) found.add(Pair(name, c.getString(0)))
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { f ->
            try {
                DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, f.second))
            } catch (e: Exception) {
            }
        }
    }
}

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (AutoBackup.enabled()) {
            AutoBackup.runNow(context)
            AutoBackup.schedule(context)
        }
    }
}
