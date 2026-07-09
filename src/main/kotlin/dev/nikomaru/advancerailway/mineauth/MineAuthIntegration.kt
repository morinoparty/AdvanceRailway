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
import dev.nikomaru.advancerailway.file.data.ConfigData
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import party.morino.mineauth.api.MineAuthAPI

/**
 * MineAuth との連携をセットアップするエントリーポイント。
 * MineAuth は softdepend であり、存在しない場合は何もせず AdvanceRailway は単体で動作する。
 */
object MineAuthIntegration : KoinComponent {

    /**
     * MineAuth が導入されていれば [RailwayApiHandler] を登録する。
     * 登録後、エンドポイントは `/api/v1/plugins/advancerailway/` 配下で利用可能になる。
     *
     * ただし [ConfigData.mineAuthEnabled] が false の場合は登録をスキップする（フェイルセーフ）。
     * 登録した場合でも各エンドポイントは権限で保護される（[RailwayApiHandler] 参照）。
     *
     * @param plugin AdvanceRailway 本体
     */
    fun register(plugin: AdvanceRailway) {
        // 設定で連携が無効化されている場合は何もしない。
        val config = get<ConfigData>()
        if (!config.mineAuthEnabled) {
            plugin.logger.info("MineAuth integration disabled by config; skipping HTTP endpoint registration.")
            return
        }

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
