/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.Line3D
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.data.InspectData
import dev.nikomaru.advancerailway.error.DataSearchError
import dev.nikomaru.advancerailway.error.RailTraceError
import dev.nikomaru.advancerailway.file.data.ConfigData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.utils.Utils.json
import dev.nikomaru.advancerailway.utils.coroutines.minecraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.block.data.Rail
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object RailwayUtils: KoinComponent {
    val plugin: AdvanceRailway by inject()
    val config: ConfigData by inject()

    fun getRailAvailableDirection(point: Point3D): List<Triple<Int, Int, Int>> {
        val location = point.toLocation(Bukkit.getWorld("world")!!)
        val state = (location.block.blockData as Rail).shape
        return when (state) {
            Rail.Shape.EAST_WEST -> {
                listOf(
                    Triple(1, 0, 0), Triple(-1, 0, 0), Triple(1, -1, 0), Triple(-1, -1, 0)
                )
            }

            Rail.Shape.NORTH_SOUTH -> {
                listOf(
                    Triple(0, -1, -1), Triple(0, -1, 1), Triple(0, 0, -1), Triple(0, 0, 1)
                )
            }

            Rail.Shape.ASCENDING_EAST -> {
                listOf(
                    Triple(-1, 0, 0), Triple(-1, -1, 0), Triple(1, 1, 0)
                )
            }

            Rail.Shape.ASCENDING_WEST -> {
                listOf(
                    Triple(1, 0, 0), Triple(1, -1, 0), Triple(-1, 1, 0)
                )
            }

            Rail.Shape.ASCENDING_NORTH -> {
                listOf(
                    Triple(0, 0, 1), Triple(0, -1, 1), Triple(0, 1, -1)
                )
            }

            Rail.Shape.ASCENDING_SOUTH -> {
                listOf(
                    Triple(0, 0, -1), Triple(0, -1, -1), Triple(0, 1, 1)
                )
            }

            Rail.Shape.SOUTH_EAST -> {
                listOf(
                    Triple(0, -1, 1), Triple(0, 0, 1), Triple(1, 0, 0), Triple(1, -1, 0)
                )
            }

            Rail.Shape.SOUTH_WEST -> {
                listOf(
                    Triple(0, -1, 1), Triple(0, 0, 1), Triple(-1, 0, 0), Triple(-1, -1, 0)
                )
            }

            Rail.Shape.NORTH_EAST -> {
                listOf(
                    Triple(0, -1, -1), Triple(0, 0, -1), Triple(1, 0, 0), Triple(1, -1, 0)
                )
            }

            Rail.Shape.NORTH_WEST -> {
                listOf(
                    Triple(0, -1, -1), Triple(0, 0, -1), Triple(-1, 0, 0), Triple(-1, -1, 0)
                )
            }
        }
    }

    suspend fun railEndpointInspect(
        first: Point3D, directionPoint: Point3D
    ): Either<RailTraceError, Pair<InspectData, InspectData>> = withContext(Dispatchers.minecraft) {
        var forward = InspectData(first, directionPoint, null)
        var backward = InspectData(null, null, first)
        var previousPoint = first
        var currentPoint = directionPoint
        var count = 0

        while (count < config.limit) {
            val diff = previousPoint.getDiff(currentPoint)
            val list = getRailAvailableDirection(currentPoint).toMutableList()
            list.removeIf {
                it.first == -diff.first.toInt() && it.second == -diff.second.toInt() && it.third == -diff.third.toInt()
            }
            val nextPoint = list.map { (x, y, z) ->
                Point3D(currentPoint.x + x, currentPoint.y + y, currentPoint.z + z)
            }
            val rails = nextPoint.filter { it.toLocation(Bukkit.getWorld("world")!!).block.blockData is Rail }
            if (rails.isEmpty()) {
                forward = forward.copy(end = currentPoint)
                backward = backward.copy(start = currentPoint, direction = previousPoint)
                return@withContext Pair(forward, backward).right()
            }
            if (rails.size > 1) {
                return@withContext RailTraceError.MULTIPLE_RAIL.left()
            }
            previousPoint = currentPoint
            currentPoint = rails.first()

            count++
        }
        return@withContext RailTraceError.ATTACHED_TO_LIMIT.left()
    }

    suspend fun getLine(
        startPoint: Point3D, directionPoint: Point3D, endPoint: Point3D
    ): Either<RailTraceError, Line3D> = withContext(Dispatchers.minecraft) {
        var previousPoint = startPoint
        var currentPoint = directionPoint
        var count = 0
        val line = Line3D(startPoint, directionPoint)
        while (count < config.limit) {
            val diff = previousPoint.getDiff(currentPoint)
            val list = getRailAvailableDirection(currentPoint).toMutableList()
            list.removeIf {
                it.first == -diff.first.toInt() && it.second == -diff.second.toInt() && it.third == -diff.third.toInt()
            }
            val nextPoint = list.map { (x, y, z) ->
                Point3D(currentPoint.x + x, currentPoint.y + y, currentPoint.z + z)
            }
            val rails = nextPoint.filter { it.toLocation(Bukkit.getWorld("world")!!).block.blockData is Rail }
            if (rails.isEmpty()) {
                return@withContext RailTraceError.NOT_FOUND_END_POINT.left()
            }
            if (rails.size > 1) {
                return@withContext RailTraceError.MULTIPLE_RAIL.left()
            }
            previousPoint = currentPoint
            currentPoint = rails.first()
            if (currentPoint == endPoint) {
                line.addPoint(currentPoint)
                return@withContext line.right()
            }
            if (count % 2 == 0) {
                line.addPoint(currentPoint)
            }
            count++
        }
        return@withContext RailTraceError.ATTACHED_TO_LIMIT.left()
    }


    suspend fun getRailwayData(railwayId: RailwayId): Either<DataSearchError, RailwayData> =
        withContext(Dispatchers.IO) {
            val folder = StationUtils.plugin.dataFolder.resolve("data").resolve("railways")
            if (!folder.exists()) {
                folder.mkdirs()
                return@withContext Either.Left(DataSearchError.NOT_FOUND)
            }
            val file = folder.resolve("${railwayId.value}.json")
            if (!file.exists()) {
                return@withContext Either.Left(DataSearchError.NOT_FOUND)
            }
            return@withContext try {
                Either.Right(json.decodeFromString<RailwayData>(file.readText()))
            } catch (e: Exception) {
                plugin.logger.warning("Failed to decode railway data '${file.name}': ${e.message}")
                Either.Left(DataSearchError.DESERIALIZATION_FAILED)
            }
        }

}