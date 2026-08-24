/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.domain.numbering

/**
 * 駅ナンバリング（`JY01` など）の組み立て。
 *
 * 番号は駅ではなく**グループ（路線）**が持つ。グループが接頭辞と開始番号を持ち、
 * グループ内の駅の並び順（`group_stations.position`）から各駅の番号を算出する。
 * 駅は複数のグループに属し得るため、番号は常に「グループと駅の組」に対して決まる。
 */
object StationNumbering {

    /** ゼロ埋めの最小桁数。`JY01` のように 2 桁を基本とし、3 桁以上はそのまま出す。 */
    const val MIN_DIGITS = 2

    /**
     * 番号を組み立てる。
     *
     * @param prefix グループの接頭辞。null または空ならナンバリングなしとして null を返す。
     * @param start グループの開始番号。
     * @param position グループ内での 0 始まりの並び順。
     */
    fun format(prefix: String?, start: Int, position: Int): String? {
        if (prefix.isNullOrBlank()) return null
        val number = start + position
        return prefix + number.toString().padStart(MIN_DIGITS, '0')
    }
}
