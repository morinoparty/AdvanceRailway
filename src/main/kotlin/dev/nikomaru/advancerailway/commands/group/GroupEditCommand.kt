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
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.Slug
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.platform.map.MapRenderer
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Color

/**
 * グループ（路線）の編集コマンド。
 *
 * 駅ナンバリングはここで設定する。グループが接頭辞（`JY`）と開始番号を持ち、
 * `station set` で決めた駅の並び順から各駅の番号（`JY01`, `JY02` …）が決まる。
 */
@Command("ar|advancerailway group")
class GroupEditCommand : KoinComponent {

    private val groupRepository: GroupRepository by inject()
    private val stationRepository: StationRepository by inject()

    @Command("set name <groupId> <newName>")
    @CommandDescription("グループの名前を設定します")
    @Permission("advancerailway.group.manage")
    suspend fun setName(
        sender: CommandSender,
        @Argument("groupId") groupId: GroupId,
        @Argument("newName") newName: String,
    ) {
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        groupRepository.update(data.copy(name = newName))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>グループ名を変更しました。")
    }

    @Command("set slug <groupId> <newSlug>")
    @CommandDescription("グループの slug（短い識別子）を変更します")
    @Permission("advancerailway.group.manage")
    suspend fun setSlug(
        sender: CommandSender,
        @Argument("groupId") groupId: GroupId,
        @Argument("newSlug") rawSlug: String,
    ) {
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val slug = Slug.parse(rawSlug) ?: run {
            sender.sendRichMessage("<red>slug が不正です: <white>$rawSlug</white>")
            return
        }
        if (groupRepository.slugExists(slug, excluding = groupId)) {
            sender.sendRichMessage("<red>その slug は既に使われています: <white>$rawSlug</white>")
            return
        }
        groupRepository.update(data.copy(slug = slug))
        sender.sendRichMessage("<green>slug を <white>$rawSlug</white> に変更しました。")
    }

    @Command("set color <groupId> <r> <g> <b>")
    @CommandDescription("グループの路線カラーをRGB値で設定します")
    @Permission("advancerailway.group.manage")
    suspend fun setColor(
        sender: CommandSender,
        @Argument("groupId") groupId: GroupId,
        @Argument("r") r: Int,
        @Argument("g") g: Int,
        @Argument("b") b: Int,
    ) {
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) {
            sender.sendRichMessage("<red>RGB は各 0〜255 で指定してください。")
            return
        }
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        groupRepository.update(data.copy(railwayColor = Color(r, g, b)))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>グループの色を変更しました。")
    }

    /** `none` を渡すとナンバリングを無効化する（駅に番号が付かなくなる）。 */
    @Command("set numbering-prefix <groupId> <prefix>")
    @CommandDescription("駅ナンバリングの接頭辞を設定します（none で無効化）")
    @Permission("advancerailway.group.manage")
    suspend fun setNumberingPrefix(
        sender: CommandSender,
        @Argument("groupId") groupId: GroupId,
        @Argument("prefix") prefix: String,
    ) {
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val newPrefix = if (prefix.equals("none", ignoreCase = true)) null else prefix
        groupRepository.update(data.copy(numberingPrefix = newPrefix))
        MapRenderer.refresh()
        if (newPrefix == null) {
            sender.sendRichMessage("<green>ナンバリングを無効化しました。")
        } else {
            sender.sendRichMessage("<green>ナンバリングの接頭辞を <white>$newPrefix</white> にしました。")
        }
    }

    @Command("set numbering-start <groupId> <number>")
    @CommandDescription("駅ナンバリングの開始番号を設定します")
    @Permission("advancerailway.group.manage")
    suspend fun setNumberingStart(
        sender: CommandSender,
        @Argument("groupId") groupId: GroupId,
        @Argument("number") number: Int,
    ) {
        if (number < 0) {
            sender.sendRichMessage("<red>開始番号は 0 以上で指定してください。")
            return
        }
        val data = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        groupRepository.update(data.copy(numberingStart = number))
        MapRenderer.refresh()
        sender.sendRichMessage("<green>ナンバリングの開始番号を <white>$number</white> にしました。")
    }

    /**
     * グループ内の駅の並びを一括で設定する。並びがそのままナンバリングの順番になる。
     * 1 件ずつの挿入ではなく一括置換にしているのは、順番を常に矛盾なく保つため。
     */
    @Command("station set <groupId> <stations>")
    @CommandDescription("グループの駅の並び（ナンバリング順）をまとめて設定します")
    @Permission("advancerailway.group.manage")
    suspend fun setStations(
        sender: CommandSender,
        @Argument("groupId") groupId: GroupId,
        @Argument("stations") @org.incendo.cloud.annotation.specifier.Greedy stations: String,
    ) {
        if (groupRepository.findById(groupId) == null) {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val tokens = stations.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            sender.sendRichMessage("<red>駅を 1 つ以上指定してください（slug をスペース区切りで）。")
            return
        }
        val resolved = mutableListOf<StationId>()
        for (token in tokens) {
            val station = stationRepository.resolve(token) ?: run {
                sender.sendRichMessage("<red>駅が見つかりません: <white>$token</white>")
                return
            }
            if (station.id in resolved) {
                sender.sendRichMessage("<red>同じ駅が複数回指定されています: <white>$token</white>")
                return
            }
            resolved += station.id
        }
        groupRepository.replaceStations(groupId, resolved)
        MapRenderer.refresh()
        sender.sendRichMessage("<green>駅の並びを ${resolved.size} 駅で設定しました。")
        stationList(sender, groupId)
    }

    @Command("station list <groupId>")
    @CommandDescription("グループの駅の並びと算出されたナンバリングを表示します")
    @Permission("advancerailway.group.view")
    suspend fun stationList(sender: CommandSender, @Argument("groupId") groupId: GroupId) {
        val group = groupRepository.findById(groupId) ?: run {
            sender.sendRichMessage("<red>グループが見つかりません。")
            return
        }
        val stations = groupRepository.stationsOf(groupId)
        sender.sendRichMessage("<aqua><bold>${esc(group.name)}</bold></aqua> <gray>の駅（${stations.size} 駅）")
        if (group.numberingPrefix == null) {
            sender.sendRichMessage(
                "<gray>ナンバリング未設定 " +
                    "<click:suggest_command:'/ar group set numbering-prefix ${group.slug.value} '>" +
                    "<dark_gray>[設定]</dark_gray></click>"
            )
        }
        if (stations.isEmpty()) {
            sender.sendRichMessage("<gray>（なし）")
            return
        }
        stations.forEach { entry ->
            sender.sendRichMessage(
                "<dark_gray>${entry.position + 1}.</dark_gray> <white>${entry.numbering ?: "—"}</white> " +
                    "<gray>${esc(entry.station.name)}</gray> <dark_gray>(${entry.station.slug.value})</dark_gray>"
            )
        }
    }
}
