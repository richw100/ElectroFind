package com.richwatson.electrofind.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.viewmodel.TripLogViewModel
import com.richwatson.electrofind.viewmodel.TripRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateDisplay = DateTimeFormatter.ofPattern("d MMM yyyy")
private val zone: ZoneId = ZoneId.systemDefault()

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun fmt2(v: Double) = String.format(Locale.UK, "%.2f", v)
private fun fmt3(v: Double) = String.format(Locale.UK, "%.3f", v)
private fun gbp(v: Double) = "£" + fmt2(v)
private fun native(currency: String, v: Double) = (if (currency == "EUR") "€" else "£") + fmt2(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLogScreen(viewModel: TripLogViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { viewModel.folderPicked(it) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.buildCsv().toByteArray()) }
                scope.launch { snackbar.showSnackbar("Exported CSV") }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("Export failed: ${e.message}") }
            }
        }
    }
    fun openFolderPicker() {
        folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    var editRow by remember { mutableStateOf<TripRow?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteRow by remember { mutableStateOf<TripRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip") },
                actions = {
                    if (state.scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.scan() }, enabled = state.folderPicked) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "text/csv"
                                putExtra(Intent.EXTRA_TITLE, viewModel.csvFileName())
                            }
                            csvLauncher.launch(intent)
                        },
                        enabled = state.rows.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Download, "Export CSV")
                    }
                    IconButton(onClick = { openFolderPicker() }) {
                        Icon(Icons.Default.FolderOpen, "Change receipts folder")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.folderPicked) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Charge-session summary", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Choose the folder that holds your Electroverse receipt PDFs " +
                                    "(usually Downloads). ElectroFind reads the receipts to total up " +
                                    "the cost and energy of a trip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { openFolderPicker() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Choose receipts folder")
                            }
                        }
                    }
                }
            }

            item { DateRangeCard(state.rangeStart, state.rangeEnd, viewModel) }

            state.scanMessage?.let { msg ->
                item {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.folderPicked && state.folderName != null) {
                item {
                    Text(
                        "Folder: ${state.folderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.rows, key = { it.key }) { row ->
                TripRowCard(
                    row = row,
                    onToggleExclude = { viewModel.setRowExcluded(row, !row.excluded) },
                    onEdit = { editRow = row },
                    onDelete = { deleteRow = row }
                )
            }

            item {
                OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Add manual charge")
                }
            }

            item { SummaryCard(state, viewModel) }
            item { IceComparisonCard(state, viewModel) }
        }
    }

    if (showAddDialog) {
        CustomChargeDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { date, name, kwh, cost, idle, currency ->
                viewModel.saveCustomCharge(null, date, name, kwh, cost, idle, currency)
                showAddDialog = false
            }
        )
    }
    editRow?.let { row ->
        CustomChargeDialog(
            existing = row,
            onDismiss = { editRow = null },
            onSave = { date, name, kwh, cost, idle, currency ->
                viewModel.saveCustomCharge(row.customId, date, name, kwh, cost, idle, currency)
                editRow = null
            }
        )
    }
    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = { Text("Delete charge?") },
            text = { Text("“${row.name}” will be removed from your trip log.") },
            confirmButton = {
                TextButton(onClick = {
                    row.customId?.let { viewModel.deleteCustomCharge(it) }
                    deleteRow = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteRow = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeCard(rangeStart: Long, rangeEnd: Long, viewModel: TripLogViewModel) {
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Date range", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStart = true }, modifier = Modifier.weight(1f)) {
                    Text(millisToLocalDate(rangeStart).format(dateDisplay))
                }
                OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) {
                    Text(millisToLocalDate(rangeEnd).format(dateDisplay))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "7d" to TripLogViewModel.QuickRange.LAST_7,
                    "30d" to TripLogViewModel.QuickRange.LAST_30,
                    "This month" to TripLogViewModel.QuickRange.THIS_MONTH,
                    "All" to TripLogViewModel.QuickRange.ALL
                ).forEach { (label, q) ->
                    AssistChip(onClick = { viewModel.applyQuickRange(q) }, label = { Text(label) })
                }
            }
        }
    }

    if (showStart) {
        DatePickerScaffold(
            initialDate = millisToLocalDate(rangeStart),
            onDismiss = { showStart = false },
            onPicked = { picked ->
                viewModel.setRangeFromDates(picked, millisToLocalDate(rangeEnd))
                showStart = false
            }
        )
    }
    if (showEnd) {
        DatePickerScaffold(
            initialDate = millisToLocalDate(rangeEnd),
            onDismiss = { showEnd = false },
            onPicked = { picked ->
                viewModel.setRangeFromDates(millisToLocalDate(rangeStart), picked)
                showEnd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerScaffold(initialDate: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    // The M3 date picker works entirely in UTC — feed and read UTC-midnight millis so the
    // day the user taps is the day we get back, regardless of the device's time zone.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    // DatePicker reports UTC midnight for the chosen calendar day.
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun TripRowCard(
    row: TripRow,
    onToggleExclude: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().alpha(if (row.excluded) 0.4f else 1f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        millisToLocalDate(row.dateEpochMillis).format(dateDisplay) + " · " + row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val second = buildString {
                        append(fmt2(row.kwh)).append(" kWh")
                        if (row.kwh > 0) append(" · ").append(gbp(row.gbpPerKwh)).append("/kWh")
                        if (row.idleCost > 0) append(" · idle ").append(native(row.currency, row.idleCost))
                    }
                    Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.receiptNumber != null) {
                        Text(
                            "Receipt ${row.receiptNumber}" + (row.evse?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(native(row.currency, row.totalNative), style = MaterialTheme.typography.titleSmall)
                    if (row.currency == "EUR") {
                        Text("≈ ${gbp(row.totalGbp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include", style = MaterialTheme.typography.labelMedium)
                Switch(checked = !row.excluded, onCheckedChange = { onToggleExclude() })
                Spacer(Modifier.weight(1f))
                if (row.isManual) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: com.richwatson.electrofind.viewmodel.TripLogState, viewModel: TripLogViewModel) {
    val s = state.summary
    var rateText by remember { mutableStateOf(fmt3(state.eurToGbpRate)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Trip totals", style = MaterialTheme.typography.titleMedium)
            SummaryLine("Included charges", s.includedCount.toString())
            SummaryLine("Total energy", "${fmt2(s.totalKwh)} kWh")
            SummaryLine("Total cost", gbp(s.totalCostGbp))
            SummaryLine("Idle / parking", gbp(s.totalIdleGbp))
            SummaryLine("Average price", "${gbp(s.avgGbpPerKwh)}/kWh")
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = rateText,
                onValueChange = {
                    rateText = it
                    it.toDoubleOrNull()?.let { v -> if (v > 0) viewModel.setEurToGbpRate(v) }
                },
                label = { Text("EUR → GBP rate") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun IceComparisonCard(state: com.richwatson.electrofind.viewmodel.TripLogState, viewModel: TripLogViewModel) {
    val s = state.summary
    var miles by remember { mutableStateOf(if (state.milesTravelled > 0) fmt2(state.milesTravelled) else "") }
    var mpg by remember { mutableStateOf(fmt2(state.iceMpg)) }
    var petrol by remember { mutableStateOf(fmt2(state.petrolPricePerLitre)) }
    var miPerKwh by remember { mutableStateOf(fmt2(state.evMilesPerKwh)) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Versus petrol", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = miles,
                onValueChange = {
                    miles = it
                    if (it.isBlank()) viewModel.setMilesTravelled(0.0)
                    else it.toDoubleOrNull()?.let { v -> if (v >= 0) viewModel.setMilesTravelled(v) }
                },
                label = { Text("Miles travelled (optional)") },
                supportingText = {
                    Text(
                        if (s.milesFromInput) "Efficiency: ${fmt2(s.derivedMilesPerKwh)} mi/kWh"
                        else "Leave blank to estimate from energy used"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mpg,
                    onValueChange = { mpg = it; it.toDoubleOrNull()?.let { v -> if (v > 0) viewModel.setIceMpg(v) } },
                    label = { Text("mpg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = petrol,
                    onValueChange = { petrol = it; it.toDoubleOrNull()?.let { v -> if (v > 0) viewModel.setPetrolPricePerLitre(v) } },
                    label = { Text("£/litre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = miPerKwh,
                    onValueChange = { miPerKwh = it; it.toDoubleOrNull()?.let { v -> if (v > 0) viewModel.setEvMilesPerKwh(v) } },
                    label = { Text("mi/kWh") },
                    singleLine = true,
                    enabled = !s.milesFromInput,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            SummaryLine(if (s.milesFromInput) "Distance travelled" else "Estimated distance", "${fmt2(s.miles)} mi")
            SummaryLine("Electricity cost", gbp(s.totalCostGbp))
            SummaryLine("Petrol would cost", gbp(s.iceCostGbp))
            val saving = s.savingGbp
            Text(
                if (saving >= 0) "You saved ${gbp(saving)}" else "Extra cost ${gbp(-saving)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (saving >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomChargeDialog(
    existing: TripRow?,
    onDismiss: () -> Unit,
    onSave: (dateEpochMillis: Long, name: String, kwh: Double, cost: Double, idleCost: Double, currency: String) -> Unit
) {
    var dateMillis by remember {
        mutableStateOf(existing?.dateEpochMillis ?: System.currentTimeMillis())
    }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kwh by remember { mutableStateOf(existing?.let { fmt2(it.kwh) } ?: "") }
    var cost by remember { mutableStateOf(existing?.let { fmt2(it.energyCost) } ?: "") }
    var idle by remember { mutableStateOf(existing?.let { fmt2(it.idleCost) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "GBP") }
    var showPicker by remember { mutableStateOf(false) }

    val kwhVal = kwh.toDoubleOrNull()
    val costVal = cost.toDoubleOrNull()
    val idleVal = if (idle.isBlank()) 0.0 else idle.toDoubleOrNull()
    val valid = name.isNotBlank() && kwhVal != null && kwhVal >= 0 &&
        costVal != null && costVal >= 0 && idleVal != null && idleVal >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add manual charge" else "Edit charge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(millisToLocalDate(dateMillis).format(dateDisplay))
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kwh, onValueChange = { kwh = it },
                    label = { Text("Energy (kWh)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it },
                    label = { Text("Energy cost") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = idle, onValueChange = { idle = it },
                    label = { Text("Idle / parking cost (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GBP", "EUR").forEach { c ->
                        FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(dateMillis, name.trim(), kwhVal ?: 0.0, costVal ?: 0.0, idleVal ?: 0.0, currency) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showPicker) {
        DatePickerScaffold(
            initialDate = millisToLocalDate(dateMillis),
            onDismiss = { showPicker = false },
            onPicked = { picked ->
                dateMillis = picked.atStartOfDay(zone).toInstant().toEpochMilli()
                showPicker = false
            }
        )
    }
}
