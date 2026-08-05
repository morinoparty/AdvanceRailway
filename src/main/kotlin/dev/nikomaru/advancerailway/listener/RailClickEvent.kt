/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.listener

import arrow.core.Either
import dev.nikomaru.advancerailway.domain.geometry.Point3D
import dev.nikomaru.advancerailway.domain.rail.BranchDirection
import dev.nikomaru.advancerailway.domain.rail.BranchEndpoint
import dev.nikomaru.advancerailway.domain.rail.EndpointKind
import dev.nikomaru.advancerailway.domain.error.toUserMessage
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.RailwayUtils.railEndpointInspect
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.platform.coroutines.minecraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.block.data.Rail
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

class RailClickEvent: Listener {
    companion object {
        val detect = arrayListOf<Player>()
    }

    @EventHandler
    suspend fun onRailClick(event: PlayerInteractEvent) {
        val player = event.player
        if (player !in detect) {
            return
        }
        detect.remove(player)
        withContext(Dispatchers.minecraft) {
            val block = event.clickedBlock ?: return@withContext
            val blockState = block.blockData
            if (blockState !is Rail) {
                return@withContext
            }

            player.sendRichMessage("<gray>線路を探索しています...")
            val locate = block.location
            val startPoint = locate.let { Point3D(it.x, it.y, it.z) }
            // 端かどうかの表示のためだけに隣接レールを数える（探索自体は traceAll が全方向へ行う）。
            val adjacentRails = RailwayUtils.detectAdjacentRails(startPoint, player.world)
            if (adjacentRails.count() == 1) {
                player.sendRichMessage("<gray>このレールは線路の端です。")
            } else {
                player.sendRichMessage("<gray>このレールは線路の途中です。")
            }
            when (val result = railEndpointInspect(startPoint, player.world)) {
                is Either.Right -> {
                    player.sendRichMessage("<green>${result.value.size} 件の終端を検出しました。")
                    result.value.forEach { endpoint ->
                        sendEndpoint(player, endpoint)
                    }
                }

                is Either.Left -> {
                    player.sendRichMessage(result.value.toUserMessage())
                }
            }
        }
    }

    /**
     * 終端1件を forward（クリック点→終端）・backward（終端→クリック点）の2行で表示する。
     * flags の先頭は出発方角で、そのまま railway add の flags 引数に使える。
     * 始点と終点の最寄り駅が同じ（自駅に戻ってくる）経路は表示しない。
     * backward の分岐フラグは逆走時の分岐の現れ方が異なり算出していないため、
     * 途中分岐なし（flags が出発方角のみ）の場合のみ [作成] を出す。
     */
    private suspend fun sendEndpoint(player: Player, endpoint: BranchEndpoint) {
        val label = buildString {
            append("<yellow>[flags: ${endpoint.flagString()}]</yellow> ")
            when (endpoint.kind) {
                EndpointKind.STOP_BLOCK -> append("<gold>[停止ブロック]</gold> ")
                EndpointKind.LOOP -> append("<gray>[環状/合流]</gray> ")
                EndpointKind.RAIL_END -> {}
            }
        }
        val backwardFlags = endpoint.backward.let { backward ->
            val start = backward.start
            val direction = backward.direction
            if (endpoint.flags.size == 1 && start != null && direction != null) {
                BranchDirection.fromPoints(start, direction)?.label
            } else {
                null
            }
        }
        listOf(
            endpoint.forward to endpoint.flagString(),
            endpoint.backward to backwardFlags,
        ).forEach { (data, flags) ->
            val start = data.start ?: return@forEach
            val end = data.end ?: return@forEach
            val startStation = StationUtils.nearStation(start.toLocation(player.world)).getOrNull()
            val endStation = StationUtils.nearStation(end.toLocation(player.world)).getOrNull()
            if (startStation != null && startStation == endStation) {
                // 自分の駅に戻ってくる経路は表示しない
                return@forEach
            }
            if (startStation == null || endStation == null) {
                player.sendRichMessage(
                    "$label<white>${start.toPlainString()} -> ${end.toPlainString()}</white> <gray>(付近に駅が登録されていません)"
                )
                return@forEach
            }
            val railwayId = startStation.value + "_" + endStation.value
            val suggestMessage = if (flags != null) {
                " <click:suggest_command:'/ar railway add $railwayId ${start.toPlainString()} ${end.toPlainString()} $flags'><green>[作成]</green></click>"
            } else {
                ""
            }
            player.sendRichMessage(
                "$label<white>${startStation.toData()?.name} : ${start.toPlainString()} -> ${endStation.toData()?.name} : ${end.toPlainString()}</white>$suggestMessage"
            )
        }
    }
}