/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.database.table

/**
 * グループ（路線）テーブル。
 *
 * テーブル名を `railway_groups` にしているのは、`GROUPS` が SQLite 3.28 以降の
 * キーワード（ウィンドウ関数のフレーム指定）であり、引用が外れた経路で壊れるのを避けるため。
 *
 * ナンバリングの接頭辞と開始番号はここが持ち、駅ごとの番号は
 * [GroupStationTable] の並び順から算出する（駅自体は番号を持たない）。
 */
object GroupTable : UuidV7Table("railway_groups") {
    /** 人間可読な識別子。コマンド・API のパスで使う。 */
    val slug = varchar("slug", SLUG_LENGTH).uniqueIndex()

    /** 表示名。 */
    val name = varchar("name", NAME_LENGTH)

    /** 路線色（RGB を int にパックしたもの）。 */
    val color = integer("color")

    /** ナンバリングの接頭辞（`JY` など）。null ならナンバリングなし。 */
    val numberingPrefix = varchar("numbering_prefix", SLUG_LENGTH).nullable()

    /** ナンバリングの開始番号。 */
    val numberingStart = integer("numbering_start").default(1)
}
