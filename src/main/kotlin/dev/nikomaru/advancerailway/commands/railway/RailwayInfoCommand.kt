/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.railway

import dev.nikomaru.advancerailway.commands.getOrSend
import dev.nikomaru.advancerailway.commands.sendPaginated
import dev.nikomaru.advancerailway.file.DataPaths
import dev.nikomaru.advancerailway.file.value.IdValidation
import dev.nikomaru.advancerailway.file.value.RailwayId
import dev.nikomaru.advancerailway.utils.RailwayUtils
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import kotlin.math.ceil

/** 路線の閲覧コマンド（`/ar railway info|list`）。全員が実行できる（`advancerailway.railway.view`）。 */
@Command("ar|advancerailway railway")
class RailwayInfoCommand {

    @Command("info <railwayId>")
    @CommandDescription("路線の詳細（駅間・所要時間）を表示します")
    @Permission("advancerailway.railway.view")
    suspend fun info(sender: CommandSender, @Argument("railwayId") railwayId: RailwayId) {
        val data = RailwayUtils.getRailwayData(railwayId).getOrSend(sender) { "Railway not found" } ?: return
        sender.sendRichMessage("Railway ID: ${data.id.value}")
        sender.sendRichMessage("Railway Stations: ${data.toStation} -> ${data.fromStation}")
        sender.sendRichMessage("Railway Length: ${ceil(data.timeRequired / 6.0) / 10} minutes")
    }

    @Command("list [page]")
    @CommandDescription("登録されている路線の一覧をページ表示します")
    @Permission("advancerailway.railway.view")
    fun list(sender: CommandSender, @Argument("page") @Default("1") page: Int) {
        val list = DataPaths.railways.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { IdValidation.isValid(it) }
            ?.sorted()
            ?: emptyList()
        sender.sendPaginated(
            items = list,
            page = page,
            header = "<yellow>路線一覧 <gray>（クリックで詳細）",
            empty = "<gray>路線が登録されていません。",
            pageCommand = "/ar railway list",
        ) { "<click:run_command:/ar railway info $it><white>$it</white></click>" }
    }
}
