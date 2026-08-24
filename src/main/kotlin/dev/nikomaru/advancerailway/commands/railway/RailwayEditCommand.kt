/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.type.LineType
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Command("ar|advancerailway railway")
class RailwayEditCommand : KoinComponent {

    private val railwayRepository: RailwayRepository by inject()
    private val groupRepository: GroupRepository by inject()
    private val stationRepository: StationRepository by inject()

    @Command("set line-type <railwayId> <lineType>")
    @CommandDescription("路線の種別（LineType）を設定します")
    @Permission("advancerailway.railway.manage")
    suspend fun setLineType(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("lineType") lineType: LineType,
    ) {
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        railwayRepository.update(data.copy(lineType = lineType))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>種別を <white>$lineType</white> に変更しました。")
    }

    @Command("set slug <railwayId> <newSlug>")
    @CommandDescription("路線の slug（短い識別子）を変更します")
    @Permission("advancerailway.railway.manage")
    suspend fun setSlug(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("newSlug") rawSlug: String,
    ) {
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        val slug = Slug.parse(rawSlug) ?: run {
            sender.sendRichMessage("<red>slug が不正です: <white>$rawSlug</white>")
            return
        }
        if (railwayRepository.slugExists(slug, excluding = railwayId)) {
            sender.sendRichMessage("<red>その slug は既に使われています: <white>$rawSlug</white>")
            return
        }
        railwayRepository.update(data.copy(slug = slug))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>slug を <white>$rawSlug</white> に変更しました。")
    }

    @Command("set group <railwayId> <group>")
    @CommandDescription("路線の所属グループを設定します（none で解除）")
    @Permission("advancerailway.railway.manage")
    suspend fun setGroup(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("group") group: String,
    ) {
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        if (group.equals("none", ignoreCase = true)) {
            railwayRepository.update(data.copy(group = null))
            MapRenderer.refresh()
            sender.sendRichMessage("<green>グループを解除しました。")
            return
        }
        val groupData = groupRepository.resolve(group) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません: <white>$group</white>")
            return
        }
        railwayRepository.update(data.copy(group = groupData.id))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>グループを <white>${groupData.slug.value}</white> に設定しました。")
    }

    @Command("set from-station <railwayId> <stationId>")
    @CommandDescription("路線の始発駅を設定します")
    @Permission("advancerailway.railway.manage")
    suspend fun setFromStation(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("stationId") stationId: StationId,
    ) = setStation(sender, railwayId, stationId, from = true)

    @Command("set to-station <railwayId> <stationId>")
    @CommandDescription("路線の終着駅を設定します")
    @Permission("advancerailway.railway.manage")
    suspend fun setToStation(
        sender: CommandSender,
        @Argument("railwayId") railwayId: RailwayId,
        @Argument("stationId") stationId: StationId,
    ) = setStation(sender, railwayId, stationId, from = false)

    private suspend fun setStation(
        sender: CommandSender,
        railwayId: RailwayId,
        stationId: StationId,
        from: Boolean,
    ) {
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        val station = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません。")
            return
        }
        val updated = if (from) data.copy(fromStation = stationId) else data.copy(toStation = stationId)
        railwayRepository.update(updated)
        MapRenderer.refresh()
        val label = if (from) "始点駅" else "終点駅"
        sender.sendRichMessage("<green>${label}を <white>${station.slug.value}</white> に変更しました。")
    }
}
