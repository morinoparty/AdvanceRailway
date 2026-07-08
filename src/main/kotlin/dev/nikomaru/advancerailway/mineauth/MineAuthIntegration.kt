/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.mineauth

import dev.nikomaru.advancerailway.AdvanceRailway
import party.morino.mineauth.api.MineAuthAPI

/**
 * MineAuth との連携をセットアップするエントリーポイント。
 * MineAuth は softdepend であり、存在しない場合は何もせず AdvanceRailway は単体で動作する。
 */
object MineAuthIntegration {

    /**
     * MineAuth が導入されていれば [RailwayApiHandler] を登録する。
     * 登録後、エンドポイントは `/api/v1/plugins/advancerailway/` 配下で利用可能になる。
     *
     * @param plugin AdvanceRailway 本体
     */
    fun register(plugin: AdvanceRailway) {
        // MineAuth 本体を安全にキャストして取得する（未導入・非対応バージョンでは null）。
        val api = plugin.server.pluginManager.getPlugin("MineAuth") as? MineAuthAPI
        if (api == null) {
            plugin.logger.info("MineAuth not found; skipping HTTP endpoint registration.")
            return
        }

        api.createHandler(plugin)
            .register(RailwayApiHandler())
        plugin.logger.info("Registered MineAuth HTTP endpoints under /api/v1/plugins/advancerailway/")
    }
}
