/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.commands.esc
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.commands.toHex
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Command("ar|advancerailway group")
class GroupInfoCommand : KoinComponent {

    private val groupRepository: GroupRepository by inject()
    private val railwayRepository: RailwayRepository by inject()

    @Command("info <groupId>")
    @CommandDescription("グループの詳細（slug・表示名・路線カラー・ナンバリング）を表示します")
    @Permission("advancerailway.group.view")
    suspend fun info(sender: CommandSender, @Argument("groupId") groupId: GroupId) {
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val slug = data.slug.value
        val hex = data.railwayColor.toHex()
        val members = railwayRepository.findByGroup(groupId)
        val stations = groupRepository.stationsOf(groupId)

        sender.sendRichMessage(
            "<dark_gray>━━ <color:$hex>■</color> <aqua><bold>${esc(data.name)}</bold></aqua> " +
                "<dark_gray>(<white>$slug</white>) ━━"
        )
        sender.sendRichMessage("<gray>ID: <dark_gray>${data.id}</dark_gray>")
        sender.sendRichMessage(
            "<gray>路線カラー: <color:$hex>■■■</color> <white>$hex</white> " +
                "<click:suggest_command:'/ar group set color $slug <r> <g> <b>'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<gray>ナンバリング: <white>${data.numberingPrefix ?: "—"}</white> " +
                "<gray>(開始 ${data.numberingStart} / ${stations.size} 駅)</gray> " +
                "<click:run_command:'/ar group station list $slug'><dark_gray>[一覧]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<gray>グループ名変更: " +
                "<click:suggest_command:'/ar group set name $slug <newName>'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage("<gray>所属路線: <white>${members.size} 本</white>")
        if (members.isEmpty()) {
            sender.sendRichMessage("<gray>（なし）")
        } else {
            members.forEach { railway ->
                sender.sendRichMessage(
                    "<color:$hex>■</color> <white>${railway.slug.value}</white> " +
                        "<click:run_command:/ar railway info ${railway.slug.value}><dark_gray>[詳細]</dark_gray></click>"
                )
            }
        }
    }

    @Command("list [page]")
    @CommandDescription("登録されているグループの一覧をページ表示します")
    @Permission("advancerailway.group.view")
    suspend fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val groups = groupRepository.findAll()
        // 全路線を 1 回だけ読み込み、グループ ID → 所属本数のマップを作る。
        val countByGroup = railwayRepository.findAll()
            .mapNotNull { it.group }
            .groupingBy { it }
            .eachCount()

        sender.sendPaginated(
            items = groups,
            page = page,
            header = "<aqua><bold>グループ一覧</bold></aqua> <gray>クリックで詳細",
            empty = "<gray>グループが登録されていません。",
            pageCommand = "/ar group list",
        ) { data ->
            val slug = data.slug.value
            val hex = data.railwayColor.toHex()
            val count = countByGroup[data.id] ?: 0
            "<color:$hex>■</color> <white>${esc(data.name)}</white> <dark_gray>($slug)</dark_gray> " +
                "<gray>· $count 路線</gray> " +
                "<click:run_command:/ar group info $slug><dark_gray>[詳細]</dark_gray></click>"
        }
    }
}
