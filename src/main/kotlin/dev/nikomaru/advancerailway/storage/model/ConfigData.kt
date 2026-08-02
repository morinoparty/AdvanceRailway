/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val limit: Long,
    val circleSizeBase: Double = 5.0,
    val circleMax: Double = 20.0,
    val circleMultiple: Double = 5.0, // size = base + (multiple * (stations.size))
    val calcString: String = "base + (multiple * (stations.size))", //TODO: Implement this
    /**
     * MineAuth の HTTP API 連携を有効にするか。
     * false の場合、MineAuth が導入されていてもエンドポイントを登録しない。
     * 有効時もエンドポイントはサービストークンでの認証で保護される。
     */
    val mineAuthEnabled: Boolean = true,
    /**
     * inspect の探索を打ち切るブロック（Material 名）。
     * レール直下 (y-1) がこのリストのブロックだった場合、その地点を終端として扱う。
     */
    val inspectStopBlocks: List<String> = listOf("GOLD_BLOCK"),
    /** inspect の分岐探索で列挙する終端数の上限。 */
    val inspectMaxEndpoints: Int = 16,
)