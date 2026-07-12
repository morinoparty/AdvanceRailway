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
import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.commands.toHex
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.utils.GroupUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission

@Command("ar|advancerailway group")
class GroupInfoCommand {

    @Command("info <groupId>")
    @CommandDescription("グループの詳細（ID・表示名・路線カラー）を表示します")
    @Permission("advancerailway.group.view")
    suspend fun info(sender: CommandSender, @Argument("groupId") groupId: GroupId) {
        val data = GroupUtils.getGroupData(groupId).getOrSend(sender) { "<red>グループが見つかりません。" } ?: return
        val id = data.groupId.value
        val hex = data.railwayColor.toHex()
        val members = loadAllRailways().filter { it.group == data.groupId }.map { it.id }

        sender.sendRichMessage(
            "<dark_gray>━━ <color:$hex>■</color> <aqua><bold>${esc(data.name)}</bold></aqua> " +
                "<dark_gray>(<white>$id</white>) ━━"
        )
        sender.sendRichMessage(
            "<gray>路線カラー: <color:$hex>■■■</color> <white>$hex</white> " +
                "<click:suggest_command:'/ar group set color $id <r> <g> <b>'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<gray>グループ名変更: " +
                "<click:suggest_command:'/ar group set name $id <newName>'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage("<gray>所属路線: <white>${members.size} 本</white>")
        if (members.isEmpty()) {
            sender.sendRichMessage("<gray>（なし）")
        } else {
            members.forEach { railwayId ->
                sender.sendRichMessage(
                    "<color:$hex>■</color> <white>${railwayId.value}</white> " +
                        "<click:run_command:/ar railway info ${railwayId.value}><dark_gray>[詳細]</dark_gray></click>"
                )
            }
        }
    }

    @Command("list [page]")
    @CommandDescription("登録されているグループの一覧をページ表示します")
    @Permission("advancerailway.group.view")
    suspend fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val groups = loadAllGroups().sortedBy { it.groupId.value }
        // 全路線を 1 回だけ読み込み、グループ ID → 所属本数のマップを作る。
        val countByGroup = loadAllRailways()
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
            val id = data.groupId.value
            val hex = data.railwayColor.toHex()
            val count = countByGroup[data.groupId] ?: 0
            "<color:$hex>■</color> <white>${esc(data.name)}</white> <dark_gray>($id)</dark_gray> " +
                "<gray>· $count 路線</gray> " +
                "<click:run_command:/ar group info $id><dark_gray>[詳細]</dark_gray></click>"
        }
    }

    /** data/groups/ 配下のすべてのグループデータを読み込む。 */
    private suspend fun loadAllGroups(): List<GroupData> = withContext(Dispatchers.IO) {
        listIds(DataPaths.groups)
            .mapNotNull { GroupUtils.getGroupData(GroupId(it)).getOrNull() }
    }

    /** data/railways/ 配下のすべての路線データを読み込む。 */
    private suspend fun loadAllRailways(): List<RailwayData> = withContext(Dispatchers.IO) {
        listIds(DataPaths.railways)
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
    }

    /** 指定ディレクトリ配下の JSON ファイル名を、allowlist を満たす ID として列挙する。 */
    private fun listIds(dir: java.io.File): List<String> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { IdValidation.isValid(it) }
            ?: emptyList()
}
