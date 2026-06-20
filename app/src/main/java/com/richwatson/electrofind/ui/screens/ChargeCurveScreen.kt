package com.richwatson.electrofind.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.util.SvgCurveParser
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import java.util.UUID
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeCurveScreen(chargerViewModel: ChargerViewModel) {
    val state by chargerViewModel.state.collectAsState()
    val activeProfile = state.activeProfile
    val profiles = state.profiles
    val startSoc = state.startSocPercent
    val targetSoc = state.targetSocPercent

    val colorScheme = MaterialTheme.colorScheme
    val curveColor = colorScheme.primary
    val startColor = Color(0xFF1976D2)
    val targetColor = Color(0xFF388E3C)
    val gridColor = colorScheme.outlineVariant
    val labelColor = colorScheme.onSurfaceVariant
    val fillColor = curveColor.copy(alpha = 0.12f)
    val sessionFill = Color(0xFF1976D2).copy(alpha = 0.10f)

    val density = LocalDensity.current
    val labelSizePx = with(density) { 10.sp.toPx() }

    val context = LocalContext.current

    var dropdownExpanded by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var pendingParseResult by remember { mutableStateOf<SvgCurveParser.ParseResult?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileBatteryKwh by remember { mutableStateOf("77.4") }
    var parseError by remember { mutableStateOf<String?>(null) }

    val svgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                val result = SvgCurveParser.parse(content)
                if (result != null) {
                    pendingParseResult = result
                    newProfileName = ""
                    newProfileBatteryKwh = "77.4"
                    parseError = null
                    showUploadDialog = true
                } else {
                    parseError = "Could not parse SVG — ensure it contains a charge curve path"
                }
            } catch (e: Exception) {
                parseError = "Failed to read file: ${e.message}"
            }
        }
    }

    val maxKw = remember(activeProfile) {
        val raw = activeProfile.rawPoints.maxOfOrNull { it.second } ?: 100f
        (ceil(raw / 25f) * 25f).coerceAtLeast(25f)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Charge curves") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile selector
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = activeProfile.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Car profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    chargerViewModel.setActiveProfile(profile.id)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { svgLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = "Upload SVG curve")
                }
                if (activeProfile.id != CarProfile.KONA_LR_ID) {
                    IconButton(onClick = { chargerViewModel.deleteProfile(activeProfile.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete profile", tint = colorScheme.error)
                    }
                }
            }

            parseError?.let { err ->
                Text(err, color = colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text(
                "Maximum accepted charge power (kW) at each state of charge (SoC%)",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                val padL = 48.dp.toPx()
                val padR = 16.dp.toPx()
                val padT = 16.dp.toPx()
                val padB = 36.dp.toPx()
                val w = size.width - padL - padR
                val h = size.height - padT - padB

                fun socX(soc: Float) = padL + (soc / 100f) * w
                fun kwY(kw: Float) = padT + h - (kw / maxKw) * h

                // Horizontal grid lines (kW)
                val kwStep = if (maxKw <= 100f) 25f else 50f
                var kwGrid = 0f
                while (kwGrid <= maxKw) {
                    val y = kwY(kwGrid)
                    drawLine(gridColor, Offset(padL, y), Offset(padL + w, y), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${kwGrid.toInt()}",
                        padL - 6f,
                        y + labelSizePx / 3f,
                        android.graphics.Paint().apply {
                            textSize = labelSizePx
                            color = labelColor.toArgb()
                            textAlign = android.graphics.Paint.Align.RIGHT
                            isAntiAlias = true
                        }
                    )
                    kwGrid += kwStep
                }

                // Vertical grid lines (SoC%)
                listOf(0, 20, 40, 60, 80, 100).forEach { soc ->
                    val x = socX(soc.toFloat())
                    drawLine(gridColor, Offset(x, padT), Offset(x, padT + h), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "$soc%",
                        x,
                        padT + h + padB * 0.65f,
                        android.graphics.Paint().apply {
                            textSize = labelSizePx
                            color = labelColor.toArgb()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }

                // Axes
                drawLine(colorScheme.outline, Offset(padL, padT), Offset(padL, padT + h), strokeWidth = 2f)
                drawLine(colorScheme.outline, Offset(padL, padT + h), Offset(padL + w, padT + h), strokeWidth = 2f)

                // Session region
                if (startSoc < targetSoc) {
                    val x0 = socX(startSoc.toFloat())
                    val x1 = socX(targetSoc.toFloat())
                    drawRect(sessionFill, Offset(x0, padT), androidx.compose.ui.geometry.Size(x1 - x0, h))
                }

                // Curve fill
                val fillPath = Path().apply {
                    moveTo(socX(0f), kwY(0f))
                    for (soc in 0..100) {
                        lineTo(socX(soc.toFloat()), kwY(activeProfile.powerAtSoc(soc.toFloat())))
                    }
                    lineTo(socX(100f), kwY(0f))
                    close()
                }
                drawPath(fillPath, fillColor)

                // Curve line
                val curvePath = Path().apply {
                    moveTo(socX(0f), kwY(activeProfile.powerAtSoc(0f)))
                    for (soc in 1..100) {
                        lineTo(socX(soc.toFloat()), kwY(activeProfile.powerAtSoc(soc.toFloat())))
                    }
                }
                drawPath(curvePath, curveColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                // Start SoC marker
                val startX = socX(startSoc.toFloat())
                drawLine(startColor, Offset(startX, padT), Offset(startX, padT + h), strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                drawContext.canvas.nativeCanvas.drawText(
                    "Start\n$startSoc%",
                    startX + 4f,
                    padT + labelSizePx * 1.2f,
                    android.graphics.Paint().apply {
                        textSize = labelSizePx * 0.9f
                        color = startColor.toArgb()
                        isAntiAlias = true
                    }
                )

                // Target SoC marker
                val targetX = socX(targetSoc.toFloat())
                drawLine(targetColor, Offset(targetX, padT), Offset(targetX, padT + h), strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                drawContext.canvas.nativeCanvas.drawText(
                    "Target\n$targetSoc%",
                    targetX - 4f,
                    padT + labelSizePx * 1.2f,
                    android.graphics.Paint().apply {
                        textSize = labelSizePx * 0.9f
                        color = targetColor.toArgb()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendItem(color = colorScheme.primary, label = "Accepted power")
                LegendItem(color = Color(0xFF1976D2), label = "Start SoC", dashed = true)
                LegendItem(color = Color(0xFF388E3C), label = "Target SoC", dashed = true)
            }

            HorizontalDivider()

            Text("Profile stats", style = MaterialTheme.typography.titleSmall)
            val peakKw = activeProfile.rawPoints.maxOfOrNull { it.second } ?: 0f
            val avgKw2080 = (20..80).map { activeProfile.powerAtSoc(it.toFloat()) }.average().toFloat()
            val kwAt80 = activeProfile.powerAtSoc(80f)
            listOf(
                "Battery" to "%.1f kWh usable".format(activeProfile.batteryKwh),
                "Peak power" to "%.0f kW".format(peakKw),
                "Avg 20–80%" to "%.0f kW".format(avgKw2080),
                "At 80% SoC" to "%.0f kW".format(kwAt80),
            ).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = colorScheme.primary)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            Text(
                "DC efficiency: 95% · AC efficiency: 88%",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }

    if (showUploadDialog && pendingParseResult != null) {
        val parse = pendingParseResult!!
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Add car profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Detected ${parse.pointCount} curve points · peak ~${parse.detectedMaxKw.toInt()} kW",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Car name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProfileBatteryKwh,
                        onValueChange = { newProfileBatteryKwh = it },
                        label = { Text("Usable battery (kWh)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val profile = CarProfile(
                            id = UUID.randomUUID().toString(),
                            name = newProfileName.trim().ifBlank { "Custom profile" },
                            batteryKwh = newProfileBatteryKwh.toDoubleOrNull() ?: 77.4,
                            rawPoints = parse.points
                        )
                        chargerViewModel.saveProfile(profile)
                        showUploadDialog = false
                    },
                    enabled = newProfileName.isNotBlank() && (newProfileBatteryKwh.toDoubleOrNull() ?: 0.0) > 0
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(24.dp, 12.dp)) {
            val y = size.height / 2
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.5.dp.toPx(),
                pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
