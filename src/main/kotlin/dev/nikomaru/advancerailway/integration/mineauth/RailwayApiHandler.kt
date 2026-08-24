/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.integration.mineauth

import arrow.core.Either
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.domain.route.RailEdge
import dev.nikomaru.advancerailway.domain.route.Route
import dev.nikomaru.advancerailway.domain.route.RouteError
import dev.nikomaru.advancerailway.domain.route.RouteFinder
import dev.nikomaru.advancerailway.domain.route.StationNode
import dev.nikomaru.advancerailway.domain.route.Waypoint
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.integration.mineauth.dto.CreateGroupRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.CreateRailwayRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.CreateStationRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.DeletedResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.GroupDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.GroupStationDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.GroupStationsResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.GroupsResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.RailwayDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.RailwayDtoMapper
import dev.nikomaru.advancerailway.integration.mineauth.dto.RailwaysResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.ReplaceGroupStationsRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.RouteLegDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.RouteResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.StationDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.StationNumberingDto
import dev.nikomaru.advancerailway.integration.mineauth.dto.StationsResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.StatsResponse
import dev.nikomaru.advancerailway.integration.mineauth.dto.UpdateGroupRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.UpdateRailwayRequest
import dev.nikomaru.advancerailway.integration.mineauth.dto.UpdateStationRequest
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.repository.GroupOfStation
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.GroupData
import dev.nikomaru.advancerailway.storage.model.RailwayData
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.storage.type.LineType
import dev.nikomaru.advancerailway.utils.Utils.toLocation
import org.bukkit.Bukkit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mineauth.api.CallerType
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Body
import party.morino.mineauth.api.annotations.Delete
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Patch
import party.morino.mineauth.api.annotations.Path
import party.morino.mineauth.api.annotations.Post
import party.morino.mineauth.api.annotations.Put
import party.morino.mineauth.api.annotations.Query
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import party.morino.mineauth.api.http.Response
import java.awt.Color
import java.time.Instant

/**
 * MineAuth の HTTP API へ AdvanceRailway の駅・路線・グループを公開するハンドラー。
 * `/api/v1/plugins/advancerailway/` 配下にエンドポイントを提供する。
 *
 * 各エンドポイントは [Authenticated] で保護し、サービストークンと `advancerailway.admin` を持つ
 * プレイヤーのユーザートークンの双方から呼び出せるようにする。
 * サービストークンは管理者が発行する信頼された資格情報として扱われ、パーミッションノードの
 * 評価対象外となる。ユーザートークンは [ADMIN_PERMISSION] を持つ場合のみ通過する
 * （権限判定はオンラインのプレイヤーに対して行われ、オフラインなら 403 `player_offline`）。
 *
 * データはすべてデータベースから読み書きする。`/ar file` によるファイルの入出力は廃止し、
 * 外部からの登録・編集・削除はこの API が担う。
 *
 * パスの `{id}` は **UUID でも slug でも**受け付ける。
 */
class RailwayApiHandler : KoinComponent {

    private val stationRepository: StationRepository by inject()
    private val railwayRepository: RailwayRepository by inject()
    private val groupRepository: GroupRepository by inject()

