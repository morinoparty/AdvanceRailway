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
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.error.DataSearchError
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object StationUtils: KoinComponent {
    val plugin: AdvanceRailway by inject()

    suspend fun nearStation(location: Location): Either<DataSearchError, StationId> = withContext(Dispatchers.IO) {
        val folder = DataPaths.stations
        if (!folder.exists()) {
            folder.mkdirs()
            return@withContext Either.Left(DataSearchError.NOT_FOUND)
        }
        // クリック位置のワールドが不明なら最寄り駅を判定できない。
        val worldName = location.world?.name ?: return@withContext Either.Left(DataSearchError.NOT_FOUND)
        val files = folder.listFiles() ?: return@withContext Either.Left(DataSearchError.NOT_FOUND)
        val stations = files
            .filter { it.isFile && it.extension == "json" && IdValidation.isValid(it.nameWithoutExtension) }
            .mapNotNull { getStationData(StationId(it.nameWithoutExtension)).getOrNull() }
            // 最寄り駅は同一ワールド内でのみ判定する（別ディメンションの駅を誤って選ばないため）。
            // 同一ワールド内では座標が 1:1 のため、ネザー等のスケーリング補正は不要。
            .filter { it.world.name == worldName }

        val nearest = stations.minByOrNull { it.point.distanceTo2D(location.toPoint3D()) }
            ?: return@withContext Either.Left(DataSearchError.NOT_FOUND)
        return@withContext Either.Right(nearest.stationId)
    }

    suspend fun getStationData(stationId: StationId): Either<DataSearchError, StationData> =
        withContext(Dispatchers.IO) {
            val folder = DataPaths.stations
            if (!folder.exists()) {
                folder.mkdirs()
                return@withContext Either.Left(DataSearchError.NOT_FOUND)
            }
            val file = folder.resolve("${stationId.value}.json")
            if (!file.exists()) {
                return@withContext Either.Left(DataSearchError.NOT_FOUND)
            }
            return@withContext try {
                Either.Right(json.decodeFromString<StationData>(file.readText()))
            } catch (e: Exception) {
                plugin.logger.warning("Failed to decode station data '${file.name}': ${e.message}")
                Either.Left(DataSearchError.DESERIALIZATION_FAILED)
            }
        }
}