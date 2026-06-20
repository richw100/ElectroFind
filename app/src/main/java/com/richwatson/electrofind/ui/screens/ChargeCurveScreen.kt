package com.richwatson.electrofind.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richwatson.electrofind.util.KonaChargeCurve
import com.richwatson.electrofind.viewmodel.ChargerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeCurveScreen(chargerViewModel: ChargerViewModel) {
    val state by chargerViewModel.state.collectAsState()
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kona charge curve") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

                val maxKw = 110f

                fun socX(soc: Float) = padL + (soc / 100f) * w
                fun kwY(kw: Float) = padT + h - (kw / maxKw) * h

                // Grid lines — horizontal (kW)
                listOf(0f, 25f, 50f, 75f, 100f).forEach { kw ->
                    val y = kwY(kw)
                    drawLine(gridColor, Offset(padL, y), Offset(padL + w, y), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${kw.toInt()}",
                        padL - 6f,
                        y + labelSizePx / 3f,
                        android.graphics.Paint().apply {
                            textSize = labelSizePx
                            color = labelColor.toArgb()
                            textAlign = android.graphics.Paint.Align.RIGHT
                            isAntiAlias = true
                        }
                    )
                }

                // Grid lines — vertical (SoC%)
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

                // Session region fill (between start and target SoC)
                if (startSoc < targetSoc) {
                    val x0 = socX(startSoc.toFloat())
                    val x1 = socX(targetSoc.toFloat())
                    drawRect(sessionFill, Offset(x0, padT), androidx.compose.ui.geometry.Size(x1 - x0, h))
                }

                // Curve fill
                val fillPath = Path().apply {
                    moveTo(socX(0f), kwY(0f))
                    for (soc in 0..100) {
                        lineTo(socX(soc.toFloat()), kwY(KonaChargeCurve.powerAtSoc(soc.toFloat())))
                    }
                    lineTo(socX(100f), kwY(0f))
                    close()
                }
                drawPath(fillPath, fillColor)

                // Curve line
                val curvePath = Path().apply {
                    moveTo(socX(0f), kwY(KonaChargeCurve.powerAtSoc(0f)))
                    for (soc in 1..100) {
                        lineTo(socX(soc.toFloat()), kwY(KonaChargeCurve.powerAtSoc(soc.toFloat())))
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

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Accepted power")
                LegendItem(color = Color(0xFF1976D2), label = "Start SoC", dashed = true)
                LegendItem(color = Color(0xFF388E3C), label = "Target SoC", dashed = true)
            }

            HorizontalDivider()

            // Key points summary
            Text("Charge curve summary", style = MaterialTheme.typography.titleSmall)
            listOf(
                "0–10%" to "50 → 91 kW  (ramp-up from low SoC)",
                "11–61%" to "~92–100 kW  (full-power plateau)",
                "62–67%" to "~75 kW  (first taper)",
                "68–73%" to "~45 kW  (second taper)",
                "74–80%" to "~37–46 kW  (gradual reduction)",
                "81–82%" to "~25–31 kW  (sharp drop)",
                "83–92%" to "~25 kW  (low-power plateau)",
                "93–100%" to "24 → 8 kW  (final taper to full)",
            ).forEach { (range, description) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            Text(
                "Battery: 77.4 kWh usable · DC efficiency: 95% · AC efficiency: 88%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
