package com.richwatson.electrofind.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richwatson.electrofind.db.CustomChargeEntity
import com.richwatson.electrofind.db.ReceiptSessionEntity
import com.richwatson.electrofind.model.MergeMode
import com.richwatson.electrofind.model.TripLogBackup
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.TripLogRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// A session or a manual charge, flattened for display. Costs in `*Cost`/`totalNative` are in
// the row's own currency; `totalGbp`/`gbpPerKwh` are converted with the user's EUR->GBP rate.
data class TripRow(
    val key: String,
    val dateEpochMillis: Long,
    val name: String,
    val evse: String?,
    val provider: String?,
    val receiptNumber: String?,
    val currency: String,
    val kwh: Double,
    val energyCost: Double,
    val idleCost: Double,
    val totalNative: Double,
    val totalGbp: Double,
    val gbpPerKwh: Double,
    val excluded: Boolean,
    val isManual: Boolean,
    val sessionId: Long? = null,
    val customId: String? = null,
    val startEpochMillis: Long? = null,
    val endEpochMillis: Long? = null,
    val address: String? = null
)

data class TripSummary(
    val includedCount: Int = 0,
    val totalKwh: Double = 0.0,
    val totalCostGbp: Double = 0.0,
    val totalIdleGbp: Double = 0.0,
    val avgGbpPerKwh: Double = 0.0,
    // Actual miles when the user entered a trip distance, otherwise energy × mi/kWh estimate.
    val miles: Double = 0.0,
    val milesFromInput: Boolean = false,
    // miles / totalKwh — meaningful only when miles came from a real distance entry.
    val derivedMilesPerKwh: Double = 0.0,
    val iceCostGbp: Double = 0.0,
    val savingGbp: Double = 0.0
)

data class TripLogState(
    val rangeStart: Long = 0L,
    val rangeEnd: Long = 0L,
    val rows: List<TripRow> = emptyList(),
    val summary: TripSummary = TripSummary(),
    val folderPicked: Boolean = false,
    val folderName: String? = null,
    val scanning: Boolean = false,
    val scanMessage: String? = null,
    val eurToGbpRate: Double = 0.86,
    val iceMpg: Double = 40.0,
    val petrolPricePerLitre: Double = 1.45,
    val evMilesPerKwh: Double = 3.5,
    val milesTravelled: Double = 0.0
)

private const val LITRES_PER_GALLON = 4.546