    companion object {
        /**
         * ユーザートークンでの呼び出しに要求するパーミッションノード。
         * サービストークンは信頼された資格情報のため、この評価対象外となる。
         */
        const val ADMIN_PERMISSION = "advancerailway.admin"

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

    // ---------------------------------------------------------------- 駅（読み取り）

    /**
     * すべての駅を取得する（limit/offset でページングする）。
     * GET /stations?limit={n}&offset={n}
     */
    @Get("/stations")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun listStations(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): StationsResponse {
        val (skip, take) = paging(limit, offset)
        val numberings = groupRepository.allGroupsOfStations()
        val stations = stationRepository.findAll().drop(skip).take(take)
            .map { it.toDto(numberings[it.id].orEmpty()) }
        return StationsResponse(stations)
    }

    /**
     * 指定した駅を取得する。
     * GET /stations/{id}
     */
    @Get("/stations/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getStation(@Path("id") id: String): StationDto {
        val station = requireStation(id)
        return station.toDto(groupRepository.groupsOf(station.id))
    }

    /**
     * 指定した駅に接続する路線一覧を取得する（from/to のいずれかがその駅）。
     * GET /stations/{id}/railways
     */
    @Get("/stations/{id}/railways")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun stationRailways(@Path("id") id: String): RailwaysResponse {
        val station = requireStation(id)
        return railwaysResponse(railwayRepository.findByStation(station.id))
    }

    /**
     * 指定座標に最も近い駅を取得する（同一ワールド内、水平距離）。
     * GET /nearest-station?world={world}&x={x}&z={z}
     * - 同一ワールドに駅が 1 つも無い場合は 404 `no_station`。
     */
    @Get("/nearest-station")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun nearestStation(
        @Query("world") world: String,
        @Query("x") x: Double,
        @Query("z") z: Double,
    ): StationDto {
        val target = Point3D(x, 0.0, z)
        val nearest = stationRepository.findByWorld(world).minByOrNull { it.point.distanceTo2D(target) }
            ?: throw HttpError(HttpStatus.NOT_FOUND, "No station in world: $world", code = "no_station")
        return nearest.toDto(groupRepository.groupsOf(nearest.id))
    }

    // ---------------------------------------------------------------- 駅（書き込み）

    /**
     * 駅を新規作成する。
     * POST /stations
     * - slug が不正: 422 `invalid_slug`
     * - slug が使用済み: 409 `slug_conflict`
     */
    @Post("/stations")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun createStation(@Body request: CreateStationRequest): Response<StationDto> {
        val slug = requireSlug(request.slug)
        requireStationSlugFree(slug, null)
        val data = StationData(
            id = StationId.new(),
            slug = slug,
            name = request.name,
            worldName = request.world,
            point = request.point.toPoint3D(),
            overrideSize = request.overrideSize,
            color = request.color?.let { parseColor(it) } ?: StationData.defaultColor(slug),
        )
        stationRepository.insert(data)
        MapRenderer.refresh()
        return Response.of(data.toDto(emptyList()), status = HttpStatus.CREATED)
    }

    /**
     * 駅を部分更新する。指定しなかったフィールドは変更しない。
     * PATCH /stations/{id}
     *
     * `overrideSize` を消したい場合は `"unset": ["overrideSize"]` を指定する
     * （JSON では「キーの欠落」と「明示的な null」を区別できないため）。
     */
    @Patch("/stations/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun updateStation(@Path("id") id: String, @Body request: UpdateStationRequest): StationDto {
        val current = requireStation(id)
        val slug = request.slug?.let { requireSlug(it) }?.also { requireStationSlugFree(it, current.id) }
        val updated = current.copy(
            slug = slug ?: current.slug,
            name = request.name ?: current.name,
            worldName = request.world ?: current.worldName,
            point = request.point?.toPoint3D() ?: current.point,
            overrideSize = if ("overrideSize" in request.unset) null else request.overrideSize ?: current.overrideSize,
            color = request.color?.let { parseColor(it) } ?: current.color,
        )
        stationRepository.update(updated)
        MapRenderer.refresh()
        return updated.toDto(groupRepository.groupsOf(updated.id))
    }

    /**
     * 駅を削除する。
     * DELETE /stations/{id}
     * - その駅を参照する路線がある場合は 409 `station_in_use`（路線の slug を details に列挙する）。
     */
    @Delete("/stations/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun deleteStation(@Path("id") id: String): DeletedResponse {
        val station = requireStation(id)
        val dependents = railwayRepository.findByStation(station.id)
        if (dependents.isNotEmpty()) {
            throw HttpError(
                HttpStatus.CONFLICT,
                "Station is referenced by ${dependents.size} railway(s)",
                code = "station_in_use",
                details = mapOf("railways" to dependents.joinToString(",") { it.slug.value }),
            )
        }
        stationRepository.delete(station.id)
        MapRenderer.refresh()
        return DeletedResponse(true)
    }

    // ---------------------------------------------------------------- 路線（読み取り）

    /**
     * すべての路線を取得する（limit/offset でページングする）。
     * GET /railways?limit={n}&offset={n}
     */
    @Get("/railways")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun listRailways(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): RailwaysResponse {
        val (skip, take) = paging(limit, offset)
        return railwaysResponse(railwayRepository.findAll().drop(skip).take(take))
    }

    /**
     * 指定した路線を取得する。
     * GET /railways/{id}
     */
    @Get("/railways/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getRailway(@Path("id") id: String): RailwayDto = toRailwayDto(requireRailway(id))

    // ---------------------------------------------------------------- 路線（書き込み）

    /**
     * 路線を新規作成する。始点・終点・flags からサーバー側がレールをトレースして経路を求める。
     * POST /railways
     * - flags が不正: 422 `invalid_flags`
     * - トレース失敗: 422 `trace_failed`
     * - 始点・終点付近に駅が無い: 404 `station_not_found`
     */
    @Post("/railways")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun createRailway(@Body request: CreateRailwayRequest): Response<RailwayDto> {
        val slug = requireSlug(request.slug)
        if (railwayRepository.slugExists(slug)) {
            throw HttpError(HttpStatus.CONFLICT, "Railway slug already in use: ${slug.value}", code = "slug_conflict")
        }
        val world = requireWorld(request.world)
        val startPoint = request.startPoint.toPoint3D()
        val endPoint = request.endPoint.toPoint3D()
        val flags = requireFlags(request.flags)
        // レールのトレースはワールドのブロックを読むため、RailwayUtils 側でメインスレッドへ切り替わる。
        val line = when (val traced = RailwayUtils.getLine(startPoint, endPoint, flags)) {
            is Either.Left -> throw HttpError(
                HttpStatus.UNPROCESSABLE_ENTITY, "Failed to trace rails: ${traced.value}", code = "trace_failed"
            )

            is Either.Right -> traced.value
        }
        val fromStation = resolveEndpointStation(request.fromStation, startPoint, world.name)
        val toStation = resolveEndpointStation(request.toStation, endPoint, world.name)
        val data = RailwayData(
            id = RailwayId.new(),
            slug = slug,
            group = request.group?.let { requireGroup(it).id },
            worldName = world.name,
            lineType = request.lineType?.let { requireLineType(it) } ?: LineType.UP_DOWN_LINE,
            line = line,
            fromStation = fromStation.id,
            toStation = toStation.id,
            timeRequired = line.getLength().toLong() / 8,
            startPoint = startPoint,
            endPoint = endPoint,
            flags = flags.joinToString("") { it.label },
            // 今トレースした経路そのものを保存しているので、この瞬間は確認済みとして扱う。
            lastCheckedAt = Instant.now(),
        )
        railwayRepository.insert(data)
        MapRenderer.refresh()
        return Response.of(toRailwayDto(data), status = HttpStatus.CREATED)
    }

    /**
     * 路線を部分更新する。指定しなかったフィールドは変更しない。
     * PATCH /railways/{id}
     *
     * `startPoint` / `endPoint` / `flags` が揃って指定された場合のみ経路を引き直し、
     * 成功した時点で最終確認時刻を更新する。グループを外したい場合は `"unset": ["group"]`。
     */
    @Patch("/railways/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun updateRailway(@Path("id") id: String, @Body request: UpdateRailwayRequest): RailwayDto {
        val current = requireRailway(id)
        val slug = request.slug?.let { requireSlug(it) }?.also {
            if (railwayRepository.slugExists(it, excluding = current.id)) {
                throw HttpError(HttpStatus.CONFLICT, "Railway slug already in use: ${it.value}", code = "slug_conflict")
            }
        }
        val retrace = request.startPoint != null && request.endPoint != null && request.flags != null
        var line = current.line
        var lastCheckedAt = current.lastCheckedAt
        var flags = current.flags
        var startPoint = current.startPoint
        var endPoint = current.endPoint
        if (retrace) {
            startPoint = request.startPoint!!.toPoint3D()
            endPoint = request.endPoint!!.toPoint3D()
            val parsed = requireFlags(request.flags!!)
            line = when (val traced = RailwayUtils.getLine(startPoint, endPoint, parsed)) {
                is Either.Left -> throw HttpError(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Failed to trace rails: ${traced.value}", code = "trace_failed"
                )

                is Either.Right -> traced.value
            }
            flags = parsed.joinToString("") { it.label }
            lastCheckedAt = Instant.now()
        }
        val updated = current.copy(
            slug = slug ?: current.slug,
            group = when {
                "group" in request.unset -> null
                request.group != null -> requireGroup(request.group).id
                else -> current.group
            },
            lineType = request.lineType?.let { requireLineType(it) } ?: current.lineType,
            line = line,
            fromStation = request.fromStation?.let { requireStation(it).id } ?: current.fromStation,
            toStation = request.toStation?.let { requireStation(it).id } ?: current.toStation,
            timeRequired = request.timeRequired ?: if (retrace) line.getLength().toLong() / 8 else current.timeRequired,
            startPoint = startPoint,
            endPoint = endPoint,
            flags = flags,
            lastCheckedAt = lastCheckedAt,
        )
        railwayRepository.update(updated)
        MapRenderer.refresh()
        return toRailwayDto(updated)
    }

    /**
     * 路線を削除する。
     * DELETE /railways/{id}
     */
    @Delete("/railways/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun deleteRailway(@Path("id") id: String): DeletedResponse {
        val railway = requireRailway(id)
        railwayRepository.delete(railway.id)
        MapRenderer.refresh()
        return DeletedResponse(true)
    }

    // ---------------------------------------------------------------- グループ（読み取り）

    /**
     * すべてのグループを取得する（limit/offset でページングする）。
     * GET /groups?limit={n}&offset={n}
     */
    @Get("/groups")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun listGroups(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): GroupsResponse {
        val (skip, take) = paging(limit, offset)
        return GroupsResponse(groupRepository.findAll().drop(skip).take(take).map { it.toDto() })
    }

    /**
     * 指定したグループを取得する。
     * GET /groups/{id}
     */
    @Get("/groups/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getGroup(@Path("id") id: String): GroupDto = requireGroup(id).toDto()

    /**
     * 指定したグループ（路線）に属する路線一覧を取得する。
     * GET /groups/{id}/railways
     */
    @Get("/groups/{id}/railways")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun groupRailways(@Path("id") id: String): RailwaysResponse =
        railwaysResponse(railwayRepository.findByGroup(requireGroup(id).id))

    /**
     * グループ内の駅を並び順で取得する。駅ナンバリングは並び順から算出した値を返す。
     * GET /groups/{id}/stations
     */
    @Get("/groups/{id}/stations")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun groupStations(@Path("id") id: String): GroupStationsResponse {
        val group = requireGroup(id)
        val numberings = groupRepository.allGroupsOfStations()
        return GroupStationsResponse(
            groupRepository.stationsOf(group.id).map { entry ->
                GroupStationDto(
                    position = entry.position,
                    numbering = entry.numbering,
                    station = entry.station.toDto(numberings[entry.station.id].orEmpty()),
                )
            }
        )
    }

    // ---------------------------------------------------------------- グループ（書き込み）

    /**
     * グループを新規作成する。
     * POST /groups
     */
    @Post("/groups")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<GroupDto> {
        val slug = requireSlug(request.slug)
        if (groupRepository.slugExists(slug)) {
            throw HttpError(HttpStatus.CONFLICT, "Group slug already in use: ${slug.value}", code = "slug_conflict")
        }
        val data = GroupData(
            id = GroupId.new(),
            slug = slug,
            name = request.name,
            railwayColor = request.color?.let { parseColor(it) }
                ?: Color.getHSBColor(Math.random().toFloat(), 1.0f, 1.0f),
            numberingPrefix = request.numberingPrefix,
            numberingStart = request.numberingStart ?: 1,
        )
        groupRepository.insert(data)
        MapRenderer.refresh()
        return Response.of(data.toDto(), status = HttpStatus.CREATED)
    }

    /**
     * グループを部分更新する。指定しなかったフィールドは変更しない。
     * PATCH /groups/{id}
     *
     * ナンバリングを無効化したい場合は `"unset": ["numberingPrefix"]` を指定する。
     */
    @Patch("/groups/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun updateGroup(@Path("id") id: String, @Body request: UpdateGroupRequest): GroupDto {
        val current = requireGroup(id)
        val slug = request.slug?.let { requireSlug(it) }?.also {
            if (groupRepository.slugExists(it, excluding = current.id)) {
                throw HttpError(HttpStatus.CONFLICT, "Group slug already in use: ${it.value}", code = "slug_conflict")
            }
        }
        val updated = current.copy(
            slug = slug ?: current.slug,
            name = request.name ?: current.name,
            railwayColor = request.color?.let { parseColor(it) } ?: current.railwayColor,
            numberingPrefix = if ("numberingPrefix" in request.unset) {
                null
            } else {
                request.numberingPrefix ?: current.numberingPrefix
            },
            numberingStart = request.numberingStart ?: current.numberingStart,
        )
        groupRepository.update(updated)
        MapRenderer.refresh()
        return updated.toDto()
    }

    /**
     * グループ内の駅の並びを一括で置き換える。並びがそのままナンバリング順になる。
     * PUT /groups/{id}/stations
     *
     * 1 件ずつの挿入・移動ではなく一括置換にしているのは、並び順を常に矛盾なく保つため
     * （同じ入力を 2 回適用しても結果が変わらない）。
     * - 同じ駅を複数回指定: 422 `duplicate_station`
     */
    @Put("/groups/{id}/stations")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun replaceGroupStations(
        @Path("id") id: String,
        @Body request: ReplaceGroupStationsRequest,
    ): GroupStationsResponse {
        val group = requireGroup(id)
        val resolved = mutableListOf<StationId>()
        for (token in request.stations) {
            val station = requireStation(token)
            if (station.id in resolved) {
                throw HttpError(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Station listed more than once: $token",
                    code = "duplicate_station",
                )
            }
            resolved += station.id
        }
        groupRepository.replaceStations(group.id, resolved)
        MapRenderer.refresh()
        return groupStations(group.id.toString())
    }

    /**
     * グループを削除する。
     * DELETE /groups/{id}
     * - そのグループを参照する路線がある場合は 409 `group_in_use`。
     */
    @Delete("/groups/{id}")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun deleteGroup(@Path("id") id: String): DeletedResponse {
        val group = requireGroup(id)
        val dependents = railwayRepository.findByGroup(group.id)
        if (dependents.isNotEmpty()) {
            throw HttpError(
                HttpStatus.CONFLICT,
                "Group is referenced by ${dependents.size} railway(s)",
                code = "group_in_use",
                details = mapOf("railways" to dependents.joinToString(",") { it.slug.value }),
            )
        }
        groupRepository.delete(group.id)
        MapRenderer.refresh()
        return DeletedResponse(true)
    }

    // ---------------------------------------------------------------- 経路・統計

    /**
     * 2 駅間の最短（所要時間最小）経路を求める。
     * GET /route?from={station}&to={station}
     *
     * 全路線を重み付き無向グラフとみなし、[RouteFinder] で経路を探索する。
     * - 駅が存在しない: 404 `station_not_found`
     * - 出発駅と到着駅が同一: 400 `same_station`
     * - 経路が存在しない: 404 `no_route`
     */
    @Get("/route")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getRoute(
        @Query("from") from: String,
        @Query("to") to: String,
    ): RouteResponse {
        val stationData = stationRepository.findAll()
        val stations = stationData.map { StationNode(it.id, it.worldName, it.point) }
        val stationNames = stationData.associate { it.id to it.name }
        val fromStation = requireRouteStation(from)
        val toStation = requireRouteStation(to)
        val fromNode = stations.first { it.id == fromStation.id }
        val toNode = stations.first { it.id == toStation.id }
        val railwayData = railwayRepository.findAll()
        val railways = railwayData.map { RailEdge(it.id, it.fromStation, it.toStation, it.timeRequired, it.group) }
        val railwaySlugs = railwayData.associate { it.id to it.slug.value }
        val groupNames = groupRepository.findAll().associate { it.id to it.name }
        return when (val result = RouteFinder.findRoute(stations, railways, Waypoint.Station(fromNode), toNode)) {
            is Either.Left -> when (result.value) {
                RouteError.SameStation -> throw HttpError(
                    HttpStatus.BAD_REQUEST, "Departure and destination are the same station", code = "same_station"
                )

                RouteError.NoPath -> throw HttpError(
                    HttpStatus.NOT_FOUND, "No route between $from and $to", code = "no_route"
                )
            }

            is Either.Right -> result.value.toDto(fromNode.id, toNode.id, stationNames, groupNames, railwaySlugs)
        }
    }

    /**
     * ネットワークの件数サマリを取得する。
     * GET /stats
     */
    @Get("/stats")
    @Authenticated(permission = ADMIN_PERMISSION, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun stats(): StatsResponse =
        StatsResponse(
            stations = stationRepository.count().toInt(),
            railways = railwayRepository.count().toInt(),
            groups = groupRepository.count().toInt(),
        )

    // ---------------------------------------------------------------- 入力の検証・解決

    /** UUID でも slug でも駅を引く。見つからなければ 404。 */
    private suspend fun requireStation(raw: String): StationData =
        stationRepository.resolve(raw)
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Station not found: $raw", code = "station_not_found")

    /** 経路探索の端点。エラーコードを `station_not_found` に統一するため別関数にしている。 */
    private suspend fun requireRouteStation(raw: String): StationData = requireStation(raw)

    private suspend fun requireRailway(raw: String): RailwayData =
        railwayRepository.resolve(raw)
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Railway not found: $raw", code = "railway_not_found")

    private suspend fun requireGroup(raw: String): GroupData =
        groupRepository.resolve(raw)
            ?: throw HttpError(HttpStatus.NOT_FOUND, "Group not found: $raw", code = "group_not_found")

    private fun requireSlug(raw: String): Slug =
        Slug.parse(raw)
            ?: throw HttpError(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid slug: $raw", code = "invalid_slug")

    private suspend fun requireStationSlugFree(slug: Slug, excluding: StationId?) {
        if (stationRepository.slugExists(slug, excluding = excluding)) {
            throw HttpError(HttpStatus.CONFLICT, "Station slug already in use: ${slug.value}", code = "slug_conflict")
        }
    }

    private fun requireFlags(raw: String): List<BranchDirection> =
        BranchDirection.parse(raw)?.takeIf { it.isNotEmpty() }
            ?: throw HttpError(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid flags (expected one or more of N/S/E/W): $raw",
                code = "invalid_flags",
            )

    private fun requireLineType(raw: String): LineType =
        runCatching { LineType.valueOf(raw) }.getOrNull()
            ?: throw HttpError(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid lineType: $raw", code = "invalid_line_type")

    private fun requireWorld(name: String): org.bukkit.World =
        Bukkit.getWorld(name)
            ?: throw HttpError(HttpStatus.UNPROCESSABLE_ENTITY, "World not found: $name", code = "world_not_found")

    /**
     * 路線の端点となる駅を決める。明示指定があればそれを、無ければ座標の最寄り駅を使う
     * （`/ar railway add` と同じ挙動）。
     */
    private suspend fun resolveEndpointStation(raw: String?, point: Point3D, worldName: String): StationData {
        if (raw != null) return requireStation(raw)
        val world = requireWorld(worldName)
        return StationUtils.nearStation(point.toLocation(world))
            ?: throw HttpError(
                HttpStatus.NOT_FOUND,
                "No station near ${point.x},${point.y},${point.z}",
                code = "station_not_found",
            )
    }

    /** `#RRGGBB` を [Color] にする。 */
    private fun parseColor(raw: String): Color =
        runCatching { Color.decode(if (raw.startsWith("#")) raw else "#$raw") }.getOrNull()
            ?: throw HttpError(
                HttpStatus.UNPROCESSABLE_ENTITY, "Invalid color (expected #RRGGBB): $raw", code = "invalid_color"
            )

    // ---------------------------------------------------------------- DTO 変換

    private fun StationData.toDto(groups: List<GroupOfStation>): StationDto = StationDto(
        id = id.toString(),
        slug = slug.value,
        name = name,
        world = worldName,
        point = RailwayDtoMapper.toPointDto(point),
        overrideSize = overrideSize,
        color = RailwayDtoMapper.colorToHex(color),
        numberings = groups.map {
            StationNumberingDto(
                group = it.group.id.toString(),
                groupSlug = it.group.slug.value,
                groupName = it.group.name,
                position = it.position,
                numbering = it.numbering,
            )
        },
    )

    private fun GroupData.toDto(): GroupDto = GroupDto(
        id = id.toString(),
        slug = slug.value,
        name = name,
        color = RailwayDtoMapper.colorToHex(railwayColor),
        numberingPrefix = numberingPrefix,
        numberingStart = numberingStart,
    )

    /** 路線一覧をまとめて DTO 化する。参照先の slug は 1 回だけ引いて使い回す。 */
    private suspend fun railwaysResponse(railways: List<RailwayData>): RailwaysResponse {
        val stationSlugs = stationRepository.findAll().associate { it.id to it.slug.value }
        val groupSlugs = groupRepository.findAll().associate { it.id to it.slug.value }
        return RailwaysResponse(railways.map { it.toDto(stationSlugs, groupSlugs) })
    }

    private suspend fun toRailwayDto(data: RailwayData): RailwayDto = data.toDto(
        stationSlugs = mapOf(
            data.fromStation to (stationRepository.findById(data.fromStation)?.slug?.value ?: ""),
            data.toStation to (stationRepository.findById(data.toStation)?.slug?.value ?: ""),
        ).filterValues { it.isNotEmpty() },
        groupSlugs = data.group?.let { group ->
            groupRepository.findById(group)?.let { mapOf(group to it.slug.value) }
        } ?: emptyMap(),
    )

    private fun RailwayData.toDto(
        stationSlugs: Map<StationId, String>,
        groupSlugs: Map<GroupId, String>,
    ): RailwayDto = RailwayDto(
        id = id.toString(),
        slug = slug.value,
        group = group?.toString(),
        groupSlug = group?.let { groupSlugs[it] },
        world = worldName,
        lineType = lineType.name,
        fromStation = fromStation.toString(),
        fromStationSlug = stationSlugs[fromStation],
        toStation = toStation.toString(),
        toStationSlug = stationSlugs[toStation],
        timeRequired = timeRequired,
        startPoint = RailwayDtoMapper.toPointDto(startPoint),
        endPoint = RailwayDtoMapper.toPointDto(endPoint),
        flags = flags,
        lastCheckedAt = lastCheckedAt?.toString(),
    )

    private fun Route.toDto(
        from: StationId,
        to: StationId,
        stationNames: Map<StationId, String>,
        groupNames: Map<GroupId, String>,
        railwaySlugs: Map<RailwayId, String>,
    ): RouteResponse {
        fun sName(id: StationId): String = stationNames[id]?.takeIf { it.isNotBlank() } ?: id.toString()
        return RouteResponse(
            from = from.toString(),
            fromName = sName(from),
            to = to.toString(),
            toName = sName(to),
            totalTime = totalSeconds,
            stations = stations.map { it.toString() },
            legs = legs.map {
                RouteLegDto(
                    mode = it.mode.name,
                    railway = it.railwayId?.toString(),
                    railwaySlug = it.railwayId?.let { id -> railwaySlugs[id] },
                    from = it.from?.toString(),
                    fromName = it.from?.let { s -> sName(s) },
                    to = it.to.toString(),
                    toName = sName(it.to),
                    timeRequired = it.timeSeconds,
                    group = it.group?.toString(),
                    line = it.group?.let { g -> groupNames[g]?.takeIf { n -> n.isNotBlank() } ?: g.toString() },
                )
            },
        )
    }
}

/** [dev.nikomaru.advancerailway.integration.mineauth.dto.PointDto] を [Point3D] にする。 */
private fun dev.nikomaru.advancerailway.integration.mineauth.dto.PointDto.toPoint3D(): Point3D = Point3D(x, y, z)
