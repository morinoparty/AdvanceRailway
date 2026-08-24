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
import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.domain.error.toUserMessage
import dev.nikomaru.advancerailway.domain.service.RailwayUtils
import dev.nikomaru.advancerailway.domain.service.RailwayUtils.railEndpointInspect
import dev.nikomaru.advancerailway.domain.service.StationUtils
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.platform.coroutines.minecraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.block.data.Rail
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RailClickEvent: Listener, KoinComponent {
    private val plugin: AdvanceRailway by inject()
    private val stationRepository: StationRepository by inject()

    companion object {
        /**
         * `/ar inspect` を実行して次のクリックを待っているプレイヤーと、その要求。
         * 非スレッドセーフな [HashMap] なので、出し入れはメインスレッドでのみ行う。
         */
        val detect = HashMap<UUID, InspectRequest>()
    }

    @EventHandler
    suspend fun onRailClick(event: PlayerInteractEvent) {
        val player = event.player
        val request = detect.remove(player.uniqueId) ?: return
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
            // 例外をそのまま伝播させると、プレイヤーには「探索しています…」だけが残って
            // 何が起きたのか分からない（原因はコンソールのログにしか出ない）。ここで拾って伝える。
            try {
                when (val result = railEndpointInspect(startPoint, player.world, request.flagPrefix)) {
                    is Either.Right -> {
                        val (loopFree, excluded) = InspectMessage.partitionForDisplay(result.value)
                        // 終点駅で絞る場合は、各終端の最寄り駅を 1 度だけ引いて突き合わせる。
                        // forward.end は終端そのものなので通常は非 null だが、念のため落とさず素通しする。
                        val withStations = loopFree.map { endpoint ->
                            val end = endpoint.forward.end
                            endpoint to end?.let { StationUtils.nearStation(it.toLocation(player.world)) }
                        }
                        val (endpoints, filtered) = InspectMessage.filterByDestination(withStations, request.destination)
                        player.sendRichMessage("<green>${endpoints.size} 件の終端を検出しました。")
                        InspectMessage.excludedNote(excluded)?.let { player.sendRichMessage(it) }
                        InspectMessage.destinationNote(filtered, request.destination?.let { id ->
                            stationRepository.findById(id)?.let { s -> s.name to s.slug }
                        })?.let { player.sendRichMessage(it) }
                        endpoints.forEach { endpoint ->
                            sendEndpoint(player, endpoint)
                        }
                    }

                    is Either.Left -> {
                        player.sendRichMessage(result.value.toUserMessage())
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("inspect failed for ${player.name}: ${e.message}")
                player.sendRichMessage("<red>解析に失敗しました: <white>${e.message}</white>")
            }
        }
    }

    /**
     * 終端 1 件を forward（クリック点→終端）・backward（終端→クリック点）の 2 行で表示する。
     * flags の先頭は出発方角で、そのまま railway add の flags 引数に使える。
     * backward の分岐フラグは逆走時の分岐の現れ方が異なり算出していないため、
     * 途中分岐なし（flags が出発方角のみ）の場合のみ [作成] を出す。
     *
     * 行の組み立て自体は [InspectMessage] が持つ（レールを敷かずにテストできるようにするため）。
     */
    private suspend fun sendEndpoint(player: Player, endpoint: BranchEndpoint) {
        val label = InspectMessage.label(endpoint)
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
            val line = InspectMessage.endpointLine(
                label = label,
                startStation = StationUtils.nearStation(start.toLocation(player.world)),
                endStation = StationUtils.nearStation(end.toLocation(player.world)),
                start = start,
                end = end,
                flags = flags,
            ) ?: return@forEach
            player.sendRichMessage(line)
        }
    }
}
