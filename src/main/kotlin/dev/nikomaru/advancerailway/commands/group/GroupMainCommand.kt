/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.FileLoader
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.utils.Utils.json
import kotlinx.serialization.decodeFromString
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import java.awt.Color

@Command("ar|advancerailway group")
class GroupMainCommand {

    @Command("add <id> <name>")
    @CommandDescription("グループを新規登録します")
    @Permission("advancerailway.group.manage")
    fun add(sender: CommandSender, @Argument("id") id: String, @Argument("name") name: String) {
        if (!IdValidation.isValid(id)) {
            sender.sendRichMessage("<red>グループ ID が不正です: <white>$id</white>")
            return
        }
        val groupId = GroupId(id)
        val data = GroupData(groupId, name, Color.getHSBColor(Math.random().toFloat(), 1.0f, 1.0f))
        data.save()
        sender.sendRichMessage("<green>グループを追加しました。")
    }

    @Command("remove <id>")
    @CommandDescription("グループを削除します（依存路線があれば拒否します）")
    @Permission("advancerailway.group.manage")
    suspend fun remove(sender: CommandSender, @Argument("id") id: GroupId) {
        val file = DataPaths.groups.resolve("${id.value}.json")
        if (!file.exists()) {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val dependents = (DataPaths.railways.listFiles() ?: emptyArray()).mapNotNull { railwayFile ->
            runCatching { json.decodeFromString<RailwayData>(railwayFile.readText()) }.getOrNull()
        }.filter { it.group == id }.map { it.id.value }
        if (dependents.isNotEmpty()) {
            sender.sendRichMessage(
                "<red>グループ <yellow>${id.value}</yellow> は削除できません。" +
                    "次の路線が参照しています: <white>${dependents.joinToString(", ")}</white>"
            )
            return
        }
        file.delete()
        FileLoader.mapDataLoad()
        sender.sendRichMessage("<green>グループを削除しました。")
    }

}
