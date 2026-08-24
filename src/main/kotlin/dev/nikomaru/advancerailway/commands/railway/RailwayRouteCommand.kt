/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import arrow.core.Either
import dev.nikomaru.advancerailway.commands.nameWithSlugPlain
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.route.RailEdge
import dev.nikomaru.advancerailway.domain.route.RenderedRoute
import dev.nikomaru.advancerailway.domain.route.RouteError
import dev.nikomaru.advancerailway.domain.route.RouteFinder
import dev.nikomaru.advancerailway.domain.route.RouteRenderer
import dev.nikomaru.advancerailway.domain.route.StationNode
import dev.nikomaru.advancerailway.domain.route.TravelMode
import dev.nikomaru.advancerailway.domain.route.Waypoint
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.StationData
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 駅間の最短（所要時間最小）経路を求めて表示するコマンド。
 *
 * - `/ar railway route <from> <to>` — 駅から駅。
 * - `/ar railway route <to>` — プレイヤーの現在地から駅（プレイヤー専用）。
 *
 * 全路線（レール）と、同一ワールド内の徒歩移動を組み合わせた幾何グラフを [RouteFinder] に渡し、
 * A* で最短経路を求める。レールでつながっていない駅どうしや現在地からでも徒歩で到達できる。
 *
 * `--rail-only`（`-r`）フラグを付けると、駅間の徒歩（乗り換え・目的駅への徒歩）を
 * [RouteFinder.RAIL_ONLY_MAX_WALK_SECONDS]（徒歩 20 秒）以内に制限し、できる限り鉄道だけの経路を探す。
 * 現在地から最初の駅までの徒歩は制限しない。
 */
@Command("ar|advancerailway railway")
class RailwayRouteCommand : KoinComponent {

    private val stationRepository: StationRepository by inject()
    private val railwayRepository: RailwayRepository by inject()
    private val groupRepository: GroupRepository by inject()

    /**
     * 経路検索。
     * - `/ar railway route <to>` — プレイヤーの現在地から `to` へ（プレイヤー専用）。
     * - `/ar railway route <from> <to>` — 駅 `from` から駅 `to` へ。
     *
     * 引数が 1 つのときは末尾省略として現在地を起点にする（[second] が null）。
     * 2 つのときは `first` を出発駅、[second] を到着駅として扱う。
     * Cloud では任意引数 `[second]` を末尾に置くことで両形式を 1 メソッドで受ける。
     *
     * `--rail-only` は presence フラグ。省略可能引数の後ろでも解析できるよう、
     * マネージャ側で [org.incendo.cloud.setting.ManagerSetting.LIBERAL_FLAG_PARSING] を有効にしている。
     */
    @Command("route <first> [second]")
    @CommandDescription("2 駅間（または現在地から）の最短経路を表示します")
    @Permission("advancerailway.railway.route")
    suspend fun route(
        sender: CommandSender,
        @Argument("first") first: StationId,
        @Argument("second") second: StationId?,
        @Flag(
            value = "rail-only",
            aliases = ["r"],
            description = "駅間の徒歩を 20 秒以内に制限し、できる限り鉄道だけの経路を探します",
        ) railOnly: Boolean,
    ) {
        val stationData = stationRepository.findAll()
        val stations = stationData.map { it.toNode() }
        // 表示名だけだと同名駅を見分けられず、そのままコマンドに打てるとも限らないので slug も添える。
        val stationNames = stationData.associate { it.id to nameWithSlugPlain(it.name, it.slug) }
        val labels = stationNames
        if (second == null) {
            // route <to>: 現在地から first へ。
            val player = sender as? Player ?: run {
                sender.sendRichMessage("<red>この形式はプレイヤー専用です（現在地を起点にします）。")
                return
            }
            val toNode = stations.find { it.id == first } ?: run {
                sender.sendRichMessage("<red>駅が見つかりません。")
                return
            }
            val origin = Waypoint.Origin(player.location.world.name, player.location.toPoint3D())
            search(sender, stations, stationNames, "現在地", origin, toNode, railOnly)
        } else {
            // route <from> <to>: 駅から駅へ。
            val fromNode = stations.find { it.id == first } ?: run {
                sender.sendRichMessage("<red>駅が見つかりません。")
                return
            }
            val toNode = stations.find { it.id == second } ?: run {
                sender.sendRichMessage("<red>駅が見つかりません。")
                return
            }
            search(sender, stations, stationNames, labels[first] ?: "出発駅", Waypoint.Station(fromNode), toNode, railOnly)
        }
    }

