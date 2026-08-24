/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.station

import dev.nikomaru.advancerailway.commands.esc
import dev.nikomaru.advancerailway.commands.nameWithSlug
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.commands.toHex
import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.storage.database.repository.GroupRepository
import dev.nikomaru.advancerailway.storage.database.repository.StationRepository
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 駅の閲覧コマンド（`/ar station info|list`）。全員が実行できる（`advancerailway.station.view`）。 */
@Command("ar|advancerailway station")
class StationInfoCommand : KoinComponent {

    private val stationRepository: StationRepository by inject()
    private val groupRepository: GroupRepository by inject()

    @Command("info <stationId>")
    @CommandDescription("駅の詳細（名前・座標・所属路線とナンバリング）を表示します")
    @Permission("advancerailway.station.view")
    suspend fun info(sender: CommandSender, @Argument("stationId") stationId: StationId) {
        val data = stationRepository.findById(stationId) ?: run {
            sender.sendRichMessage("<red>駅が見つかりません")
            return
        }
        val slug = data.slug.value
        val escName = esc(data.name)
        val hex = data.color.toHex()
        sender.sendRichMessage(
            "<dark_gray>━━ <color:$hex>●</color> <aqua><bold>$escName</bold></aqua> <dark_gray>(<white>$slug</white>) ━━"
        )
        sender.sendRichMessage("<gray>ID: <dark_gray>${data.id}</dark_gray>")
        // ナンバリングは駅ではなくグループ（路線）が持つ。所属している路線ごとに番号が決まる。
        val groups = groupRepository.groupsOf(stationId)
        if (groups.isEmpty()) {
            sender.sendRichMessage("<gray>所属路線: <gray>—（/ar group station set で設定します）")
        } else {
            sender.sendRichMessage("<gray>所属路線:")
            groups.forEach { entry ->
                val groupHex = entry.group.railwayColor.toHex()
                val numbering = entry.numbering ?: "—"
                sender.sendRichMessage(
                    "  <color:$groupHex>■</color> ${nameWithSlug(entry.group.name, entry.group.slug)} " +
                        "<gray>· ${entry.position + 1} 番目 · </gray><white>$numbering</white>"
                )
            }
        }
        sender.sendRichMessage(
            "<gray>座標: <white>${data.worldName} / ${data.point}</white> " +
                "<click:suggest_command:'/ar station set location $slug'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<gray>色: <color:$hex>■</color> <white>$hex</white> " +
                "<click:suggest_command:'/ar station set color $slug <r> <g> <b>'><dark_gray>[編集]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<gray>駅名変更: " +
                "<click:suggest_command:'/ar station set name $slug <newName>'><dark_gray>[編集]</dark_gray></click>"
        )
    }

    @Command("list [page]")
    @CommandDescription("登録されている駅の一覧をページ表示します")
    @Permission("advancerailway.station.view")
    suspend fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val stations = stationRepository.findAll().sortedBy { it.name }
        val numberings = groupRepository.allGroupsOfStations()
            .mapValues { (_, groups) -> groups.mapNotNull { it.numbering } }
        sender.sendPaginated(
            items = stations,
            page = page,
            header = "<aqua><bold>駅一覧</bold></aqua> <gray>クリックで詳細",
            empty = "<gray>駅が登録されていません。",
            pageCommand = "/ar station list",
        ) { st ->
            val hex = st.color.toHex()
            val numbering = numberings[st.id]?.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: ""
            "<color:$hex>●</color> <white>${esc(st.name)}</white> <gray>$numbering</gray> " +
                "<dark_gray>(${st.slug.value})</dark_gray> " +
                "<click:run_command:/ar station info ${st.slug.value}><dark_gray>[詳細]</dark_gray></click>"
        }
    }
}
