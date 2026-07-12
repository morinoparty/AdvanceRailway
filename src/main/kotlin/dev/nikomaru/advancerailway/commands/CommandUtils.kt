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
import org.bukkit.command.CommandSender
import java.awt.Color

/**
 * ユーザー由来の文字列（駅名・グループ名など）を MiniMessage に埋め込む際のエスケープ。
 * `<` を `\<` にしてタグ注入を防ぐ。表示に使う名前は必ずこれを通す。
 */
fun esc(text: String): String = text.replace("<", "\\<")

/** [Color] を MiniMessage の `<color:#RRGGBB>` で使える `#RRGGBB` 形式にする。 */
fun Color.toHex(): String = "#%02X%02X%02X".format(red, green, blue)

/** 秒単位の所要時間を「分（小数第1位）」の文字列にする。route 表示と同じ換算。 */
fun formatMinutes(timeRequiredSeconds: Long): String {
    val minutes = kotlin.math.ceil(timeRequiredSeconds / 6.0) / 10
    return "$minutes 分"
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

/**
 * 一覧を 1 ページ [perPage] 件でページ送り表示する共通ヘルパ。
 * 見出し（総件数・現在ページ）と、次ページへ進むクリック行を自動で付ける。
 * これにより `station list` / `railway list` / `group list` が同じ体裁になる。
 *
 * @param items       表示対象の全件（未整形の値）。
 * @param page        1 始まりのページ番号。範囲外は端にクランプする。
 * @param header      一覧の見出し（MiniMessage）。
 * @param empty       0 件のときに表示するメッセージ（MiniMessage）。
 * @param pageCommand ページ番号を付けて再実行するコマンド（末尾へ半角スペース＋番号を付す）。例: `/ar station list`
 * @param render      1 件を表示行（MiniMessage）へ整形する関数。
 */
inline fun <T> CommandSender.sendPaginated(
    items: List<T>,
    page: Int,
    header: String,
    empty: String,
    pageCommand: String,
    perPage: Int = 8,
    render: (T) -> String,
) {
    if (items.isEmpty()) {
        sendRichMessage(empty)
        return
    }
    val totalPages = (items.size + perPage - 1) / perPage
    val current = page.coerceIn(1, totalPages)
    val from = (current - 1) * perPage
    val to = minOf(from + perPage, items.size)
    sendRichMessage("$header <gray>(全 ${items.size} 件 / ページ $current/$totalPages)</gray>")
    for (i in from until to) sendRichMessage(render(items[i]))
    if (current < totalPages) {
        sendRichMessage(
            "<click:run_command:'$pageCommand ${current + 1}'><gray>» 次のページ ($current/$totalPages → ${current + 1})</gray></click>"
        )
    }
}
