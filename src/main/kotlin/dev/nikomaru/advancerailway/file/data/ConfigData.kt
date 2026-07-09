/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.file.data

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
     * 有効時もエンドポイントは権限（advancerailway.mineauth.read）で保護される。
     */
    val mineAuthEnabled: Boolean = true,
)