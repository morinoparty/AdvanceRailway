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
 * 駅テーブル。
 *
 * 座標は x/y/z を個別の列に持つ（ワールド絞り込みと最寄り駅探索がそのまま書ける）。
 * `color` は NOT NULL。以前は未指定時に ID をシードとして色を導出していたため、
 * 主キーを UUID にすると既存駅の色が変わってしまう。移行時に slug をシードとして
 * 実体化し、以後は必ず値を持たせる。
 */
object StationTable : UuidV7Table("stations") {
    val slug = varchar("slug", SLUG_LENGTH).uniqueIndex()
    val name = varchar("name", NAME_LENGTH)

    /** ワールド名。ワールドが存在しない環境でも読み出せるよう、実体ではなく名前で持つ。 */
    val world = varchar("world", WORLD_LENGTH)

    val pointX = double("point_x")
    val pointY = double("point_y")
    val pointZ = double("point_z")

    /** マーカー半径の手動指定。null なら接続路線数から自動計算する。 */
    val overrideSize = double("override_size").nullable()

    /** 駅の色（RGB を int にパックしたもの）。 */
    val color = integer("color")
}
