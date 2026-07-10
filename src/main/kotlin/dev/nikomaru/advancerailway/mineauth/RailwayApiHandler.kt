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
import dev.nikomaru.advancerailway.mineauth.dto.RouteLegDto
import dev.nikomaru.advancerailway.mineauth.dto.RouteResponse
import dev.nikomaru.advancerailway.mineauth.dto.StationDto
import dev.nikomaru.advancerailway.mineauth.dto.StationsResponse
import dev.nikomaru.advancerailway.route.RailEdge
import dev.nikomaru.advancerailway.route.Route
import dev.nikomaru.advancerailway.route.RouteError
import dev.nikomaru.advancerailway.route.RouteFinder
import dev.nikomaru.advancerailway.route.StationNode
import dev.nikomaru.advancerailway.route.Waypoint
import dev.nikomaru.advancerailway.utils.GroupUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.StationUtils
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mineauth.api.CallerType
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Path
import party.morino.mineauth.api.annotations.Query
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.io.File

/**
 * MineAuth の HTTP API へ AdvanceRailway の駅・路線・グループデータを公開するハンドラー。
 * `/api/v1/plugins/advancerailway/` 配下に読み取り専用エンドポイントを提供する。
 *
 * 各エンドポイントは [Authenticated] で保護し、`callers = [CallerType.SERVICE]` を指定して
 * 管理者が発行したサービストークンからのみ呼び出せるようにする（プレイヤーのユーザートークンは拒否）。
 * サービストークンは信頼された資格情報として扱われ、パーミッションノードの評価対象外となるため、
 * ここでは permission を指定しない。
 *
 * ハンドラーは [kotlinx.serialization.Serializable] な DTO をそのまま返し、
 * JSON へのシリアライズは MineAuth 側が担う。
 */
class RailwayApiHandler : KoinComponent {
    private val plugin: AdvanceRailway by inject()

    companion object {
        /** limit 未指定時に返す件数の既定値。 */
        const val DEFAULT_LIMIT = 100

        /** limit の上限。過大なリクエストによる全件読み取り・エンコードを防ぐ。 */
        const val MAX_LIMIT = 500
    }

    /**
     * limit/offset のクエリパラメータを健全な範囲に正規化する。
     *
     * - offset: 0 以上（負値・未指定は 0 に丸める）。
     * - limit: 1..[MAX_LIMIT] の範囲。未指定は [DEFAULT_LIMIT]。
     *
     * @return `(skip, take)` のペア。
     */
    private fun paging(limit: Int?, offset: Int?): Pair<Int, Int> {
        val skip = (offset ?: 0).coerceAtLeast(0)
        val take = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return skip to take
    }

    /**
     * すべての駅を取得する（limit/offset でページングする）。
     * GET /stations?limit={n}&offset={n}
     */
    @Get("/stations")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun listStations(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): StationsResponse {
        val (skip, take) = paging(limit, offset)
        val stations = listIds("stations")
            .drop(skip)
            .take(take)
            .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
            .map { it.toDto() }
        return StationsResponse(stations)
    }

