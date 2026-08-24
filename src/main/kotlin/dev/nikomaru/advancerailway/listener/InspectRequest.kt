/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.listener

import dev.nikomaru.advancerailway.domain.id.StationId
import dev.nikomaru.advancerailway.domain.rail.BranchDirection

/**
 * `/ar inspect` の 1 回分の要求。コマンドで受け取り、次にレールをクリックしたときに使う。
 *
 * 分岐の多い場所では終端が一気に増えて読めなくなるため、あらかじめ見たい経路を絞れるようにしている。
 *
 * @property flagPrefix たどる方角の並び（例 `E` / `EE`）。先頭が出発方角。
 *   空なら全方向を探索する。指定した並びから外れる分岐は探索しない。
 * @property destination この駅を終点とする経路だけを表示する。null なら絞り込まない。
 */
data class InspectRequest(
    val flagPrefix: List<BranchDirection> = emptyList(),
    val destination: StationId? = null,
)
