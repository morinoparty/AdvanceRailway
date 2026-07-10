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
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.route.Route
import dev.nikomaru.advancerailway.route.RouteError
import dev.nikomaru.advancerailway.route.RouteFinder
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.StationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.math.ceil

/**
 * 駅間の最短（所要時間最小）経路を求めて表示するコマンド。
 * `/ar railway route <from> <to>`
 *
 * 全路線データを重み付きグラフとして [RouteFinder] に渡し、経路の各区間と合計所要時間を表示する。
 */
@Command("ar railway", "advancerailway railway")
@CommandPermission("advancerailway.command.railway.read")
class RailwayRouteCommand : KoinComponent {
    val plugin: AdvanceRailway by inject()

    @Subcommand("route")
    suspend fun route(sender: CommandSender, from: StationId, to: StationId) {
        // 出発駅・到着駅が実在することを先に確認する（存在しなければ経路探索より前に弾く）。
        StationUtils.getStationData(from).getOrSend(sender) { "Station not found: ${from.value}" } ?: return
        StationUtils.getStationData(to).getOrSend(sender) { "Station not found: ${to.value}" } ?: return

        val edges = RouteFinder.buildEdges(loadAllRailways())
        when (val result = RouteFinder.findRoute(edges, from, to)) {
            is Either.Left -> when (result.value) {
                RouteError.SameStation ->
                    sender.sendRichMessage("<red>Departure and destination are the same station.")

                RouteError.NoPath ->
                    sender.sendRichMessage("<red>No route found between ${from.value} and ${to.value}.")
            }

            is Either.Right -> sendRoute(sender, result.value)
        }
    }

    private fun sendRoute(sender: CommandSender, route: Route) {
        sender.sendRichMessage(
            "<green>Route: ${route.stations.first().value} -> ${route.stations.last().value} " +
                "<gray>(${formatMinutes(route.totalSeconds)} min, ${route.legs.size} legs)"
        )
        route.legs.forEach { leg ->
            val group = leg.group?.let { " <gray>[${it.value}]" } ?: ""
            sender.sendRichMessage(
                "<yellow> -> </yellow>${leg.from.value} → ${leg.to.value} " +
                    "<gray>via</gray> <click:run_command:/ar railway info ${leg.railwayId.value}>" +
                    "${leg.railwayId.value}</click>$group <gray>(${formatMinutes(leg.timeRequired)} min)"
            )
        }
    }

    /** data/railways/ 配下のすべての路線データを読み込む（壊れた/不正名のファイルは黙ってスキップ）。 */
    private suspend fun loadAllRailways(): List<RailwayData> = withContext(Dispatchers.IO) {
        val files = DataPaths.railways.listFiles()?.filter { it.isFile && it.extension == "json" } ?: return@withContext emptyList()
        files.map { it.nameWithoutExtension }
            .filter { IdValidation.isValid(it) }
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
    }

    /** 秒を「小数第 1 位までの分」へ丸める（[RailwayInfoCommand] と同じ表記）。 */
    private fun formatMinutes(seconds: Long): Double = ceil(seconds / 6.0) / 10
}
