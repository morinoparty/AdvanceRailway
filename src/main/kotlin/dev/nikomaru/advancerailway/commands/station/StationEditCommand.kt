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
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import java.awt.Color

@Command("ar|advancerailway station")
class StationEditCommand {
    @Command("set name <stationId> <newName>")
    @CommandDescription("駅の名前を変更します")
    @Permission("advancerailway.station.manage")
    suspend fun setName(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("newName") newName: String,
    ) {
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "<red>駅が見つかりません。" } ?: return
        data.copy(name = newName).save()
        sender.sendRichMessage("<green>駅名を変更しました。")
    }

    @Command("set location <stationId> [point]")
    @CommandDescription("駅の座標とワールドを設定します")
    @Permission("advancerailway.station.manage")
    suspend fun setLocation(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("point") inputPoint: Point3D?,
    ) {
        if (sender !is Player && inputPoint == null) {
            sender.sendRichMessage("<red>座標を指定してください（プレイヤー以外は必須です）。")
            return
        }
        val point = inputPoint ?: (sender as Player).location.toPoint3D()
        val world = if (sender is Player) {
            sender.world
        } else {
            Bukkit.getWorld("world") ?: run {
                sender.sendRichMessage("<red>ワールド \"world\" が見つかりません。")
                return
            }
        }
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "<red>駅が見つかりません。" } ?: return
        data.copy(point = point, world = world).save()
        sender.sendRichMessage("<green>駅の座標を変更しました。")
    }

    @Command("set numbering <stationId> <numbering>")
    @CommandDescription("駅のナンバリングを設定します")
    @Permission("advancerailway.station.manage")
    suspend fun setNumbering(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("numbering") numbering: String,
    ) {
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "<red>駅が見つかりません。" } ?: return
        data.copy(numbering = numbering).save()
        sender.sendRichMessage("<green>駅ナンバリングを変更しました。")
    }

    @Command("set color <stationId> <r> <g> <b>")
    @CommandDescription("駅のカラー（RGB）を設定します")
    @Permission("advancerailway.station.manage")
    suspend fun setColor(
        sender: CommandSender,
        @Argument("stationId") stationId: StationId,
        @Argument("r") r: Int,
        @Argument("g") g: Int,
        @Argument("b") b: Int,
    ) {
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) {
            sender.sendRichMessage("<red>RGB は各 0〜255 で指定してください。")
            return
        }
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "<red>駅が見つかりません。" } ?: return
        data.copy(color = Color(r, g, b)).save()
        sender.sendRichMessage("<green>駅の色を変更しました。")
    }
}
