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
import dev.nikomaru.advancerailway.commands.formatMinutes
import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.commands.toHex
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.data.GroupData
import dev.nikomaru.advancerailway.file.data.RailwayData
import dev.nikomaru.advancerailway.file.value.GroupId
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.GroupUtils
import dev.nikomaru.advancerailway.utils.RailwayUtils
import dev.nikomaru.advancerailway.utils.StationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission

/** 路線の閲覧コマンド（`/ar railway info|list`）。全員が実行できる（`advancerailway.railway.view`）。 */
@Command("ar|advancerailway railway")
class RailwayInfoCommand {

    @Command("info <railwayId>")
    @CommandDescription("路線の詳細（駅間・所要時間）を表示します")
    @Permission("advancerailway.railway.view")
    suspend fun info(sender: CommandSender, @Argument("railwayId") railwayId: RailwayId) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "<red>路線が見つかりません。" } ?: return
        val id = data.id.value
        val fromName = resolveStationName(data.fromStation)
        val toName = resolveStationName(data.toStation)
        val groupData = data.group?.let { GroupUtils.getGroupData(it).getOrNull() }

        sender.sendRichMessage(
            "<dark_gray>━━ ${groupMarker(groupData)} <aqua><bold>路線 $id</bold></aqua> <dark_gray>━━"
        )
        if (groupData != null) {
            val hex = groupData.railwayColor.toHex()
            sender.sendRichMessage(
                "<gray>グループ: <color:$hex>${esc(groupData.name)}</color> " +
                    "<dark_gray>(${data.group.value})</dark_gray> " +
                    "<click:suggest_command:'/ar railway set group $id <group>'><dark_gray>[編集]</dark_gray></click>"
            )
        } else {
            sender.sendRichMessage(
                "<gray>グループ: <gray>— " +
                    "<click:suggest_command:'/ar railway set group $id <group>'><dark_gray>[編集]</dark_gray></click>"
            )
        }
        sender.sendRichMessage("<gray>区間: <white>${esc(fromName)}</white> <yellow>→</yellow> <white>${esc(toName)}</white>")
        sender.sendRichMessage("<gray>所要時間: <white>${formatMinutes(data.timeRequired)}</white>")
        sender.sendRichMessage(
            "<gray>種別: <white>${data.lineType}</white> " +
                "<click:suggest_command:'/ar railway set line-type $id <lineType>'><dark_gray>[編集]</dark_gray></click>"
        )
    }

    @Command("list [page]")
    @CommandDescription("登録されている路線の一覧をページ表示します")
    @Permission("advancerailway.railway.view")
    suspend fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val railways = loadAllRailwayData()
        val stationNames = loadStationNames()
        val groups = loadGroupData()
        sender.sendPaginated(
            items = railways,
            page = page,
            header = "<aqua><bold>路線一覧</bold></aqua> <gray>クリックで詳細",
            empty = "<gray>路線が登録されていません。",
            pageCommand = "/ar railway list",
        ) {
            val id = it.id.value
            val fromName = stationNames[it.fromStation] ?: it.fromStation.value
            val toName = stationNames[it.toStation] ?: it.toStation.value
            val marker = it.group?.let { g -> groups[g] }
                ?.let { gd -> "<color:${gd.railwayColor.toHex()}>■</color>" }
                ?: "<gray>■</gray>"
            "$marker <white>$id</white> " +
                "<gray>${esc(fromName)} <yellow>→</yellow> ${esc(toName)}</gray> " +
                "<dark_gray>(${formatMinutes(it.timeRequired)})</dark_gray> " +
                "<click:run_command:/ar railway info $id><dark_gray>[詳細]</dark_gray></click>"
        }
    }

    /** グループ色の四角マーカー。グループ未設定なら灰色。 */
    private fun groupMarker(groupData: GroupData?): String =
        groupData?.let { "<color:${it.railwayColor.toHex()}>■</color>" } ?: "<gray>■</gray>"

    /** 駅 ID を表示名に解決する。解決できなければ ID をそのまま返す。 */
    private suspend fun resolveStationName(id: StationId): String =
        StationUtils.getStationData(id).getOrNull()?.name?.takeIf { it.isNotBlank() } ?: id.value

    /** data/railways/ 配下のすべての路線データを読み込む。 */
    private suspend fun loadAllRailwayData(): List<RailwayData> = withContext(Dispatchers.IO) {
        listIds("railways")
            .mapNotNull { RailwayUtils.getRailwayData(RailwayId(it)).getOrNull() }
            .sortedBy { it.id.value }
    }

    /** 駅 ID → 表示名のマップを 1 回だけ作る。 */
    private suspend fun loadStationNames(): Map<StationId, String> = withContext(Dispatchers.IO) {
        listIds("stations")
            .mapNotNull { StationUtils.getStationData(StationId(it)).getOrNull() }
            .associate { it.stationId to it.name }
    }

    /** グループ ID → GroupData のマップを 1 回だけ作る。 */
    private suspend fun loadGroupData(): Map<GroupId, GroupData> = withContext(Dispatchers.IO) {
        listIds("groups")
            .mapNotNull { GroupUtils.getGroupData(GroupId(it)).getOrNull() }
            .associateBy { it.groupId }
    }

    /** data/{type}/ 配下の JSON ファイル名を、allowlist を満たす ID として列挙する。 */
    private fun listIds(type: String): List<String> =
        DataPaths.of(type).listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { IdValidation.isValid(it) }
            ?: emptyList()
}
