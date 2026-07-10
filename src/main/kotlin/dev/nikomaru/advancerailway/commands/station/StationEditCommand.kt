/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.station

import dev.nikomaru.advancerailway.Point3D
import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.StationUtils
import dev.nikomaru.advancerailway.utils.Utils.toPoint3D
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.awt.Color

@Command("ar station", "advancerailway station")
@CommandPermission("advancerailway.command.station.write")
class StationEditCommand {
    @Subcommand("set name")
    suspend fun setName(sender: CommandSender, stationId: StationId, newName: String) {
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "Station not found" } ?: return
        data.copy(name = newName).save()
        sender.sendRichMessage("Station name set")
    }

    @Subcommand("set location")
    suspend fun setLocation(sender: CommandSender, stationId: StationId, inputPoint: Point3D?) {
        if (sender !is Player && inputPoint == null) {
            sender.sendRichMessage("Error: You must enter the point")
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
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "Station not found" } ?: return
        data.copy(point = point, world = world).save()
        sender.sendRichMessage("Station location set")
    }

    @Subcommand("set numbering")
    suspend fun setNumbering(sender: CommandSender, stationId: StationId, numbering: String) {
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "Station not found" } ?: return
        data.copy(numbering = numbering).save()
        sender.sendRichMessage("Station numbering set")
    }

    @Subcommand("set color")
    suspend fun setColor(sender: CommandSender, stationId: StationId, r: Int, g: Int, b: Int) {
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) {
            sender.sendRichMessage("Error: RGB values must each be between 0 and 255")
            return
        }
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "Station not found" } ?: return
        data.copy(color = Color(r, g, b)).save()
        sender.sendRichMessage("Station color set")
    }
}
