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
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils.railEndpointInspect
import dev.nikomaru.advancerailway.utils.StationUtils
import dev.nikomaru.advancerailway.utils.coroutines.minecraft
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

            player.sendRichMessage("Detecting railway...")
            val locate = block.location
            val startPoint = locate.let { Point3D(it.x, it.y, it.z) }
            val availableLocations = RailwayUtils.getRailAvailableDirection(startPoint).map { (x, y, z) ->
                locate.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
            }
            val detectedRailLocations = availableLocations.filter { it.block.blockData is Rail }
            if (detectedRailLocations.count() == 1) {
                player.sendRichMessage("This is a first or last rail.")
            } else {
                player.sendRichMessage("This is a middle rail.")
            }
            val res = detectedRailLocations.map { detectedPlace ->
                async {
                    railEndpointInspect(locate.let { Point3D(it.x, it.y, it.z) },
                                        detectedPlace.let { Point3D(it.x, it.y, it.z) })
                }
            }.awaitAll()
            res.forEach {
                when (it) {
                    is Either.Right -> {
                        player.sendRichMessage("Railway end detected.")
                        val list = it.value.toList()
                        list.forEach { value ->
                            val (start, direction, end) = value
                            val startStation = StationUtils.nearStation(start!!.toLocation(player.world)).getOrNull()
                            val endStation = StationUtils.nearStation(end!!.toLocation(player.world)).getOrNull()
                            if (startStation == null || endStation == null) {
                                player.sendRichMessage("No stations registered yet")
                                return@forEach
                            }
                            val railwayId = startStation.value + "_" + endStation.value
                            val suggestMessage =
                                "<click:suggest_command:'/ar railway add $railwayId ${start.toPlainString()} ${direction!!.toPlainString()} ${end.toPlainString()}'>[create]</click>"
                            player.sendRichMessage(
                                "Railway Data : ${startStation.toData()?.name} : ${start.toPlainString()} -> ${endStation.toData()?.name} : ${end.toPlainString()} $suggestMessage"
                            )
                        }
                    }

                    is Either.Left -> {
                        player.sendRichMessage("Error: ${it.value}")
                    }
                }
            }
        }
    }
}