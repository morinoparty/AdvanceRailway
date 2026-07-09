/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.mineauth

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.mineauth.dto.GroupDto
import dev.nikomaru.advancerailway.mineauth.dto.GroupsResponse
import dev.nikomaru.advancerailway.mineauth.dto.RailwayDto
import dev.nikomaru.advancerailway.mineauth.dto.RailwayDtoMapper
import dev.nikomaru.advancerailway.mineauth.dto.RailwaysResponse
import dev.nikomaru.advancerailway.mineauth.dto.StationDto
import dev.nikomaru.advancerailway.mineauth.dto.StationsResponse
import dev.nikomaru.advancerailway.utils.GroupUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.StationUtils
import dev.nikomaru.advancerailway.utils.Utils.json
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mineauth.api.annotations.GetMapping
import party.morino.mineauth.api.annotations.PathParam
import party.morino.mineauth.api.annotations.Permission
import party.morino.mineauth.api.annotations.QueryParams
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.io.File

/**
 * MineAuth の HTTP API へ AdvanceRailway の駅・路線・グループデータを公開するハンドラー。
 * `/api/v1/plugins/advancerailway/` 配下に読み取り専用エンドポイントを提供する。
 *
 * 戻り値は [dev.nikomaru.advancerailway.utils.Utils.json] で JSON へ変換した [TextContent] とする。
 * MineAuth 側はこれを生の JSON としてそのまま書き出すため、プラグイン間で kotlinx.serialization の
 * シリアライザ実体を共有する必要がなくなる。
 */
class RailwayApiHandler : KoinComponent {
    private val plugin: AdvanceRailway by inject()

    companion object {
        /** すべての読み取りエンドポイントに要求する権限ノード。 */
        const val READ_PERMISSION = "advancerailway.mineauth.read"

        /** limit 未指定時に返す件数の既定値。 */
        const val DEFAULT_LIMIT = 100

        /** limit の上限。過大なリクエストによる全件読み取り・エンコードを防ぐ。 */
        const val MAX_LIMIT = 500
    }

    /**
     * limit/offset のクエリパラメータを健全な範囲に正規化する。
     *
     * - offset: 0 以上（負値・不正値は 0 に丸める）。
     * - limit: 1..[MAX_LIMIT] の範囲。未指定・不正値は [DEFAULT_LIMIT]。
     */
    private fun paging(params: Map<String, String>): Pair<Int, Int> {
        val offset = params["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = (params["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return offset to limit
    }

    /**
     * すべての駅を取得する（limit/offset でページングする）。
     * GET /stations?limit={n}&offset={n}
     */
    @Permission(READ_PERMISSION)
    @GetMapping("/stations")
    suspend fun listStations(@QueryParams params: Map<String, String> = emptyMap()): TextContent {
        val (offset, limit) = paging(params)
        val stations = listIds("stations")
            .drop(offset)
            .take(limit)
            .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
            .map { it.toDto() }
        return json(StationsResponse(stations), StationsResponse.serializer())
    }

    /**
     * 指定した駅を取得する。
     * GET /stations/{id}
     */
    @Permission(READ_PERMISSION)
    @GetMapping("/stations/{id}")
    suspend fun getStation(@PathParam("id") id: String): TextContent {
        if (!IdValidation.isValid(id)) throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id")
        val station = StationUtils.getStationData(StationId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id")
        return json(station.toDto(), StationDto.serializer())
    }

    /**
     * すべての路線を取得する（limit/offset でページングする）。
     * GET /railways?limit={n}&offset={n}
     */
    @Permission(READ_PERMISSION)
    @GetMapping("/railways")
    suspend fun listRailways(@QueryParams params: Map<String, String> = emptyMap()): TextContent {
        val (offset, limit) = paging(params)
        val railways = listIds("railways")
            .drop(offset)
            .take(limit)
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .map { it.toDto() }
        return json(RailwaysResponse(railways), RailwaysResponse.serializer())
    }

    /**
     * 指定した路線を取得する。
     * GET /railways/{id}
     */
    @Permission(READ_PERMISSION)
    @GetMapping("/railways/{id}")
    suspend fun getRailway(@PathParam("id") id: String): TextContent {
        if (!IdValidation.isValid(id)) throw HttpError(HttpStatus.NOT_FOUND, "Railway not found: $id")
        val railway = RailwayUtils.getRailwayData(RailwayId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Railway not found: $id")
        return json(railway.toDto(), RailwayDto.serializer())
    }

    /**
     * すべてのグループを取得する（limit/offset でページングする）。
     * GET /groups?limit={n}&offset={n}
     */
    @Permission(READ_PERMISSION)
    @GetMapping("/groups")
    suspend fun listGroups(@QueryParams params: Map<String, String> = emptyMap()): TextContent {
        val (offset, limit) = paging(params)
        val groups = listIds("groups")
            .drop(offset)
            .take(limit)
            .mapNotNull { GroupUtils.getGroupData(GroupId(it)).getOrNull() }
            .map { it.toDto() }
        return json(GroupsResponse(groups), GroupsResponse.serializer())
    }

    /**
     * 指定したグループを取得する。
     * GET /groups/{id}
     */
    @Permission(READ_PERMISSION)
    @GetMapping("/groups/{id}")
    suspend fun getGroup(@PathParam("id") id: String): TextContent {
        if (!IdValidation.isValid(id)) throw HttpError(HttpStatus.NOT_FOUND, "Group not found: $id")
        val group = GroupUtils.getGroupData(GroupId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Group not found: $id")
        return json(group.toDto(), GroupDto.serializer())
    }

    /**
     * data/{type}/ 配下の JSON ファイル名（拡張子なし）を ID として列挙する。
     */
    private suspend fun listIds(type: String): List<String> = withContext(Dispatchers.IO) {
        val folder = plugin.dataFolder.resolve("data").resolve(type)
        if (!folder.exists()) return@withContext emptyList()
        folder.listFiles(File::isFile)
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * DTO を [dev.nikomaru.advancerailway.utils.Utils.json] で JSON 化し、
     * application/json の [TextContent] として返すヘルパー。
     */
    private fun <T> json(value: T, serializer: SerializationStrategy<T>): TextContent =
        TextContent(json.encodeToString(serializer, value), ContentType.Application.Json)

    private fun StationData.toDto(): StationDto = StationDto(
        id = stationId.value,
        name = name,
        numbering = numbering,
        world = world.name,
        point = RailwayDtoMapper.toPointDto(point),
        overrideSize = overrideSize,
        color = RailwayDtoMapper.colorToHex(color),
    )

    private fun RailwayData.toDto(): RailwayDto = RailwayDto(
        id = id.value,
        group = group?.value,
        world = world.name,
        lineType = lineType.name,
        fromStation = fromStation.value,
        toStation = toStation.value,
        timeRequired = timeRequired,
        startPoint = RailwayDtoMapper.toPointDto(startPoint),
        endPoint = RailwayDtoMapper.toPointDto(endPoint),
        directionPoint = RailwayDtoMapper.toPointDto(directionPoint),
    )

    private fun GroupData.toDto(): GroupDto = GroupDto(
        id = groupId.value,
        name = name,
        color = RailwayDtoMapper.colorToHex(railwayColor),
    )
}
