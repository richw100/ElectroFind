package com.richwatson.electrofind.util

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class TileCoord(val zoom: Int, val x: Int, val y: Int)

object TileCalculator {

    fun latLngToTile(lat: Double, lng: Double, zoom: Int): TileCoord {
        val n = 2.0.pow(zoom)
        val x = floor((lng + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return TileCoord(zoom, x, y)
    }

    // Returns a grid of tiles surrounding the centre tile
    fun surroundingTiles(centre: TileCoord, radius: Int = 1): List<TileCoord> {
        val tiles = mutableListOf<TileCoord>()
        val maxTile = (2.0.pow(centre.zoom) - 1).toInt()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = (centre.x + dx).coerceIn(0, maxTile)
                val ny = (centre.y + dy).coerceIn(0, maxTile)
                tiles.add(TileCoord(centre.zoom, nx, ny))
            }
        }
        return tiles.distinct()
    }

    fun tileToBoundingBox(tile: TileCoord): BoundingBox {
        val n = 2.0.pow(tile.zoom)
        val west = tile.x / n * 360.0 - 180.0
        val east = (tile.x + 1) / n * 360.0 - 180.0
        val north = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * tile.y / n))))
        val south = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * (tile.y + 1) / n))))
        return BoundingBox(south, west, north, east)
    }
}

data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)
