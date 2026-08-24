/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.id

/**
 * 人間が読み書きする短い識別子（`tokyo` / `yamanote` など）。
 *
 * かつて ID そのものだった値で、コマンド引数・HTTP のパス・表示に使う。
 * 主キーではないため後から変更でき、一意性はデータベースの unique 制約が担保する。
 * 文字種の検証は [IdValidation] に委ねる（英数字・アンダースコア・ハイフンのみ）。
 */
@JvmInline
value class Slug(val value: String) {
    init {
        require(IdValidation.isValid(value)) { "Invalid slug: \"$value\"" }
    }

    override fun toString(): String = value

    companion object {
        /** 検証に通らなければ null を返す（例外を投げたくない境界向け）。 */
        fun parse(raw: String): Slug? = if (IdValidation.isValid(raw)) Slug(raw) else null
    }
}