    /**
     * 指定した駅を取得する。
     * GET /stations/{id}
     */
    @Get("/stations/{id}")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun getStation(@Path("id") id: String): StationDto {
        if (!IdValidation.isValid(id)) throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id")
        val station = StationUtils.getStationData(StationId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id")
        return station.toDto()
    }

    /**
     * すべての路線を取得する（limit/offset でページングする）。
     * GET /railways?limit={n}&offset={n}
     */
    @Get("/railways")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun listRailways(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): RailwaysResponse {
        val (skip, take) = paging(limit, offset)
        val railways = listIds("railways")
            .drop(skip)
            .take(take)
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .map { it.toDto() }
        return RailwaysResponse(railways)
    }

    /**
     * 指定した路線を取得する。
     * GET /railways/{id}
     */
    @Get("/railways/{id}")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun getRailway(@Path("id") id: String): RailwayDto {
        if (!IdValidation.isValid(id)) throw HttpError(HttpStatus.NOT_FOUND, "Railway not found: $id")
        val railway = RailwayUtils.getRailwayData(RailwayId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Railway not found: $id")
        return railway.toDto()
    }

    /**
     * すべてのグループを取得する（limit/offset でページングする）。
     * GET /groups?limit={n}&offset={n}
     */
    @Get("/groups")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun listGroups(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): GroupsResponse {
        val (skip, take) = paging(limit, offset)
        val groups = listIds("groups")
            .drop(skip)
            .take(take)
            .mapNotNull { GroupUtils.getGroupData(GroupId(it)).getOrNull() }
            .map { it.toDto() }
        return GroupsResponse(groups)
    }

    /**
     * 指定したグループを取得する。
     * GET /groups/{id}
     */
    @Get("/groups/{id}")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun getGroup(@Path("id") id: String): GroupDto {
        if (!IdValidation.isValid(id)) throw HttpError(HttpStatus.NOT_FOUND, "Group not found: $id")
        val group = GroupUtils.getGroupData(GroupId(id)).getOrNull()
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Group not found: $id")
        return group.toDto()
    }

    /**
     * 2 駅間の最短（所要時間最小）経路を求める。
     * GET /route?from={stationId}&to={stationId}
     *
     * 全路線を重み付き無向グラフとみなし、[RouteFinder] で経路を探索する。
     * - 駅が存在しない: 404 `station_not_found`
     * - 出発駅と到着駅が同一: 400 `same_station`
     * - 経路が存在しない: 404 `no_route`
     */
    @Get("/route")
    @Authenticated(callers = [CallerType.SERVICE])
    suspend fun getRoute(
        @Query("from") from: String,
        @Query("to") to: String,
    ): RouteResponse {
        val stations = loadStationNodes()
        val fromNode = requireStation(from, stations)
        val toNode = requireStation(to, stations)
        val railways = listIds("railways")
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .map { RailEdge(it.id, it.fromStation, it.toStation, it.timeRequired, it.group) }
        return when (val result = RouteFinder.findRoute(stations, railways, Waypoint.Station(fromNode), toNode)) {
            is Either.Left -> when (result.value) {
                RouteError.SameStation -> throw HttpError(
                    HttpStatus.BAD_REQUEST, "Departure and destination are the same station", code = "same_station"
                )

                RouteError.NoPath -> throw HttpError(
                    HttpStatus.NOT_FOUND, "No route between $from and $to", code = "no_route"
                )
            }

            is Either.Right -> result.value.toDto(fromNode.id, toNode.id)
        }
    }

    /** 経路探索用に、全駅を幾何ノードとして読み込む。 */
    private suspend fun loadStationNodes(): List<StationNode> = listIds("stations")
        .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
        .map { StationNode(it.stationId, it.world.name, it.point) }

    /**
     * 経路探索用に駅 ID を検証し、読み込み済みノードから解決する。
     * 不正な ID または存在しない駅は 404 `station_not_found` として弾く。
     */
    private fun requireStation(id: String, stations: List<StationNode>): StationNode {
        if (!IdValidation.isValid(id)) {
            throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id", code = "station_not_found")
        }
        return stations.find { it.id.value == id }
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $id", code = "station_not_found")
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
            // 不正なファイル名（allowlist 外）を除外し、value class の init で例外→500 になるのを防ぐ。
            ?.filter { IdValidation.isValid(it) }
            ?.sorted()
            ?: emptyList()
    }

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

    private fun Route.toDto(from: StationId, to: StationId): RouteResponse = RouteResponse(
        from = from.value,
        to = to.value,
        totalTime = totalSeconds,
        stations = stations.map { it.value },
        legs = legs.map {
            RouteLegDto(
                mode = it.mode.name,
                railway = it.railwayId?.value,
                from = it.from?.value,
                to = it.to.value,
                timeRequired = it.timeSeconds,
                group = it.group?.value,
            )
        },
    )
}
