/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands.parser

import dev.nikomaru.advancerailway.storage.database.table.GroupTable
import dev.nikomaru.advancerailway.storage.database.table.RailwayTable
import dev.nikomaru.advancerailway.storage.database.table.StationTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.suggestion.BlockingSuggestionProvider
import java.util.UUID

/** 補完・解決に使う 1 件分の情報。 */
data class IdEntry(val id: UUID, val slug: String, val name: String?)

/**
 * 入力トークンと候補一覧の突き合わせ。Bukkit にもデータベースにも依存しないので単体テストできる。
 */
object IdIndex {

    /**
     * 補完候補: 表示名と slug の両方。
     *
     * 表示名だけだと、slug を知っている人が打ち始めても候補が出ない。逆に slug だけだと
     * 何の駅か分からない。[resolve] はどちらでも解決できるので、候補にも両方載せる。
     * 表示名を先に並べるのは、そちらの方が探しやすいため。
     */
    fun suggestions(entries: List<IdEntry>): Set<String> =
        (entries.mapNotNull { it.name } + entries.map { it.slug }).toSet()

    /**
     * 入力を表示名 → slug → UUID の順に解決する。
     * どれにも一致しなければ null（コマンド側でエラーメッセージを出す）。
     */
    fun resolve(entries: List<IdEntry>, token: String): UUID? =
        entries.firstOrNull { it.name == token }?.id
            ?: entries.firstOrNull { it.slug == token }?.id
            ?: entries.firstOrNull { it.id.toString() == token }?.id
}

/**
 * 駅・路線・グループの ID 引数に共通のパーサ。
 *
 * 補完には slug ではなく**表示名**を出す（`fti` のような slug は覚えられない）。解決は
 * 表示名・slug・UUID のいずれでも通る。以前はデータフォルダを走査して署名でキャッシュしていたが、
 * 現在はデータベースへ直接問い合わせる（数百行の SQLite クエリで、補完のたびに引いても十分速い）。
 *
 * parse / suggestions は Cloud の asyncCoordinator・補完スレッド上で呼ばれるため、
 * ブロッキングな JDBC アクセスをそのまま行ってよい。
 */
abstract class IdParser<C, T : Any>(
    private val entriesProvider: () -> List<IdEntry>,
    private val idFactory: (UUID) -> T,
) : ArgumentParser<C, T>, BlockingSuggestionProvider.Strings<C> {

    override fun parse(
        commandContext: CommandContext<C & Any>,
        commandInput: CommandInput,
    ): ArgumentParseResult<T> {
        val token = commandInput.readString()
        val id = IdIndex.resolve(entriesProvider(), token)
            ?: return ArgumentParseResult.failure(IllegalArgumentException("見つかりません: $token"))
        return ArgumentParseResult.success(idFactory(id))
    }

    override fun stringSuggestions(
        commandContext: CommandContext<C>,
        input: CommandInput,
    ): Iterable<String> = IdIndex.suggestions(entriesProvider())
}

/** 各エンティティの補完候補をデータベースから引く。 */
object IdEntries {

    fun stations(): List<IdEntry> = transaction {
        StationTable.selectAll().map {
            IdEntry(it[StationTable.id].value, it[StationTable.slug], it[StationTable.name].ifBlank { null })
        }
    }

    fun groups(): List<IdEntry> = transaction {
        GroupTable.selectAll().map {
            IdEntry(it[GroupTable.id].value, it[GroupTable.slug], it[GroupTable.name].ifBlank { null })
        }
    }

    /** 路線は表示名を持たないため slug のみで解決する。 */
    fun railways(): List<IdEntry> = transaction {
        RailwayTable.selectAll().map { IdEntry(it[RailwayTable.id].value, it[RailwayTable.slug], null) }
    }
}
