/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.group

import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.RailwayRepository
import dev.nikomaru.advancerailway.storage.model.GroupData
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Color

@Command("ar|advancerailway group")
class GroupMainCommand : KoinComponent {

    private val groupRepository: GroupRepository by inject()
    private val railwayRepository: RailwayRepository by inject()

    @Command("add <slug> <name>")
    @CommandDescription("グループを新規登録します")
    @Permission("advancerailway.group.manage")
    suspend fun add(sender: CommandSender, @Argument("slug") rawSlug: String, @Argument("name") name: String) {
        val slug = Slug.parse(rawSlug) ?: run {
            sender.sendRichMessage("<red>グループ slug が不正です: <white>$rawSlug</white>")
            return
        }
        if (groupRepository.slugExists(slug)) {
            sender.sendRichMessage("<red>その slug は既に使われています: <white>$rawSlug</white>")
            return
        }
        groupRepository.insert(
            GroupData(
                id = GroupId.new(),
                slug = slug,
                name = name,
                railwayColor = Color.getHSBColor(Math.random().toFloat(), 1.0f, 1.0f),
            )
        )
        MapRenderer.refresh()
        sender.sendRichMessage("<green>グループを追加しました。")
    }

    @Command("remove <groupId>")
    @CommandDescription("グループを削除します（依存路線があれば拒否します）")
    @Permission("advancerailway.group.manage")
    suspend fun remove(sender: CommandSender, @Argument("groupId") groupId: GroupId) {
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val dependents = railwayRepository.findByGroup(groupId)
        if (dependents.isNotEmpty()) {
            sender.sendRichMessage(
                "<red>グループ <yellow>${data.slug.value}</yellow> は削除できません。" +
                    "次の路線が参照しています: <white>${dependents.joinToString(", ") { it.slug.value }}</white>"
            )
            return
        }
        groupRepository.delete(groupId)
        MapRenderer.refresh()
        sender.sendRichMessage("<green>グループを削除しました。")
    }
}
