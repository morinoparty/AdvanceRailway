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
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.route.RailEdge
import dev.nikomaru.advancerailway.route.RenderedRoute
import dev.nikomaru.advancerailway.route.RouteError
import dev.nikomaru.advancerailway.route.RouteFinder
import dev.nikomaru.advancerailway.route.RouteRenderer
import dev.nikomaru.advancerailway.route.StationNode
import dev.nikomaru.advancerailway.route.TravelMode
import dev.nikomaru.advancerailway.route.Waypoint
import dev.nikomaru.advancerailway.utils.GroupUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.StationUtils
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * 駅間の最短（所要時間最小）経路を求めて表示するコマンド。
 *
 * - `/ar railway route <from> <to>` — 駅から駅。
 * - `/ar railway route <to>` — プレイヤーの現在地から駅（プレイヤー専用）。
 *
 * 全路線（レール）と、同一ワールド内の徒歩移動を組み合わせた幾何グラフを [RouteFinder] に渡し、
 * A* で最短経路を求める。レールでつながっていない駅どうしや現在地からでも徒歩で到達できる。
 */
@Command("ar railway", "advancerailway railway")
@CommandPermission("advancerailway.command.railway.read")
class RailwayRouteCommand : KoinComponent {
    val plugin: AdvanceRailway by inject()

    /**
     * 経路検索。
     * - `/ar railway route <to>` — プレイヤーの現在地から `to` へ（プレイヤー専用）。
     * - `/ar railway route <from> <to>` — 駅 `from` から駅 `to` へ。
     *
     * 引数が 1 つのときは末尾省略として現在地を起点にする（[second] が null）。
     * 2 つのときは `first` を出発駅、[second] を到着駅として扱う。
     * revxrsal Lamp 3.x は同名サブコマンドのアリティ多重定義を許さないため、
     * 末尾 [Optional] 引数 1 つで両形式を受ける。
     */
    @Subcommand("route")
    suspend fun route(sender: CommandSender, first: StationId, @Optional second: StationId? = null) {
        val stationData = loadAllStationData()
        val stations = stationData.map { it.toNode() }
        val stationNames = stationData.associate { it.stationId to it.name }
        if (second == null) {
            // route <to>: 現在地から first へ。
            val player = sender as? Player ?: run {
                sender.sendRichMessage("<red>この形式はプレイヤー専用です（現在地を起点にします）。")
                return
            }
            val toNode = stations.find { it.id == first } ?: run {
                sender.sendRichMessage("<red>駅が見つかりません: ${first.value}")
                return
            }
            val origin = Waypoint.Origin(player.location.world.name, player.location.toPoint3D())
            search(sender, stations, stationNames, "現在地", origin, toNode)
        } else {
            // route <from> <to>: 駅から駅へ。
            val fromNode = stations.find { it.id == first } ?: run {
                sender.sendRichMessage("<red>駅が見つかりません: ${first.value}")
                return
            }
            val toNode = stations.find { it.id == second } ?: run {
                sender.sendRichMessage("<red>駅が見つかりません: ${second.value}")
                return
            }
            val originLabel = stationNames[first]?.takeIf { it.isNotBlank() } ?: first.value
            search(sender, stations, stationNames, originLabel, Waypoint.Station(fromNode), toNode)
        }
    }

    private suspend fun search(
        sender: CommandSender,
        stations: List<StationNode>,
        stationNames: Map<StationId, String>,
        originLabel: String,
        from: Waypoint,
        to: StationNode,
    ) {
        val railways = loadAllRailways()
        val groupNames = loadGroupNames()
        when (val result = RouteFinder.findRoute(stations, railways, from, to)) {
            is Either.Left -> when (result.value) {
                RouteError.SameStation ->
                    sender.sendRichMessage("<red>出発駅と到着駅が同じです。")

                RouteError.NoPath ->
                    sender.sendRichMessage("<red>${stationNames[to.id] ?: to.id.value} への経路が見つかりませんでした。")
            }

            is Either.Right -> {
                val rendered = RouteRenderer.render(
                    result.value, originLabel, { stationNames[it] }, { groupNames[it] }
                )
                sendRoute(sender, rendered)
            }
        }
    }

    private fun sendRoute(sender: CommandSender, route: RenderedRoute) {
        sender.sendRichMessage(
            "<green>経路: <white>${esc(route.fromLabel)}</white> <gray>→</gray> <white>${esc(route.toLabel)}</white></green> " +
                "<gray>(合計 ${route.totalMinutes} 分 / ${route.legCount} 区間)"
        )
        route.legs.forEach { leg ->
            val via = when (leg.mode) {
                TravelMode.RAIL -> {
                    val line = leg.lineLabel?.let { "<aqua>[${esc(it)}]</aqua>" } ?: "<gray>[路線]</gray>"
                    val info = leg.railwayId
                        ?.let { " <click:run_command:/ar railway info ${it.value}><dark_gray>[詳細]</dark_gray></click>" }
                        ?: ""
                    "$line$info"
                }

                TravelMode.WALK -> "<gray>徒歩</gray>"
            }
            sender.sendRichMessage(
                "<dark_gray>${leg.index}.</dark_gray> <white>${esc(leg.fromLabel)}</white> " +
                    "<yellow>→</yellow> <white>${esc(leg.toLabel)}</white> $via <gray>(${leg.minutes} 分)"
            )
        }
    }

    /** data/stations/ 配下のすべての駅データを読み込む（表示名の解決に使うため StationData のまま保持）。 */
    private suspend fun loadAllStationData(): List<StationData> = withContext(Dispatchers.IO) {
        listIds("stations")
            .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
    }

    /** data/railways/ 配下のすべての路線をレール辺として読み込む。 */
    private suspend fun loadAllRailways(): List<RailEdge> = withContext(Dispatchers.IO) {
        listIds("railways")
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .map { RailEdge(it.id, it.fromStation, it.toStation, it.timeRequired, it.group) }
    }

    /** data/groups/ 配下のグループ ID → 表示名（路線名）のマップを読み込む。 */
    private suspend fun loadGroupNames(): Map<GroupId, String> = withContext(Dispatchers.IO) {
        listIds("groups")
            .mapNotNull { GroupUtils.getGroupData(GroupId(it)).getOrNull() }
            .associate { it.groupId to it.name }
    }

    /** data/{type}/ 配下の JSON ファイル名を、allowlist を満たす ID として列挙する。 */
    private fun listIds(type: String): List<String> =
        plugin.dataFolder.resolve("data").resolve(type).listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { IdValidation.isValid(it) }
            ?: emptyList()

    private fun StationData.toNode(): StationNode = StationNode(stationId, world.name, point)

    /** MiniMessage のタグ注入を防ぐため、ユーザー由来の名前中の `<` をエスケープする。 */
    private fun esc(text: String): String = text.replace("<", "\\<")
}