@OptIn(ExperimentalCoroutinesApi::class)
class TripLogViewModel(
    private val repo: TripLogRepository,
    private val prefs: AppPreferences,
    application: Application
) : ViewModel() {

    private data class Settings(
        val eurToGbpRate: Double,
        val iceMpg: Double,
        val petrolPricePerLitre: Double,
        val evMilesPerKwh: Double,
        val milesTravelled: Double
    )

    private fun currentSettings() = Settings(
        prefs.tripEurToGbpRate,
        prefs.tripIceMpg,
        prefs.tripPetrolPricePerLitre,
        prefs.tripEvMilesPerKwh,
        prefs.tripMilesTravelled
    )

    private val zone: ZoneId = ZoneId.systemDefault()

    private val rangeFlow = MutableStateFlow(initialRange())
    private val settingsFlow = MutableStateFlow(currentSettings())

    private val _state = MutableStateFlow(
        TripLogState(
            rangeStart = rangeFlow.value.first,
            rangeEnd = rangeFlow.value.second,
            folderPicked = repo.hasFolder(),
            folderName = if (repo.hasFolder()) repo.folderDisplayName() else null,
            eurToGbpRate = settingsFlow.value.eurToGbpRate,
            iceMpg = settingsFlow.value.iceMpg,
            petrolPricePerLitre = settingsFlow.value.petrolPricePerLitre,
            evMilesPerKwh = settingsFlow.value.evMilesPerKwh,
            milesTravelled = settingsFlow.value.milesTravelled
        )
    )
    val state: StateFlow<TripLogState> = _state.asStateFlow()

    init {
        rangeFlow
            .flatMapLatest { (from, to) ->
                combine(
                    repo.sessionsInRange(from, to),
                    repo.customChargesInRange(from, to),
                    settingsFlow
                ) { sessions, charges, settings -> Triple(sessions, charges, settings) }
            }
            .onEach { (sessions, charges, settings) ->
                val rows = buildRows(sessions, charges, settings)
                val summary = buildSummary(rows, settings)
                _state.update {
                    it.copy(
                        rows = rows,
                        summary = summary,
                        rangeStart = rangeFlow.value.first,
                        rangeEnd = rangeFlow.value.second,
                        eurToGbpRate = settings.eurToGbpRate,
                        iceMpg = settings.iceMpg,
                        petrolPricePerLitre = settings.petrolPricePerLitre,
                        evMilesPerKwh = settings.evMilesPerKwh,
                        milesTravelled = settings.milesTravelled
                    )
                }
            }
            .launchIn(viewModelScope)

        if (repo.hasFolder()) scan()
    }

    // ── Range ──────────────────────────────────────────────────────────────
    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOfDay(date: LocalDate): Long =
        date.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

    private fun initialRange(): Pair<Long, Long> {
        val today = LocalDate.now(zone)
        val start = prefs.tripRangeStartEpochMillis.takeIf { it != 0L }
            ?: startOfDay(today.minusDays(30))
        val end = prefs.tripRangeEndEpochMillis.takeIf { it != 0L }
            ?: endOfDay(today)
        return start to end
    }

    fun setRange(startMillis: Long, endMillis: Long) {
        prefs.tripRangeStartEpochMillis = startMillis
        prefs.tripRangeEndEpochMillis = endMillis
        rangeFlow.value = startMillis to endMillis
        _state.update { it.copy(rangeStart = startMillis, rangeEnd = endMillis) }
    }

    fun setRangeFromDates(startDate: LocalDate, endDate: LocalDate) =
        setRange(startOfDay(startDate), endOfDay(endDate))

    enum class QuickRange { LAST_7, LAST_30, THIS_MONTH, ALL }

    fun applyQuickRange(q: QuickRange) {
        val today = LocalDate.now(zone)
        when (q) {
            QuickRange.LAST_7 -> setRange(startOfDay(today.minusDays(7)), endOfDay(today))
            QuickRange.LAST_30 -> setRange(startOfDay(today.minusDays(30)), endOfDay(today))
            QuickRange.THIS_MONTH -> setRange(startOfDay(today.withDayOfMonth(1)), endOfDay(today))
            QuickRange.ALL -> setRange(0L, endOfDay(today.plusYears(1)))
        }
    }

    // ── Settings ───────────────────────────────────────────────────────────
    fun setEurToGbpRate(v: Double) {
        prefs.tripEurToGbpRate = v
        settingsFlow.update { it.copy(eurToGbpRate = v) }
        repo.scheduleBackup()
    }

    fun setIceMpg(v: Double) {
        prefs.tripIceMpg = v
        settingsFlow.update { it.copy(iceMpg = v) }
        repo.scheduleBackup()
    }

    fun setPetrolPricePerLitre(v: Double) {
        prefs.tripPetrolPricePerLitre = v
        settingsFlow.update { it.copy(petrolPricePerLitre = v) }
        repo.scheduleBackup()
    }

    fun setEvMilesPerKwh(v: Double) {
        prefs.tripEvMilesPerKwh = v
        settingsFlow.update { it.copy(evMilesPerKwh = v) }
        repo.scheduleBackup()
    }

    /** 0.0 clears it (miles fall back to the energy × mi/kWh estimate). */
    fun setMilesTravelled(v: Double) {
        prefs.tripMilesTravelled = v
        settingsFlow.update { it.copy(milesTravelled = v) }
        repo.scheduleBackup()
    }

    // ── Folder + scan ──────────────────────────────────────────────────────
    fun folderPicked(uri: Uri) {
        repo.persistFolder(uri)
        _state.update { it.copy(folderPicked = true, folderName = repo.folderDisplayName()) }
        scan()
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, scanMessage = "Scanning receipts…") }
        viewModelScope.launch {
            val r = repo.scanFolder()
            val msg = when {
                r.error != null -> r.error
                r.imported == 0 && r.skipped == 0 && r.failures.isEmpty() -> "No new receipts found"
                else -> buildString {
                    append("Imported ${r.imported}")
                    if (r.skipped > 0) append(" · skipped ${r.skipped} already imported")
                    if (r.failures.isNotEmpty()) {
                        append(" · ${r.failures.size} could not be read (")
                        append(r.failures.joinToString("; ") { "${it.fileName}: ${it.reason}" })
                        append(")")
                    }
                }
            }
            _state.update {
                it.copy(
                    scanning = false,
                    scanMessage = msg,
                    folderPicked = repo.hasFolder(),
                    folderName = repo.folderDisplayName()
                )
            }
        }
    }

    // ── Mutations ──────────────────────────────────────────────────────────
    fun setRowExcluded(row: TripRow, excluded: Boolean) {
        viewModelScope.launch {
            when {
                row.isManual && row.customId != null -> repo.setCustomChargeExcluded(row.customId, excluded)
                row.sessionId != null -> repo.setSessionExcluded(row.sessionId, excluded)
            }
        }
    }

    /** id null → new manual charge; otherwise edit the existing one. */
    fun saveCustomCharge(
        id: String?,
        dateEpochMillis: Long,
        name: String,
        kwh: Double,
        cost: Double,
        idleCost: Double,
        currency: String
    ) {
        val existingExcluded = id?.let { cid -> _state.value.rows.firstOrNull { it.customId == cid }?.excluded } ?: false
        val entity = CustomChargeEntity(
            id = id ?: UUID.randomUUID().toString(),
            dateEpochMillis = dateEpochMillis,
            name = name,
            kwh = kwh,
            cost = cost,
            idleCost = idleCost,
            currency = currency,
            excluded = existingExcluded
        )
        viewModelScope.launch { repo.upsertCustomCharge(entity) }
    }

    fun deleteCustomCharge(id: String) {
        viewModelScope.launch { repo.deleteCustomCharge(id) }
    }

    // ── Backup / restore ───────────────────────────────────────────────────
    suspend fun buildBackup(): TripLogBackup = repo.buildBackup()

    /** (sessionCount, customChargeCount) for the Backup & restore preview. */
    suspend fun counts(): Pair<Int, Int> = repo.counts()

    fun applyImport(backup: TripLogBackup, mode: MergeMode) {
        viewModelScope.launch {
            repo.applyImport(backup, mode)
            settingsFlow.value = currentSettings()
            _state.update {
                it.copy(folderPicked = repo.hasFolder(), folderName = repo.folderDisplayName())
            }
        }
    }

    // ── Derived data ───────────────────────────────────────────────────────
    private fun buildRows(
        sessions: List<ReceiptSessionEntity>,
        charges: List<CustomChargeEntity>,
        s: Settings
    ): List<TripRow> {
        fun toGbp(v: Double, currency: String) = if (currency == "EUR") v * s.eurToGbpRate else v

        val sessionRows = sessions.map { e ->
            val totalGbp = toGbp(e.totalGross, e.currency)
            TripRow(
                key = "s${e.id}",
                dateEpochMillis = e.startEpochMillis,
                name = e.provider ?: "Charge session",
                evse = e.evse,
                provider = e.provider,
                receiptNumber = e.receiptNumber,
                currency = e.currency,
                kwh = e.kwh,
                energyCost = e.energyCostGross,
                idleCost = e.idleCostGross,
                totalNative = e.totalGross,
                totalGbp = totalGbp,
                gbpPerKwh = if (e.kwh > 0) totalGbp / e.kwh else 0.0,
                excluded = e.excluded,
                isManual = false,
                sessionId = e.id,
                startEpochMillis = e.startEpochMillis,
                endEpochMillis = e.endEpochMillis,
                address = e.address
            )
        }
        val chargeRows = charges.map { c ->
            val native = c.cost + c.idleCost
            val totalGbp = toGbp(native, c.currency)
            TripRow(
                key = "c${c.id}",
                dateEpochMillis = c.dateEpochMillis,
                name = c.name.ifBlank { "Manual charge" },
                evse = null,
                provider = null,
                receiptNumber = null,
                currency = c.currency,
                kwh = c.kwh,
                energyCost = c.cost,
                idleCost = c.idleCost,
                totalNative = native,
                totalGbp = totalGbp,
                gbpPerKwh = if (c.kwh > 0) totalGbp / c.kwh else 0.0,
                excluded = c.excluded,
                isManual = true,
                customId = c.id,
                startEpochMillis = c.dateEpochMillis
            )
        }
        return (sessionRows + chargeRows).sortedByDescending { it.dateEpochMillis }
    }

    private fun buildSummary(rows: List<TripRow>, s: Settings): TripSummary {
        val inc = rows.filter { !it.excluded }
        val totalKwh = inc.sumOf { it.kwh }
        val totalCostGbp = inc.sumOf { it.totalGbp }
        val totalIdleGbp = inc.sumOf { if (it.currency == "EUR") it.idleCost * s.eurToGbpRate else it.idleCost }
        val milesFromInput = s.milesTravelled > 0
        val miles = if (milesFromInput) s.milesTravelled else totalKwh * s.evMilesPerKwh
        val iceCost = if (s.iceMpg > 0) miles / s.iceMpg * LITRES_PER_GALLON * s.petrolPricePerLitre else 0.0
        return TripSummary(
            includedCount = inc.size,
            totalKwh = totalKwh,
            totalCostGbp = totalCostGbp,
            totalIdleGbp = totalIdleGbp,
            avgGbpPerKwh = if (totalKwh > 0) totalCostGbp / totalKwh else 0.0,
            miles = miles,
            milesFromInput = milesFromInput,
            derivedMilesPerKwh = if (milesFromInput && totalKwh > 0) s.milesTravelled / totalKwh else 0.0,
            iceCostGbp = iceCost,
            savingGbp = iceCost - totalCostGbp
        )
    }

    // ── CSV export ─────────────────────────────────────────────────────────
    fun csvFileName(): String {
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val st = _state.value
        val start = Instant.ofEpochMilli(st.rangeStart).atZone(zone).toLocalDate().format(f)
        val end = Instant.ofEpochMilli(st.rangeEnd).atZone(zone).toLocalDate().format(f)
        return "electrofind-trip-$start-to-$end.csv"
    }

    fun buildCsv(): String {
        val st = _state.value
        val s = st.summary
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun n(v: Double) = String.format(Locale.UK, "%.2f", v)
        fun n3(v: Double) = String.format(Locale.UK, "%.3f", v)

        val sb = StringBuilder()
        sb.append("Date,Start,End,Name,EVSE,Provider,Receipt,Source,Currency,kWh,Energy cost,Idle/parking cost,Total (native),Total (GBP),GBP per kWh,Excluded\n")
        for (r in st.rows) {
            val date = Instant.ofEpochMilli(r.dateEpochMillis).atZone(zone).toLocalDate().format(dateFmt)
            val start = r.startEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).format(dtFmt) } ?: ""
            val end = if (!r.isManual) {
                r.endEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).format(dtFmt) } ?: ""
            } else ""
            val cols = listOf(
                date, start, end, r.name, r.evse ?: "", r.provider ?: "", r.receiptNumber ?: "",
                if (r.isManual) "manual" else "receipt", r.currency,
                n(r.kwh), n(r.energyCost), n(r.idleCost), n(r.totalNative), n(r.totalGbp),
                n3(r.gbpPerKwh), if (r.excluded) "yes" else "no"
            )
            sb.append(cols.joinToString(",") { csvEscape(it) }).append("\n")
        }
        sb.append("\n")
        sb.append("Total kWh,${n(s.totalKwh)}\n")
        sb.append("Total cost (GBP),${n(s.totalCostGbp)}\n")
        sb.append("Average GBP/kWh,${n3(s.avgGbpPerKwh)}\n")
        sb.append("EUR to GBP rate used,${n3(st.eurToGbpRate)}\n")
        sb.append("${if (s.milesFromInput) "Miles travelled" else "Estimated miles"},${n(s.miles)}\n")
        if (s.milesFromInput) sb.append("Derived mi/kWh,${n3(s.derivedMilesPerKwh)}\n")
        sb.append("ICE cost (GBP),${n(s.iceCostGbp)}\n")
        sb.append("Saving (GBP),${n(s.savingGbp)}\n")
        return sb.toString()
    }

    private fun csvEscape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v
}
