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
import dev.nikomaru.advancerailway.domain.rail.BranchEndpoint
import dev.nikomaru.advancerailway.domain.rail.EndpointKind
import dev.nikomaru.advancerailway.domain.error.toUserMessage
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.RailwayUtils.railEndpointInspect
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.platform.coroutines.minecraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            // クリック地点の隣接検出も探索と同じロジックを使い、ポイントの切替状態に
            // 依存せず全ての脚から探索を開始する。
            val adjacentRails = RailwayUtils.detectAdjacentRails(startPoint, player.world)
            if (adjacentRails.count() == 1) {
                player.sendRichMessage("<gray>このレールは線路の端です。")
            } else {
                player.sendRichMessage("<gray>このレールは線路の途中です。")
            }
            val res = adjacentRails.map { detectedPlace ->
                async {
                    railEndpointInspect(startPoint, detectedPlace, player.world)
                }
            }.awaitAll()
            res.forEach { result ->
                when (result) {
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
    }

    /**
     * 終端1件を forward（クリック点→終端）・backward（終端→クリック点）の2行で表示する。
     * forward の [作成] には分岐フラグ（例: EE）を railway add の flags 引数として付ける。
     * backward の分岐フラグは逆走時の分岐の現れ方が異なり算出していないため、分岐なしの場合のみ [作成] を出す。
     */
    private suspend fun sendEndpoint(player: Player, endpoint: BranchEndpoint) {
        val label = buildString {
            if (endpoint.flags.isNotEmpty()) {
                append("<yellow>[分岐: ${endpoint.flagString()}]</yellow> ")
            }
            when (endpoint.kind) {
                EndpointKind.STOP_BLOCK -> append("<gold>[停止ブロック]</gold> ")
                EndpointKind.LOOP -> append("<gray>[環状/合流]</gray> ")
                EndpointKind.RAIL_END -> {}
            }
        }
        val flagsArg = endpoint.flagString()
        listOf(
            endpoint.forward to flagsArg,
            endpoint.backward to if (endpoint.flags.isEmpty()) "" else null,
        ).forEach { (data, flags) ->
            val start = data.start ?: return@forEach
            val direction = data.direction ?: return@forEach
            val end = data.end ?: return@forEach
            val startStation = StationUtils.nearStation(start.toLocation(player.world)).getOrNull()
            val endStation = StationUtils.nearStation(end.toLocation(player.world)).getOrNull()
            if (startStation == null || endStation == null) {
                player.sendRichMessage(
                    "$label<white>${start.toPlainString()} -> ${end.toPlainString()}</white> <gray>(付近に駅が登録されていません)"
                )
                return@forEach
            }
            val railwayId = startStation.value + "_" + endStation.value
            val suggestMessage = if (flags != null) {
                val flagsSuffix = if (flags.isEmpty()) "" else " $flags"
                " <click:suggest_command:'/ar railway add $railwayId ${start.toPlainString()} ${direction.toPlainString()} ${end.toPlainString()}$flagsSuffix'><green>[作成]</green></click>"
            } else {
                ""
            }
            player.sendRichMessage(
                "$label<white>${startStation.toData()?.name} : ${start.toPlainString()} -> ${endStation.toData()?.name} : ${end.toPlainString()}</white>$suggestMessage"
            )
        }
    }
}