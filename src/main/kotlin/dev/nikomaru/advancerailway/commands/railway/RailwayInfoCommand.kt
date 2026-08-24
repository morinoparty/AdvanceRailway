/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import dev.nikomaru.advancerailway.commands.esc
import dev.nikomaru.advancerailway.commands.nameWithSlug
import dev.nikomaru.advancerailway.commands.formatCheckedAt
import dev.nikomaru.advancerailway.commands.formatMinutes
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.commands.toHex
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import dev.nikomaru.advancerailway.storage.model.GroupData
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 路線の閲覧コマンド（`/ar railway info|list`）。全員が実行できる（`advancerailway.railway.view`）。 */
@Command("ar|advancerailway railway")
class RailwayInfoCommand : KoinComponent {

    private val railwayRepository: RailwayRepository by inject()
    private val stationRepository: StationRepository by inject()
    private val groupRepository: GroupRepository by inject()

    @Command("info <railwayId>")
    @CommandDescription("路線の詳細（駅間・所要時間・最終確認）を表示します")
    @Permission("advancerailway.railway.view")
    suspend fun info(sender: CommandSender, @Argument("railwayId") railwayId: RailwayId) {
        val data = railwayRepository.findById(railwayId) ?: run {
            sender.sendRichMessage("<red>路線が見つかりません。")
            return
        }
        val slug = data.slug.value
        val fromStation = stationRepository.findById(data.fromStation)
        val toStation = stationRepository.findById(data.toStation)
        val fromLabel = fromStation?.let { nameWithSlug(it.name, it.slug) } ?: "<gray>${data.fromStation}</gray>"
        val toLabel = toStation?.let { nameWithSlug(it.name, it.slug) } ?: "<gray>${data.toStation}</gray>"
        val groupData = data.group?.let { groupRepository.findById(it) }

        sender.sendRichMessage(
            "<dark_gray>━━ ${groupMarker(groupData)} <aqua><bold>路線 $slug</bold></aqua> <dark_gray>━━"
        )
        sender.sendRichMessage("<gray>ID: <dark_gray>${data.id}</dark_gray>")
        if (groupData != null) {
            val hex = groupData.railwayColor.toHex()
            sender.sendRichMessage(
                "<gray>グループ: <color:$hex>${esc(groupData.name)}</color> " +
                    "<dark_gray>(${groupData.slug.value})</dark_gray> " +
                    "<click:suggest_command:'/ar railway set group $slug <group>'><dark_gray>[編集]</dark_gray></click>"
            )
        } else {
            sender.sendRichMessage(
                "<gray>グループ: <gray>— " +
                    "<click:suggest_command:'/ar railway set group $slug <group>'><dark_gray>[編集]</dark_gray></click>"
            )
        }
        sender.sendRichMessage("<gray>区間: $fromLabel <yellow>→</yellow> $toLabel")
        sender.sendRichMessage("<gray>所要時間: <white>${formatMinutes(data.timeRequired)}</white>")
        sender.sendRichMessage(
            "<gray>種別: <white>${data.lineType}</white> " +
                "<click:suggest_command:'/ar railway set line-type $slug <lineType>'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<gray>最終確認: <white>${formatCheckedAt(data.lastCheckedAt)}</white> " +
                (
                    fromStation?.let {
                        "<click:run_command:'/ar railway check --station ${it.slug.value}'>" +
                            "<dark_gray>[検証]</dark_gray></click>"
                    } ?: ""
                    )
        )
    }

    @Command("list [page]")
    @CommandDescription("登録されている路線の一覧をページ表示します")
    @Permission("advancerailway.railway.view")
    suspend fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val railways = railwayRepository.findAll()
        val stationLabels = stationRepository.findAll().associate { it.id to nameWithSlug(it.name, it.slug) }
        val groups = groupRepository.findAll().associateBy { it.id }
        sender.sendPaginated(
            items = railways,
            page = page,
            header = "<aqua><bold>路線一覧</bold></aqua> <gray>クリックで詳細",
            empty = "<gray>路線が登録されていません。",
            pageCommand = "/ar railway list",
        ) {
            val slug = it.slug.value
            val fromLabel = stationLabels[it.fromStation] ?: "<gray>${it.fromStation}</gray>"
            val toLabel = stationLabels[it.toStation] ?: "<gray>${it.toStation}</gray>"
            val marker = it.group?.let { g -> groups[g] }
                ?.let { gd -> "<color:${gd.railwayColor.toHex()}>■</color>" }
                ?: "<gray>■</gray>"
            "$marker <white>$slug</white> " +
                "$fromLabel <yellow>→</yellow> $toLabel " +
                "<dark_gray>(${formatMinutes(it.timeRequired)})</dark_gray> " +
                "<click:run_command:/ar railway info $slug><dark_gray>[詳細]</dark_gray></click>"
        }
    }

    /** グループ色の四角マーカー。グループ未設定なら灰色。 */
    private fun groupMarker(groupData: GroupData?): String =
        groupData?.let { "<color:${it.railwayColor.toHex()}>■</color>" } ?: "<gray>■</gray>"
}