    private suspend fun search(
        sender: CommandSender,
        stations: List<StationNode>,
        stationNames: Map<StationId, String>,
        originLabel: String,
        from: Waypoint,
        to: StationNode,
        railOnly: Boolean,
    ) {
        val railways = railwayRepository.findAll()
            .map { RailEdge(it.id, it.fromStation, it.toStation, it.timeRequired, it.group) }
        val groupNames: Map<GroupId, String> = groupRepository.findAll().associate { it.id to it.name }
        val railwaySlugs = railwayRepository.findAll().associate { it.id to it.slug.value }
        val maxWalkSeconds = if (railOnly) RouteFinder.RAIL_ONLY_MAX_WALK_SECONDS else null
        when (val result = RouteFinder.findRoute(stations, railways, from, to, maxWalkSeconds = maxWalkSeconds)) {
            is Either.Left -> when (result.value) {
                RouteError.SameStation ->
                    sender.sendRichMessage("<red>出発駅と到着駅が同じです。")

                RouteError.NoPath -> {
                    val hint =
                        if (railOnly) "<gray>（--rail-only 指定中: 徒歩 20 秒以内の乗り換えでは到達できません）" else ""
                    sender.sendRichMessage(
                        "<red>${stationNames[to.id] ?: "目的地"} への経路が見つかりませんでした。$hint"
                    )
                }
            }

            is Either.Right -> {
                val rendered = RouteRenderer.render(
                    result.value, originLabel, { stationNames[it] }, { groupNames[it] }
                )
                sendRoute(sender, rendered, railwaySlugs)
            }
        }
    }

    private fun sendRoute(
        sender: CommandSender,
        route: RenderedRoute,
        railwaySlugs: Map<dev.nikomaru.advancerailway.domain.id.RailwayId, String>,
    ) {
        sender.sendRichMessage(
            "<green>経路: <white>${esc(route.fromLabel)}</white> <gray>→</gray> " +
                "<white>${esc(route.toLabel)}</white></green> " +
                "<gray>(合計 ${route.totalMinutes} 分 / ${route.legCount} 区間)"
        )
        // 連続する同一路線の区間を 1 行にまとめて表示する。
        RouteRenderer.groupLegs(route.legs).forEach { seg ->
            val via = when (seg.mode) {
                TravelMode.RAIL -> {
                    val line = seg.lineLabel?.let { "<aqua>[${esc(it)}]</aqua>" } ?: "<gray>[路線]</gray>"
                    // まとめ行（複数区間）は路線をまたぐため [詳細] を付けない。単一区間のみリンクを残す。
                    val info = seg.railwayId
                        ?.let { railwaySlugs[it] }
                        ?.let { " <click:run_command:/ar railway info $it><dark_gray>[詳細]</dark_gray></click>" }
                        ?: ""
                    "$line$info"
                }

                TravelMode.WALK -> "<gray>徒歩</gray>"
            }
            // 複数区間をまとめた行だけ区間数を併記する。
            val time = if (seg.legCount > 1) "${seg.legCount}区間 / ${seg.minutes} 分" else "${seg.minutes} 分"
            sender.sendRichMessage(
                "<dark_gray>${seg.index}.</dark_gray> <white>${esc(seg.fromLabel)}</white> " +
                    "<yellow>→</yellow> <white>${esc(seg.toLabel)}</white> $via <gray>($time)"
            )
        }
    }

    private fun StationData.toNode(): StationNode = StationNode(id, worldName, point)

    /** MiniMessage のタグ注入を防ぐため、ユーザー由来の名前中の `<` をエスケープする。 */
    private fun esc(text: String): String = text.replace("<", "\\<")
}
