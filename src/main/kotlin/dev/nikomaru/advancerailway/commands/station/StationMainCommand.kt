/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.station

import dev.nikomaru.advancerailway.AdvanceRailway
import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.data.StationData
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.Utils.json
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import kotlinx.serialization.decodeFromString
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("ar station", "advancerailway station")
@CommandPermission("advancerailway.command.station.write")
class StationMainCommand: KoinComponent {
    val plugin: AdvanceRailway by inject()

    @Subcommand("add")
    suspend fun add(
        sender: CommandSender, id: String, name: String, @Optional inputPoint: Point3D? = null
    ) { // Add station
        if (sender !is Player && inputPoint == null) {
            sender.sendRichMessage("Error: You must enter the point")
            return
        }
        if (!IdValidation.isValid(id)) {
            sender.sendRichMessage("Error: Invalid station ID \"$id\"")
            return
        }
        val point = inputPoint ?: (sender as Player).location.toPoint3D()
        val world = if (sender is Player) {
            sender.world
        } else {
            Bukkit.getWorld("world") ?: run {
                sender.sendRichMessage("Error: World \"world\" not found")
                return
            }
        }
        val stationId = StationId(id)
        val data = StationData(stationId, name, null, world, point, null)
        data.save()
        sender.sendRichMessage("Station added")
    }

    @Subcommand("remove")
    suspend fun remove(sender: CommandSender, id: StationId) { // Remove station
        val file = DataPaths.stations.resolve("${id.value}.json")
        if (!file.exists()) {
            sender.sendRichMessage("Station not found")
            return
        }
        val dependents = (DataPaths.railways.listFiles() ?: emptyArray()).mapNotNull { railwayFile ->
            runCatching { json.decodeFromString<RailwayData>(railwayFile.readText()) }.getOrNull()
        }.filter { it.fromStation == id || it.toStation == id }.map { it.id.value }
        if (dependents.isNotEmpty()) {
            sender.sendRichMessage(
                "Error: Cannot remove station <yellow>${id.value}</yellow>; " +
                    "referenced by railway(s): ${dependents.joinToString(", ")}"
            )
            return
        }
        file.delete()
        FileLoader.mapDataLoad()
        sender.sendRichMessage("Station removed")
    }

}