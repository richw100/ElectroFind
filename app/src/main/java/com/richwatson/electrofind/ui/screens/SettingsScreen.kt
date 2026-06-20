package com.richwatson.electrofind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import com.richwatson.electrofind.viewmodel.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    chargerViewModel: ChargerViewModel,
    appPreferences: AppPreferences,
    onBack: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    var localStartSoc by remember(state.startSocPercent) { mutableIntStateOf(state.startSocPercent) }
    var localTargetSoc by remember(state.targetSocPercent) { mutableIntStateOf(state.targetSocPercent) }
    var localStayMins by remember(state.stayMinutes) { mutableIntStateOf(state.stayMinutes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Auto").forEach { (mode, label) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { chargerViewModel.setThemeMode(mode) },
                        label = { Text(label) }
                    )
                }
            }

            HorizontalDivider()

            Text("Charge session", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Current SoC", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$localStartSoc%", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = localStartSoc.toFloat(),
                onValueChange = { localStartSoc = it.toInt() },
                onValueChangeFinished = { chargerViewModel.setChargeSession(localStartSoc, localTargetSoc, localStayMins) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target SoC", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$localTargetSoc%", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = localTargetSoc.toFloat(),
                onValueChange = { localTargetSoc = it.toInt() },
                onValueChangeFinished = { chargerViewModel.setChargeSession(localStartSoc, localTargetSoc, localStayMins) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stay time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        localStayMins < 60 -> "$localStayMins min"
                        localStayMins % 60 == 0 -> "${localStayMins / 60} hr"
                        else -> "${localStayMins / 60}h ${localStayMins % 60}m"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = localStayMins.toFloat(),
                onValueChange = { localStayMins = it.toInt() },
                onValueChangeFinished = { chargerViewModel.setChargeSession(localStartSoc, localTargetSoc, localStayMins) },
                valueRange = 0f..720f,
                steps = 143,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("Search radius", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { miles ->
                    FilterChip(
                        selected = state.searchRadiusMiles == miles,
                        onClick = { chargerViewModel.setSearchRadius(miles) },
                        label = { Text("$miles mi") }
                    )
                }
            }
        }
    }
}
