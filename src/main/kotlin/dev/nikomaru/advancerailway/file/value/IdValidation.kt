/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.value

/**
 * 駅・路線・グループなどの ID フォーマット検証を一元化するためのユーティリティ。
 *
 * ID は英数字・アンダースコア・ハイフンのみを許可する allowlist 方式で検証する。
 * これにより、パストラバーサル（`..`, `/`, `\`, `.`）や NUL バイト、
 * MiniMessage を破壊する文字などを含む不正な ID を弾く。
 *
 * コマンドや HTTP エンドポイントなどの境界では、値クラスを構築する前に
 * [isValid] を呼び出してユーザ入力を検証すること。値クラスの `init` でも同じ検証を
 * 行うため、不正な ID での構築は [IllegalArgumentException] を送出する。
 */
object IdValidation {
    /**
     * ID として許可する文字列の allowlist 正規表現。
     * 英数字・アンダースコア・ハイフンのみで構成され、1 文字以上であること。
     */
    val PATTERN: Regex = Regex("^[A-Za-z0-9_-]+$")

    /**
     * [id] が [PATTERN] に完全一致するかを判定する。
     *
     * 部分一致ではなく完全一致（[Regex.matches]）で判定するため、
     * 改行を含む文字列などの注入を確実に弾く。
     */
    fun isValid(id: String): Boolean = PATTERN.matches(id)
}
