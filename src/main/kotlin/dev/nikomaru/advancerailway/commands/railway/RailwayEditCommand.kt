/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.file.type.LineType
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.RailwayUtils
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission

@Command("ar|advancerailway railway")
class RailwayEditCommand {

    @Command("set line-type <railwayId> <lineType>")
    @CommandDescription("路線の種別（LineType）を設定します")
    @Permission("advancerailway.railway.manage")
    suspend fun setLineType(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("lineType") lineType: LineType,
    ) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "<red>路線が見つかりません。" } ?: return
        data.copy(lineType = lineType).save()
        sender.sendRichMessage("<green>種別を <white>$lineType</white> に変更しました。")
    }

    @Command("set group <railwayId> <group>")
    @CommandDescription("路線の所属グループを設定します（none で解除）")
    @Permission("advancerailway.railway.manage")
    suspend fun setGroup(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("group") group: String,
    ) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "<red>路線が見つかりません。" } ?: return
        if (group.equals("none", ignoreCase = true)) {
            data.copy(group = null).save()
            sender.sendRichMessage("<green>グループを解除しました。")
            return
        }
        if (!IdValidation.isValid(group)) {
            sender.sendRichMessage("<red>グループ ID が不正です: <white>$group</white>")
            return
        }
        data.copy(group = GroupId(group)).save()
        sender.sendRichMessage("<green>グループを <white>$group</white> に設定しました。")
    }

    @Command("set from-station <railwayId> <stationId>")
    @CommandDescription("路線の始発駅を設定します")
    @Permission("advancerailway.railway.manage")
    suspend fun setFromStation(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("stationId") stationId: StationId,
    ) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "<red>路線が見つかりません。" } ?: return
        data.copy(fromStation = stationId).save()
        sender.sendRichMessage("<green>始点駅を <white>$stationId</white> に変更しました。")
    }

    @Command("set to-station <railwayId> <stationId>")
    @CommandDescription("路線の終着駅を設定します")
    @Permission("advancerailway.railway.manage")
    suspend fun setToStation(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("stationId") stationId: StationId,
    ) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "<red>路線が見つかりません。" } ?: return
        data.copy(toStation = stationId).save()
        sender.sendRichMessage("<green>終点駅を <white>$stationId</white> に変更しました。")
    }
}
