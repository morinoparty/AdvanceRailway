/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import arrow.core.Either
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.commands.DataPaths
import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.route.RailEdge
import dev.nikomaru.advancerailway.route.Route
import dev.nikomaru.advancerailway.route.RouteError
import dev.nikomaru.advancerailway.route.RouteFinder
import dev.nikomaru.advancerailway.route.StationNode
import dev.nikomaru.advancerailway.route.TravelMode
import dev.nikomaru.advancerailway.route.Waypoint
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
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.math.ceil

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

    /** `/ar railway route <to>` — 現在地から。 */
    @Subcommand("route")
    suspend fun route(sender: CommandSender, to: StationId) {
        val player = sender as? Player ?: run {
            sender.sendRichMessage("<red>This form requires a player (uses your current location as the origin).")
            return
        }
        val stations = loadAllStations()
        val toNode = stations.find { it.id == to } ?: run {
            sender.sendRichMessage("<red>Station not found: ${to.value}")
            return
        }
        val origin = Waypoint.Origin(player.location.world.name, player.location.toPoint3D())
        search(sender, stations, origin, toNode)
    }

    /** `/ar railway route <from> <to>` — 駅から駅。 */
    @Subcommand("route")
    suspend fun route(sender: CommandSender, from: StationId, to: StationId) {
        StationUtils.getStationData(from).getOrSend(sender) { "Station not found: ${from.value}" } ?: return
        StationUtils.getStationData(to).getOrSend(sender) { "Station not found: ${to.value}" } ?: return
        val stations = loadAllStations()
        val fromNode = stations.find { it.id == from } ?: return
        val toNode = stations.find { it.id == to } ?: return
        search(sender, stations, Waypoint.Station(fromNode), toNode)
    }

    private suspend fun search(
        sender: CommandSender,
        stations: List<StationNode>,
        from: Waypoint,
        to: StationNode,
    ) {
        val railways = loadAllRailways()
        when (val result = RouteFinder.findRoute(stations, railways, from, to)) {
            is Either.Left -> when (result.value) {
                RouteError.SameStation ->
                    sender.sendRichMessage("<red>Departure and destination are the same station.")

                RouteError.NoPath ->
                    sender.sendRichMessage("<red>No route found to ${to.id.value}.")
            }

            is Either.Right -> sendRoute(sender, from, result.value)
        }
    }

    private fun sendRoute(sender: CommandSender, from: Waypoint, route: Route) {
        val origin = (from as? Waypoint.Station)?.node?.id?.value ?: "(current location)"
        sender.sendRichMessage(
            "<green>Route: $origin -> ${route.legs.last().to.value} " +
                "<gray>(${formatMinutes(route.totalSeconds)} min, ${route.legs.size} legs)"
        )
        route.legs.forEach { leg ->
            val fromLabel = leg.from?.value ?: "(current location)"
            val via = when (leg.mode) {
                TravelMode.RAIL -> {
                    val group = leg.group?.let { " <gray>[${it.value}]" } ?: ""
                    "<gray>via</gray> <click:run_command:/ar railway info ${leg.railwayId?.value}>" +
                        "${leg.railwayId?.value}</click>$group"
                }

                TravelMode.WALK -> {
                    "<gray>on foot"
                }
            }
            sender.sendRichMessage(
                "<yellow> -> </yellow>$fromLabel → ${leg.to.value} $via <gray>(${formatMinutes(leg.timeSeconds)} min)"
            )
        }
    }

    /** data/stations/ 配下のすべての駅を幾何ノードとして読み込む。 */
    private suspend fun loadAllStations(): List<StationNode> = withContext(Dispatchers.IO) {
        listIds(DataPaths.stations)
            .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
            .map { it.toNode() }
    }

    /** data/railways/ 配下のすべての路線をレール辺として読み込む。 */
    private suspend fun loadAllRailways(): List<RailEdge> = withContext(Dispatchers.IO) {
        listIds(DataPaths.railways)
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .map { RailEdge(it.id, it.fromStation, it.toStation, it.timeRequired, it.group) }
    }

    private fun listIds(folder: java.io.File): List<String> =
        folder.listFiles()?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { IdValidation.isValid(it) }
            ?: emptyList()

    private fun StationData.toNode(): StationNode = StationNode(stationId, world.name, point)

    /** 秒を「小数第 1 位までの分」へ丸める（[RailwayInfoCommand] と同じ表記）。 */
    private fun formatMinutes(seconds: Long): Double = ceil(seconds / 6.0) / 10
}
