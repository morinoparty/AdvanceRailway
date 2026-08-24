/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands

import dev.nikomaru.advancerailway.domain.id.Slug
import org.bukkit.command.CommandSender
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ユーザー由来の文字列（駅名・グループ名など）を MiniMessage に埋め込む際のエスケープ。
 * `<` を `\<` にしてタグ注入を防ぐ。表示に使う名前は必ずこれを通す。
 */
fun esc(text: String): String = text.replace("<", "\\<")

/**
 * 表示名と slug を併記する（`名前 (slug)`）。
 *
 * 表示名は覚えやすいが一意とは限らず、slug はコマンドにそのまま打てる。どちらか一方だけを出すと
 * 「見えている名前でコマンドを打ったら通らない」「slug しか出ないので何の駅か分からない」の
 * どちらかが起きるため、一覧・詳細・inspect のいずれでも両方を出す。
 *
 * 名前はユーザー由来なので必ず [esc] を通す。エスケープを忘れると、名前に含まれる `<` が
 * MiniMessage のタグとして解釈され、**その後ろに続く `[作成]` などのリンクごと消える**。
 */
fun nameWithSlug(name: String, slug: Slug): String =
    "<white>${esc(name)}</white> <dark_gray>(${slug.value})</dark_gray>"

/**
 * タグを含まない形の併記（`名前 (slug)`）。
 * 後段でまとめて [esc] を掛ける経路（経路表示のラベルなど）で使う。
 */
fun nameWithSlugPlain(name: String, slug: Slug): String = "$name (${slug.value})"

/** [Color] を MiniMessage の `<color:#RRGGBB>` で使える `#RRGGBB` 形式にする。 */
fun Color.toHex(): String = "#%02X%02X%02X".format(red, green, blue)

/**
 * クリックで `/ar station tp` を実行して駅へ飛ぶ `[TP]` リンク（MiniMessage）。
 * migrate / check の失敗行から現地をすぐ確認できるようにするためのもの。
 */
fun stationTpLink(slug: Slug): String =
    "<click:run_command:'/ar station tp ${slug.value}'>" +
        "<hover:show_text:'${slug.value} へテレポート'><gold>[TP]</gold></hover></click>"

/** サーバーのタイムゾーンで `2026-08-24 12:34` の形にする。 */
private val CHECKED_AT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

/**
 * 路線の最終確認時刻（`/ar railway check` が成功した時刻）を表示用の文字列にする。
 * 一度も確認できていない路線は「未確認」。
 */
fun formatCheckedAt(instant: Instant?): String =
    instant?.let { CHECKED_AT_FORMAT.format(it) } ?: "未確認"

/** 秒単位の所要時間を「分（小数第1位）」の文字列にする。route 表示と同じ換算。 */
fun formatMinutes(timeRequiredSeconds: Long): String {
    val minutes = kotlin.math.ceil(timeRequiredSeconds / 6.0) / 10
    return "$minutes 分"
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
