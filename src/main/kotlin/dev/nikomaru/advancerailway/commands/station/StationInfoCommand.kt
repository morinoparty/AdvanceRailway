/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.station

import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.StationId
import dev.nikomaru.advancerailway.utils.StationUtils
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission

/** 駅の閲覧コマンド（`/ar station info|list`）。全員が実行できる（`advancerailway.station.view`）。 */
@Command("ar|advancerailway station")
class StationInfoCommand {

    @Command("info <stationId>")
    @CommandDescription("駅の詳細（名前・座標・ナンバリング）を表示します")
    @Permission("advancerailway.station.view")
    suspend fun info(sender: CommandSender, @Argument("stationId") stationId: StationId) {
        val data = StationUtils.getStationData(stationId).getOrSend(sender) { "<red>駅が見つかりません" } ?: return
        val id = data.stationId.value
        sender.sendRichMessage("<gray>=== <aqua>駅: ${data.name}</aqua> <gray>(<white>$id</white>) ===")
        sender.sendRichMessage(
            "<yellow>名前: <white>${data.name} " +
                "<click:suggest_command:'/ar station set name $id <newName>'><dark_gray>[変更]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<yellow>座標: <white>${data.world.name}:${data.point} " +
                "<click:suggest_command:'/ar station set location $id'><dark_gray>[変更]</dark_gray></click>"
        )
        sender.sendRichMessage(
            "<yellow>ナンバリング: <white>${data.numbering} " +
                "<click:suggest_command:'/ar station set numbering $id <numbering>'><dark_gray>[変更]</dark_gray></click>"
        )
    }

    @Command("list [page]")
    @CommandDescription("登録されている駅の一覧をページ表示します")
    @Permission("advancerailway.station.view")
    fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val list = DataPaths.stations.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { IdValidation.isValid(it) }
            ?.sorted()
            ?: emptyList()
        sender.sendPaginated(
            items = list,
            page = page,
            header = "<yellow>駅一覧 <gray>（クリックで詳細）",
            empty = "<gray>駅が登録されていません。",
            pageCommand = "/ar station list",
        ) { "<click:run_command:/ar station info $it><white>$it</white></click>" }
    }
}
