/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.migration

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.storage.serialization.Line3DCodec
import dev.nikomaru.advancerailway.storage.type.LineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Color
import java.io.File
import java.util.logging.Logger

/**
 * 旧 JSON データ（`data/{stations,railways,groups}/<id>.json`）をデータベースへ 1 度だけ取り込む。
 *
 * JSON は永続化フォーマットとしては廃止したが、既に稼働しているサーバーのデータは移す必要がある。
 * これは移行専用の使い捨て経路で、取り込みが済んだら `data/` を退避してもう触らない。
 * 全サーバーの移行が済んだリリース以降は、このファイルごと削除してよい。
 *
 * 取り込みの規則:
 * - 旧 ID はそのまま slug になり、主キーには UUIDv7 を新しく採番する
 * - 色を持たない駅は slug をシードに実体化する（UUID をシードにすると色が変わってしまうため）
 * - 路線は V2 を V3 相当へ機械変換する。V1 は分岐情報が無く再トレースが要るため取り込まずに警告する
 * - 駅ナンバリングは取り込まない（ナンバリングはグループ側の仕組みに変わったため）
 */
class JsonImport(
    private val plugin: AdvanceRailway,
    private val stationRepository: StationRepository,
    private val railwayRepository: RailwayRepository,
    private val groupRepository: GroupRepository,
) {
    private val logger: Logger get() = plugin.logger

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val dataFolder: File get() = plugin.dataFolder.resolve("data")

    /** 取り込み結果の件数。 */
    data class Result(val groups: Int, val stations: Int, val railways: Int, val skippedRailways: List<String>)

    /**
     * データベースが空で、かつ旧データが残っている場合にのみ取り込む。
     *
     * @return 取り込みを行った場合はその結果、行わなかった場合は null。
     */
    suspend fun runIfNeeded(): Result? {
        if (!dataFolder.exists()) return null
        if (stationRepository.count() > 0 || railwayRepository.count() > 0 || groupRepository.count() > 0) return null

        logger.info("Found legacy JSON data; importing into the database.")
        val result = import()
        archiveLegacyFolder()
        logger.info(
            "Imported legacy data: ${result.groups} groups, ${result.stations} stations, ${result.railways} railways."
        )
        if (result.skippedRailways.isNotEmpty()) {
            logger.warning(
                "Skipped ${result.skippedRailways.size} legacy railway(s) in the old V1 format " +
                    "(no branch information; re-create them with /ar railway add): " +
                    result.skippedRailways.joinToString(", ")
            )
        }
        return result
    }

    private suspend fun import(): Result {
        val groupIds = importGroups()
        val stationIds = importStations()
        val (railways, skipped) = importRailways(groupIds, stationIds)
        return Result(groupIds.size, stationIds.size, railways, skipped)
    }

    private suspend fun importGroups(): Map<String, GroupId> {
        val ids = mutableMapOf<String, GroupId>()
        forEachJson("groups") { file, text ->
            val legacy = json.decodeFromString<LegacyGroup>(text)
            val slug = Slug.parse(legacy.groupId) ?: run {
                logger.warning("Skipping group with invalid id '${legacy.groupId}' (${file.name}).")
                return@forEachJson
            }
            val id = GroupId.new()
            groupRepository.insert(
                GroupData(
                    id = id,
                    slug = slug,
                    name = legacy.name,
                    railwayColor = parseColor(legacy.railwayColor),
                    // ナンバリングは移行しない。接頭辞と並び順は移行後に設定し直す。
                    numberingPrefix = null,
                    numberingStart = 1,
                )
            )
            ids[legacy.groupId] = id
        }
        return ids
    }

    private suspend fun importStations(): Map<String, StationId> {
        val ids = mutableMapOf<String, StationId>()
        forEachJson("stations") { file, text ->
            val legacy = json.decodeFromString<LegacyStation>(text)
            val slug = Slug.parse(legacy.stationId) ?: run {
                logger.warning("Skipping station with invalid id '${legacy.stationId}' (${file.name}).")
                return@forEachJson
            }
            val id = StationId.new()
            stationRepository.insert(
                StationData(
                    id = id,
                    slug = slug,
                    name = legacy.name,
                    worldName = legacy.world,
                    point = parsePoint(legacy.point),
                    overrideSize = legacy.overrideSize,
                    // 色未指定の駅は slug をシードに実体化する（従来の見た目を保つ）。
                    color = legacy.color?.let { parseColor(it) } ?: StationData.defaultColor(slug),
                )
            )
            ids[legacy.stationId] = id
        }
        return ids
    }

    private suspend fun importRailways(
        groupIds: Map<String, GroupId>,
        stationIds: Map<String, StationId>,
    ): Pair<Int, List<String>> {
        var imported = 0
        val skipped = mutableListOf<String>()
        forEachJson("railways") { file, text ->
            val legacy = json.decodeFromString<LegacyRailway>(text)
            val slug = Slug.parse(legacy.id) ?: run {
                logger.warning("Skipping railway with invalid id '${legacy.id}' (${file.name}).")
                return@forEachJson
            }
            val flags = legacyFlags(legacy) ?: run {
                skipped += legacy.id
                return@forEachJson
            }
            val from = stationIds[legacy.fromStation]
            val to = stationIds[legacy.toStation]
            if (from == null || to == null) {
                logger.warning(
                    "Skipping railway '${legacy.id}': references unknown station(s) " +
                        "from=${legacy.fromStation}, to=${legacy.toStation}."
                )
                return@forEachJson
            }
            railwayRepository.insert(
                RailwayData(
                    id = RailwayId.new(),
                    slug = slug,
                    group = legacy.group?.let { groupIds[it] },
                    worldName = legacy.world,
                    lineType = runCatching { LineType.valueOf(legacy.lineType) }.getOrDefault(LineType.UP_DOWN_LINE),
                    line = Line3DCodec.decode(legacy.line),
                    fromStation = from,
                    toStation = to,
                    timeRequired = legacy.timeRequired,
                    startPoint = parsePoint(legacy.startPoint),
                    endPoint = parsePoint(legacy.endPoint),
                    flags = flags,
                )
            )
            imported++
        }
        return imported to skipped
    }

    /**
     * 旧形式の路線から現行の flags 文字列を導く。
     *
     * V3 はそのまま、V2 は directionPoint から出発方角を求めて先頭に足す。
     * V1（flags を持たない）は再トレースにワールドのブロック読みが必要なので取り込まず null を返す。
     */
    private fun legacyFlags(legacy: LegacyRailway): String? {
        val element = legacy.flags
        if (element == null) return null
        val asString = runCatching { element.jsonPrimitive.content }.getOrNull()
        if (asString != null) return asString.takeIf { it.isNotEmpty() }

        val labels = runCatching { element.jsonArray.map { it.jsonPrimitive.content } }.getOrNull() ?: return null
        val directionPoint = legacy.directionPoint ?: return null
        val departure = BranchDirection.fromPoints(parsePoint(legacy.startPoint), parsePoint(directionPoint))
            ?: return null
        val rest = labels.mapNotNull { name -> BranchDirection.entries.find { it.name == name }?.label }
        return departure.label + rest.joinToString("")
    }

    /** `data/<type>/` 配下の JSON を 1 件ずつ読む。壊れたファイルは警告してスキップする。 */
    private suspend inline fun forEachJson(type: String, action: (File, String) -> Unit) {
        val files = withContext(Dispatchers.IO) {
            dataFolder.resolve(type).listFiles { f: File -> f.isFile && f.extension == "json" }?.sortedBy { it.name }
        } ?: return
        for (file in files) {
            val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() } ?: continue
            try {
                action(file, text)
            } catch (e: Exception) {
                logger.warning("Skipping malformed legacy file '${file.name}': ${e.message}")
            }
        }
    }

    /** 取り込み済みの `data/` を退避し、次回以降の起動で再取り込みされないようにする。 */
    private suspend fun archiveLegacyFolder() = withContext(Dispatchers.IO) {
        val target = plugin.dataFolder.resolve("data-backup-json-${System.currentTimeMillis()}")
        if (!dataFolder.renameTo(target)) {
            logger.warning(
                "Imported legacy data but could not rename '${dataFolder.name}' to '${target.name}'. " +
                    "Move or delete it manually so it is not imported again."
            )
        } else {
            logger.info("Legacy JSON data archived as '${target.name}'.")
        }
    }

    private fun parsePoint(raw: String): Point3D {
        val parts = raw.split(",")
        require(parts.size == 3) { "Invalid point: \"$raw\"" }
        return Point3D(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble())
    }

    private fun parseColor(raw: String): Color {
        val parts = raw.split(",")
        require(parts.size == 3) { "Invalid color: \"$raw\"" }
        return Color(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }
}
