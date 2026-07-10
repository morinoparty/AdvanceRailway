/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands

import arrow.core.Either
import dev.nikomaru.advancerailway.AdvanceRailway
import org.bukkit.command.CommandSender
import java.io.File

/**
 * `data/` 配下の各サブフォルダへのパスを一元化するヘルパ。
 *
 * コマンド実装のあちこちに散らばっていた
 * `dataFolder.resolve("data").resolve("groups"/"railways"/"stations")`
 * という重複したリテラルを 1 箇所に集約する。
 *
 * アクセサは computed（`get()`）にしており、クラスロード時に
 * [AdvanceRailway.plugin]（`lateinit`）へ触れないようにしている。
 */
object DataPaths {
    private val dataFolder: File get() = AdvanceRailway.plugin.dataFolder.resolve("data")
    val groups: File get() = dataFolder.resolve("groups")
    val railways: File get() = dataFolder.resolve("railways")
    val stations: File get() = dataFolder.resolve("stations")
}

/**
 * [Either] の [Either.Right] を取り出す。[Either.Left] の場合は [msg] で生成した
 * メッセージを [sender] にリッチメッセージとして送り、`null` を返す。
 *
 * 各コマンドで繰り返されていた
 * `when(val res = XUtils.getXData(id)){ is Either.Left -> { send; return }; is Either.Right -> res.value }`
 * という定型的なアンラップを 1 行に置き換えるためのヘルパ。呼び出し側は
 * `?: return` で早期リターンする想定。
 */
inline fun <L, R> Either<L, R>.getOrSend(sender: CommandSender, msg: (L) -> String): R? = when (this) {
    is Either.Left -> {
        sender.sendRichMessage(msg(this.value))
        null
    }

    is Either.Right -> this.value
}
