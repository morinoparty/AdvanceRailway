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
import dev.nikomaru.advancerailway.domain.error.toUserMessage
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.rail.RouteCandidate
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.utils.Utils.toLocation
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * `/ar railway explore` — 2点間で考えられる経路（分岐の全組合せ）の調査。
 * flags の長さに制限はなく、到達できる全候補を列挙する。
 */
@Command("ar|advancerailway railway")
class RailwayExploreCommand : KoinComponent {

    private val railwayRepository: RailwayRepository by inject()

    @Command("explore point <startPoint> <endPoint>")
    @CommandDescription("2点間を結ぶ経路の候補を全分岐について調査します")
    @Permission("advancerailway.railway.manage")
    suspend fun explorePoint(
        sender: CommandSender,
        @Argument("startPoint") startPoint: Point3D,
        @Argument("endPoint") endPoint: Point3D,
    ) {
        val world = if (sender is Player) sender.world else Bukkit.getWorlds().first()
        // 近傍駅が両方見つかれば inspect と同じ規則で slug 候補を組んで [作成] を出す
        val fromStation = StationUtils.nearStation(startPoint.toLocation(world))
        val toStation = StationUtils.nearStation(endPoint.toLocation(world))
        val suggestSlug = if (fromStation != null && toStation != null) {
            fromStation.slug.value + "_" + toStation.slug.value
        } else {
            null
        }
        sendRoutes(sender, startPoint, endPoint, world, suggestSlug = suggestSlug, currentFlags = null)
    }

    @Command("explore railway <railwayId>")
    @CommandDescription("登録済み路線の始点・終点間で考えられる経路の候補を調査します")
    @Permission("advancerailway.railway.manage")
    suspend fun exploreRailway(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
    ) {
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        val world = Bukkit.getWorld(data.worldName) ?: run {
            sender.sendRichMessage("<red>ワールドが見つかりません: <white>${data.worldName}</white>")
            return
        }
        sendRoutes(
            sender, data.startPoint, data.endPoint, world,
            suggestSlug = data.slug.value, currentFlags = data.flags,
        )
    }

    /**
     * 候補を短い順に列挙する。[suggestSlug] があれば各候補に add の suggest を付け、
     * [currentFlags]（explore railway のとき）と一致する候補には (現在) マークを付ける。
     */
    private suspend fun sendRoutes(
        sender: CommandSender,
        startPoint: Point3D,
        endPoint: Point3D,
        world: World,
        suggestSlug: String?,
        currentFlags: String?,
    ) {
        sender.sendRichMessage("<gray>経路を調査しています…")
        val routes: List<RouteCandidate> = when (val result = RailwayUtils.findRoutes(startPoint, endPoint, world)) {
            is Either.Left -> {
                sender.sendRichMessage(result.value.toUserMessage())
                return
            }

            is Either.Right -> result.value
        }
        if (routes.isEmpty()) {
            sender.sendRichMessage("<yellow>2点間を結ぶ経路が見つかりませんでした。")
            return
        }
        sender.sendRichMessage(
            "<green>${routes.size} 件の経路が見つかりました: " +
                "<white>${startPoint.toPlainString()} -> ${endPoint.toPlainString()}</white>"
        )
        for (route in routes.sortedBy { it.steps }) {
            val flagString = route.flagString()
            val current = if (flagString == currentFlags) " <yellow>(現在)</yellow>" else ""
            val suggest = if (suggestSlug != null && current.isEmpty()) {
                " <click:suggest_command:'/ar railway add $suggestSlug ${startPoint.toPlainString()} " +
                    "${endPoint.toPlainString()} $flagString'><green>[作成]</green></click>"
            } else {
                ""
            }
            sender.sendRichMessage(
                "<white>flags: $flagString</white> " +
                    "<gray>約 ${route.steps} ブロック / 約 ${route.steps / 8} 秒</gray>$current$suggest"
            )
        }
    }
}
