/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.utils.GroupUtils
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
        val data = GroupUtils.getGroupData(groupId).getOrSend(sender) { "Group not found" } ?: return
        sender.sendRichMessage("Group Info: <yellow>${data.groupId.value}")
        sender.sendRichMessage("<yellow>Display Name: <reset>${data.name}")
        sender.sendRichMessage("<yellow>Color: <reset>${data.railwayColor}")
    }

    @Command("list [page]")
    @CommandDescription("登録されているグループの一覧をページ表示します")
    @Permission("advancerailway.group.view")
    fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val list = DataPaths.groups.listFiles()?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
        sender.sendPaginated(items = list, page = page, header = "<yellow>グループ一覧 <gray>（クリックで詳細）",
            empty = "<gray>グループが登録されていません。", pageCommand = "/ar group list") {
            "<click:run_command:/ar group info $it><white>$it</white></click>" }
    }
}
