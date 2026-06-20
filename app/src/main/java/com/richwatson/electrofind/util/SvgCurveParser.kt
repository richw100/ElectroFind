package com.richwatson.electrofind.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object SvgCurveParser {

    data class ParseResult(
        val points: List<Pair<Float, Float>>,  // (SoC% 0-100, kW)
        val detectedMaxKw: Float,
        val pointCount: Int
    )

    fun parse(svgContent: String): ParseResult? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(svgContent.reader())

            var viewBox: FloatArray? = null
            val paths = mutableListOf<List<Pair<Float, Float>>>()
            val hLines = mutableListOf<Pair<Float, Float>>()  // (y, x1) for horizontal grid lines
            val yAxisLabels = mutableListOf<Pair<Float, Float>>()  // (y, value)

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "svg" -> {
                            parser.getAttributeValue(null, "viewBox")?.let { vb ->
                                val parts = vb.trim().split(Regex("\\s+|,"))
                                if (parts.size == 4) viewBox = parts.map { it.toFloat() }.toFloatArray()
                            }
                        }
                        "path" -> {
                            val d = parser.getAttributeValue(null, "d") ?: ""
                            val fill = parser.getAttributeValue(null, "fill") ?: ""
                            val stroke = parser.getAttributeValue(null, "stroke") ?: ""
                            if (fill == "none" && stroke.isNotEmpty()) {
                                extractPathPoints(d)?.let { pts ->
                                    if (pts.size >= 10) paths.add(pts)
                                }
                            }
                        }
                        "polyline" -> {
                            val pts = parser.getAttributeValue(null, "points") ?: ""
                            extractPolylinePoints(pts)?.let { ps ->
                                if (ps.size >= 10) paths.add(ps)
                            }
                        }
                        "line" -> {
                            val x1 = parser.getAttributeValue(null, "x1")?.toFloatOrNull() ?: 0f
                            val y1 = parser.getAttributeValue(null, "y1")?.toFloatOrNull() ?: 0f
                            val x2 = parser.getAttributeValue(null, "x2")?.toFloatOrNull() ?: 0f
                            val y2 = parser.getAttributeValue(null, "y2")?.toFloatOrNull() ?: 0f
                            // Horizontal grid lines: y1 == y2, spanning a wide range
                            if (y1 == y2 && x2 - x1 > 100f) hLines.add(y1 to x1)
                        }
                        "text" -> {
                            val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                            val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                            val text = buildString {
                                var inner = parser.next()
                                while (inner != XmlPullParser.END_TAG) {
                                    if (inner == XmlPullParser.TEXT) append(parser.text.trim())
                                    inner = parser.next()
                                }
                            }.trim()
                            // Y-axis labels: numeric text near the left edge (small x) of the SVG
                            val numVal = text.trimEnd('%').toFloatOrNull()
                            if (numVal != null && !text.contains('%') && x < 300f) {
                                yAxisLabels.add(y to numVal)
                            }
                        }
                    }
                }
                event = parser.next()
            }

            val mainPath = paths.maxByOrNull { it.size } ?: return null
            val vb = viewBox ?: floatArrayOf(0f, 0f, 1000f, 1000f)

            // Build y-axis calibration from grid lines and labels
            val (xPlotMin, xPlotMax, yPlotBottom, yPlotTop) = calibratePlotArea(hLines, mainPath, vb)
            val maxKw = detectMaxKw(yAxisLabels, yPlotTop, yPlotBottom)

            // Normalise path points to (SoC%, kW)
            val normalised = mainPath.map { (x, y) ->
                val soc = ((x - xPlotMin) / (xPlotMax - xPlotMin) * 100f).coerceIn(0f, 100f)
                val kw = ((yPlotBottom - y) / (yPlotBottom - yPlotTop) * maxKw).coerceIn(0f, maxKw)
                soc to kw
            }.sortedBy { it.first }

            // Resample to 101 integer SoC points
            val resampled = (0..100).map { soc ->
                val socF = soc.toFloat()
                soc.toFloat() to interpolate(normalised, socF)
            }

            ParseResult(resampled, maxKw, mainPath.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun calibratePlotArea(
        hLines: List<Pair<Float, Float>>,
        path: List<Pair<Float, Float>>,
        vb: FloatArray
    ): FloatArray {
        val xs = path.map { it.first }
        val ys = path.map { it.second }

        // X: leftmost and rightmost extents of the path
        val xMin = xs.min()
        val xMax = xs.max()

        // Y: if we have horizontal grid lines, use the outermost ones; otherwise path extents
        val (yBottom, yTop) = if (hLines.size >= 2) {
            hLines.maxOf { it.first } to hLines.minOf { it.first }
        } else {
            ys.max() to ys.min()
        }

        return floatArrayOf(xMin, xMax, yBottom, yTop)
    }

    private fun detectMaxKw(labels: List<Pair<Float, Float>>, yTop: Float, yBottom: Float): Float {
        if (labels.isEmpty()) return 100f
        // The label with the smallest y value (closest to top) is the maximum kW
        return labels.minByOrNull { Math.abs(it.first - yTop) }?.second ?: labels.maxOf { it.second }
    }

    private fun extractPathPoints(d: String): List<Pair<Float, Float>>? {
        val result = mutableListOf<Pair<Float, Float>>()
        val tokenRegex = Regex("[MmLlHhVvCcSsQqTtAaZz]|[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?")
        val tokens = tokenRegex.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var curX = 0f
        var curY = 0f
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t[0]; i++; continue
            }
            when (cmd.uppercaseChar()) {
                'M', 'L' -> {
                    val x = tokens.getOrNull(i)?.toFloatOrNull() ?: break
                    val y = tokens.getOrNull(i + 1)?.toFloatOrNull() ?: break
                    curX = if (cmd.isUpperCase()) x else curX + x
                    curY = if (cmd.isUpperCase()) y else curY + y
                    result.add(curX to curY)
                    i += 2
                    if (cmd == 'M') cmd = 'L'
                }
                'H' -> {
                    val x = tokens.getOrNull(i)?.toFloatOrNull() ?: break
                    curX = if (cmd.isUpperCase()) x else curX + x
                    result.add(curX to curY); i++
                }
                'V' -> {
                    val y = tokens.getOrNull(i)?.toFloatOrNull() ?: break
                    curY = if (cmd.isUpperCase()) y else curY + y
                    result.add(curX to curY); i++
                }
                'C' -> {
                    // Cubic bezier: skip control points, take end point
                    if (i + 5 >= tokens.size) break
                    curX = if (cmd.isUpperCase()) tokens[i + 4].toFloatOrNull() ?: break else curX + (tokens[i + 4].toFloatOrNull() ?: break)
                    curY = if (cmd.isUpperCase()) tokens[i + 5].toFloatOrNull() ?: break else curY + (tokens[i + 5].toFloatOrNull() ?: break)
                    result.add(curX to curY); i += 6
                }
                'Z' -> { i++ }
                else -> i++
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun extractPolylinePoints(points: String): List<Pair<Float, Float>>? {
        val nums = points.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
        if (nums.size < 4) return null
        return nums.chunked(2).mapNotNull { chunk ->
            if (chunk.size == 2) chunk[0] to chunk[1] else null
        }
    }

    private fun interpolate(points: List<Pair<Float, Float>>, soc: Float): Float {
        if (points.isEmpty()) return 0f
        if (soc <= points.first().first) return points.first().second
        if (soc >= points.last().first) return points.last().second
        val idx = points.indexOfFirst { it.first >= soc }.takeIf { it > 0 } ?: return points.last().second
        val lo = points[idx - 1]
        val hi = points[idx]
        val frac = (soc - lo.first) / (hi.first - lo.first)
        return lo.second + (hi.second - lo.second) * frac
    }
}
