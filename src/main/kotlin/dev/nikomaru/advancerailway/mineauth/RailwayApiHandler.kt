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

    /**
     * すべての駅を取得する。
     * GET /stations
     */
    @GetMapping("/stations")
    suspend fun listStations(): TextContent {
        val stations = listIds("stations")
            .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
            .map { it.toDto() }
        return json(StationsResponse(stations), StationsResponse.serializer())
    }

    /**
     * 指定した駅を取得する。
     * GET /stations/{id}
     */
    @GetMapping("/stations/{id}")
    suspend fun getStation(@PathParam("id") id: String): TextContent {
        val station = StationUtils.getStationData(StationId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id")
        return json(station.toDto(), StationDto.serializer())
    }

    /**
     * すべての路線を取得する。
     * GET /railways
     */
    @GetMapping("/railways")
    suspend fun listRailways(): TextContent {
        val railways = listIds("railways")
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .map { it.toDto() }
        return json(RailwaysResponse(railways), RailwaysResponse.serializer())
    }

    /**
     * 指定した路線を取得する。
     * GET /railways/{id}
     */
    @GetMapping("/railways/{id}")
    suspend fun getRailway(@PathParam("id") id: String): TextContent {
        val railway = RailwayUtils.getRailwayData(RailwayId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Railway not found: $id")
        return json(railway.toDto(), RailwayDto.serializer())
    }

    /**
     * すべてのグループを取得する。
     * GET /groups
     */
    @GetMapping("/groups")
    suspend fun listGroups(): TextContent {
        val groups = listIds("groups")
            .mapNotNull { GroupUtils.getGroupData(GroupId(it)).getOrNull() }
            .map { it.toDto() }
        return json(GroupsResponse(groups), GroupsResponse.serializer())
    }

    /**
     * 指定したグループを取得する。
     * GET /groups/{id}
     */
    @GetMapping("/groups/{id}")
    suspend fun getGroup(@PathParam("id") id: String): TextContent {
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
