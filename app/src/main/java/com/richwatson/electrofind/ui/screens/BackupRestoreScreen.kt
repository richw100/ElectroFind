package com.richwatson.electrofind.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.model.BackupFile
import com.richwatson.electrofind.model.DataSet
import com.richwatson.electrofind.model.MergeMode
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    chargerViewModel: ChargerViewModel,
    onBack: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val gson = remember { Gson() }

    // Export selections
    val exportSelected = remember { mutableStateMapOf(
        DataSet.CUSTOM_CHARGERS to true,
        DataSet.FAVOURITES to true,
        DataSet.EXCLUDED to true,
        DataSet.TRIPS to true
    ) }

    // Import state
    var importJson by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<BackupFile?>(null) }
    val importSelected = remember { mutableStateMapOf<DataSet, Boolean>() }
    val importModes = remember { mutableStateMapOf<DataSet, MergeMode>() }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                val json = chargerViewModel.buildExportJson(exportSelected.filterValues { it }.keys)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                scope.launch { snackbar.showSnackbar("Exported successfully") }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("Export failed: ${e.message}") }
            }
        }
    }

    // Import file picker launcher
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw Exception("Could not read file")
                val type = object : TypeToken<BackupFile>() {}.type
                val preview: BackupFile = gson.fromJson(json, type) ?: throw Exception("Invalid format")
                importJson = json
                importPreview = preview
                // Reset selections: select all sets present in the file
                importSelected.clear()
                importModes.clear()
                if (preview.customChargers != null) { importSelected[DataSet.CUSTOM_CHARGERS] = true; importModes[DataSet.CUSTOM_CHARGERS] = MergeMode.ADD_AND_OVERWRITE }
                if (preview.favouritePks != null) { importSelected[DataSet.FAVOURITES] = true; importModes[DataSet.FAVOURITES] = MergeMode.ADD_AND_OVERWRITE }
                if (preview.excludedPks != null) { importSelected[DataSet.EXCLUDED] = true; importModes[DataSet.EXCLUDED] = MergeMode.ADD_AND_OVERWRITE }
                if (preview.trips != null) { importSelected[DataSet.TRIPS] = true; importModes[DataSet.TRIPS] = MergeMode.ADD_AND_OVERWRITE }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("Could not read file: ${e.message}") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Export card ──────────────────────────────────────────────────
            Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose what to include in the backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    ExportRow(
                        label = "${state.rawCustomChargers.size} custom charger${if (state.rawCustomChargers.size == 1) "" else "s"}",
                        checked = exportSelected[DataSet.CUSTOM_CHARGERS] == true,
                        onCheckedChange = { exportSelected[DataSet.CUSTOM_CHARGERS] = it }
                    )
                    ExportRow(
                        label = "${state.favouritePks.size} favourite${if (state.favouritePks.size == 1) "" else "s"}",
                        checked = exportSelected[DataSet.FAVOURITES] == true,
                        onCheckedChange = { exportSelected[DataSet.FAVOURITES] = it }
                    )
                    ExportRow(
                        label = "${state.excludedPks.size} excluded",
                        checked = exportSelected[DataSet.EXCLUDED] == true,
                        onCheckedChange = { exportSelected[DataSet.EXCLUDED] = it }
                    )
                    val stopCount = state.trips.sumOf { it.stops.size }
                    ExportRow(
                        label = "${state.trips.size} trip${if (state.trips.size == 1) "" else "s"} ($stopCount stop${if (stopCount == 1) "" else "s"})",
                        checked = exportSelected[DataSet.TRIPS] == true,
                        onCheckedChange = { exportSelected[DataSet.TRIPS] = it }
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val date = LocalDate.now().toString()
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "electrofind-backup-$date.json")
                            }
                            exportLauncher.launch(intent)
                        },
                        enabled = exportSelected.values.any { it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to file")
                    }
                }
            }

            // ── Import card ──────────────────────────────────────────────────
            Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Import", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Select a backup file, then choose what to restore and how.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                            }
                            importLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose file…")
                    }

                    val preview = importPreview
                    if (preview != null) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        preview.customChargers?.let { list ->
                            ImportSection(
                                label = "${list.size} custom charger${if (list.size == 1) "" else "s"}",
                                checked = importSelected[DataSet.CUSTOM_CHARGERS] == true,
                                mode = importModes[DataSet.CUSTOM_CHARGERS] ?: MergeMode.ADD_AND_OVERWRITE,
                                onCheckedChange = { importSelected[DataSet.CUSTOM_CHARGERS] = it },
                                onModeChange = { importModes[DataSet.CUSTOM_CHARGERS] = it }
                            )
                        }
                        preview.favouritePks?.let { list ->
                            ImportSection(
                                label = "${list.size} favourite${if (list.size == 1) "" else "s"}",
                                checked = importSelected[DataSet.FAVOURITES] == true,
                                mode = importModes[DataSet.FAVOURITES] ?: MergeMode.ADD_AND_OVERWRITE,
                                onCheckedChange = { importSelected[DataSet.FAVOURITES] = it },
                                onModeChange = { importModes[DataSet.FAVOURITES] = it }
                            )
                        }
                        preview.excludedPks?.let { list ->
                            ImportSection(
                                label = "${list.size} excluded",
                                checked = importSelected[DataSet.EXCLUDED] == true,
                                mode = importModes[DataSet.EXCLUDED] ?: MergeMode.ADD_AND_OVERWRITE,
                                onCheckedChange = { importSelected[DataSet.EXCLUDED] = it },
                                onModeChange = { importModes[DataSet.EXCLUDED] = it }
                            )
                        }
                        preview.trips?.let { list ->
                            val stops = list.sumOf { it.stops.size }
                            ImportSection(
                                label = "${list.size} trip${if (list.size == 1) "" else "s"} ($stops stop${if (stops == 1) "" else "s"})",
                                checked = importSelected[DataSet.TRIPS] == true,
                                mode = importModes[DataSet.TRIPS] ?: MergeMode.ADD_AND_OVERWRITE,
                                onCheckedChange = { importSelected[DataSet.TRIPS] = it },
                                onModeChange = { importModes[DataSet.TRIPS] = it }
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val json = importJson ?: return@Button
                                val opts = importSelected
                                    .filterValues { it }
                                    .mapValues { (k, _) -> importModes[k] ?: MergeMode.ADD_AND_OVERWRITE }
                                try {
                                    chargerViewModel.applyImport(json, opts)
                                    scope.launch { snackbar.showSnackbar("Imported successfully") }
                                } catch (e: Exception) {
                                    scope.launch { snackbar.showSnackbar("Import failed: ${e.message}") }
                                }
                            },
                            enabled = importSelected.values.any { it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ImportSection(
    label: String,
    checked: Boolean,
    mode: MergeMode,
    onCheckedChange: (Boolean) -> Unit,
    onModeChange: (MergeMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (checked) {
            Column(modifier = Modifier.padding(start = 48.dp)) {
                MergeMode.entries.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = mode == m, onClick = { onModeChange(m) })
                        Text(
                            when (m) {
                                MergeMode.CLEAR_AND_REPLACE -> "Clear and replace"
                                MergeMode.ADD_NO_OVERWRITE -> "Add, keep existing"
                                MergeMode.ADD_AND_OVERWRITE -> "Add and overwrite"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
