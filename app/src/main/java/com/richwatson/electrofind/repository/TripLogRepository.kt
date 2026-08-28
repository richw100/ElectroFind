package com.richwatson.electrofind.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.richwatson.electrofind.db.CustomChargeEntity
import com.richwatson.electrofind.db.ReceiptSessionEntity
import com.richwatson.electrofind.db.TripLogDao
import com.richwatson.electrofind.model.MergeMode
import com.richwatson.electrofind.model.TripLogBackup
import com.richwatson.electrofind.model.TripLogSettingsBackup
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.util.ReceiptPdfParser
import com.richwatson.electrofind.work.AutoBackupWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// Backs the Trip tab: parses Electroverse receipt PDFs from a user-picked SAF folder into
// Room (never re-parsing a file it has already imported), plus manual-charge CRUD and the
// exclude toggle. GBP conversion and trip totals live in TripLogViewModel.
class TripLogRepository(
    private val context: Context,
    private val dao: TripLogDao,
    private val prefs: AppPreferences
) {
    fun sessionsInRange(from: Long, to: Long): Flow<List<ReceiptSessionEntity>> =
        dao.sessionsInRange(from, to)

    fun customChargesInRange(from: Long, to: Long): Flow<List<CustomChargeEntity>> =
        dao.customChargesInRange(from, to)

    fun hasFolder(): Boolean = !prefs.tripFolderUri.isNullOrEmpty()

    val folderUri: Uri? get() = prefs.tripFolderUri?.let(Uri::parse)

    fun folderDisplayName(): String? =
        folderUri?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() }

    /** Persist a SAF tree URI picked via ACTION_OPEN_DOCUMENT_TREE and take read permission. */
    fun persistFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        prefs.tripFolderUri = uri.toString()
    }

    data class ScanResult(
        val imported: Int,
        val skipped: Int,
        val failures: List<ReceiptPdfParser.Result.Failure>,
        val error: String? = null
    )

    suspend fun scanFolder(): ScanResult = withContext(Dispatchers.IO) {
        val uriStr = prefs.tripFolderUri
        if (uriStr.isNullOrEmpty()) return@withContext ScanResult(0, 0, emptyList(), "No receipts folder chosen yet")

        ensurePdfBox()
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) }.getOrNull()
        if (tree == null || !tree.canRead()) {
            return@withContext ScanResult(0, 0, emptyList(), "Folder access lost — choose the folder again")
        }

        val known = dao.importedFileNames().toHashSet()
        val candidates = tree.listFiles().filter { f ->
            val n = f.name
            n != null && ReceiptPdfParser.FILENAME_REGEX.matches(n) && n !in known
        }

        val entities = ArrayList<ReceiptSessionEntity>()
        val failures = ArrayList<ReceiptPdfParser.Result.Failure>()
        for (f in candidates) {
            val name = f.name ?: continue
            val result = try {
                context.contentResolver.openInputStream(f.uri)?.use { input ->
                    ReceiptPdfParser.parsePdf(input, name)
                } ?: ReceiptPdfParser.Result.Failure(name, "Could not open file")
            } catch (e: Exception) {
                ReceiptPdfParser.Result.Failure(name, e.message ?: "Read error")
            }
            when (result) {
                is ReceiptPdfParser.Result.Success -> entities.add(result.entity)
                is ReceiptPdfParser.Result.Failure -> failures.add(result)
            }
        }

        var imported = 0
        if (entities.isNotEmpty()) {
            imported = dao.insertSessions(entities).count { it != -1L }
        }
        val skipped = entities.size - imported
        if (imported > 0) AutoBackupWorker.scheduleSoon(context)
        ScanResult(imported, skipped, failures)
    }

    suspend fun setSessionExcluded(id: Long, excluded: Boolean) {
        dao.setSessionExcluded(id, excluded)
        AutoBackupWorker.scheduleSoon(context)
    }

    suspend fun upsertCustomCharge(charge: CustomChargeEntity) {
        dao.upsertCustomCharge(charge)
        AutoBackupWorker.scheduleSoon(context)
    }

    suspend fun setCustomChargeExcluded(id: String, excluded: Boolean) {
        dao.setCustomChargeExcluded(id, excluded)
        AutoBackupWorker.scheduleSoon(context)
    }

    suspend fun deleteCustomCharge(id: String) {
        dao.deleteCustomCharge(id)
        AutoBackupWorker.scheduleSoon(context)
    }

    /** Trip settings (rate, mpg, …) are part of the backup file — nudge the auto-backup. */
    fun scheduleBackup() = AutoBackupWorker.scheduleSoon(context)

    // ── Backup / restore ───────────────────────────────────────────────────
    /** (sessionCount, customChargeCount) — for the Backup & restore preview rows. */
    suspend fun counts(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        dao.allSessions().size to dao.allCustomCharges().size
    }

    suspend fun buildBackup(): TripLogBackup = withContext(Dispatchers.IO) {
        TripLogBackup(
            sessions = dao.allSessions().map { it.toBackup() },
            customCharges = dao.allCustomCharges().map { it.toBackup() },
            settings = TripLogSettingsBackup(
                eurToGbpRate = prefs.tripEurToGbpRate,
                iceMpg = prefs.tripIceMpg,
                petrolPricePerLitre = prefs.tripPetrolPricePerLitre,
                evMilesPerKwh = prefs.tripEvMilesPerKwh,
                milesTravelled = prefs.tripMilesTravelled,
                folderUri = prefs.tripFolderUri
            )
        )
    }

    suspend fun applyImport(backup: TripLogBackup, mode: MergeMode) = withContext(Dispatchers.IO) {
        val sessions = backup.sessions.map { it.toEntity() }
        val charges = backup.customCharges.map { it.toEntity() }
        when (mode) {
            MergeMode.CLEAR_AND_REPLACE -> {
                dao.clearSessions()
                dao.clearCustomCharges()
                dao.insertSessions(sessions)
                charges.forEach { dao.upsertCustomCharge(it) }
            }
            MergeMode.ADD_NO_OVERWRITE -> {
                dao.insertSessions(sessions)
                dao.insertCustomChargesIgnore(charges)
            }
            MergeMode.ADD_AND_OVERWRITE -> {
                dao.deleteSessionsByReceiptNumber(sessions.map { it.receiptNumber })
                dao.insertSessions(sessions)
                charges.forEach { dao.upsertCustomCharge(it) }
            }
        }
        if (mode != MergeMode.ADD_NO_OVERWRITE) {
            backup.settings?.let { st ->
                prefs.tripEurToGbpRate = st.eurToGbpRate
                prefs.tripIceMpg = st.iceMpg
                prefs.tripPetrolPricePerLitre = st.petrolPricePerLitre
                prefs.tripEvMilesPerKwh = st.evMilesPerKwh
                prefs.tripMilesTravelled = st.milesTravelled
                val uri = st.folderUri
                val readable = uri != null && runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(uri))?.canRead() == true
                }.getOrDefault(false)
                if (readable) prefs.tripFolderUri = uri
            }
        }
        AutoBackupWorker.scheduleSoon(context)
    }

    private fun ensurePdfBox() {
        if (pdfBoxReady) return
        synchronized(TripLogRepository::class.java) {
            if (!pdfBoxReady) {
                PDFBoxResourceLoader.init(context.applicationContext)
                pdfBoxReady = true
            }
        }
    }

    companion object {
        @Volatile private var pdfBoxReady = false
    }
}
