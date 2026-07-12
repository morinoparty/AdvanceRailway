/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
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
import party.morino.mineauth.api.EndpointRegistrationException
import party.morino.mineauth.api.MineAuthApi

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
     * 登録した場合でも各エンドポイントはサービストークンでの認証で保護される（[RailwayApiHandler] 参照）。
     *
     * mineauth-api は compileOnly のため、MineAuth 不在時にその型を参照するクラスをロードすると
     * NoClassDefFoundError で enable ごと落ちる。そこで MineAuth の型に触れるコードは [Registrar] に
     * 閉じ込め、この object 自体は MineAuth の型を一切参照せず、プラグインの存在を確認してから
     * [Registrar] をロードする（softdepend の定石）。
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

        // MineAuth 不在なら Registrar（MineAuth の型を参照する）をロードする前に抜ける。
        if (plugin.server.pluginManager.getPlugin("MineAuth") == null) {
            plugin.logger.info("MineAuth not found; skipping HTTP endpoint registration.")
            return
        }

        Registrar.register(plugin)
    }

    /**
     * MineAuth の型（compileOnly）を参照する処理の置き場。MineAuth の存在確認後にのみ
     * ロードされるよう、[MineAuthIntegration.register] 以外から参照しないこと。
     */
    private object Registrar {

        /**
         * MineAuth の [MineAuthApi] は Bukkit の ServicesManager 経由で取得する
         * （バージョン非対応等でサービス未登録なら null）。
         * 登録は all-or-nothing で、検証に失敗すると [EndpointRegistrationException] が送出されるため、
         * その場合でも onEnable を巻き込まないようフェイルセーフでログのみ出力する。
         */
        fun register(plugin: AdvanceRailway) {
            val api = MineAuthApi.get(plugin.server)
            if (api == null) {
                plugin.logger.info("MineAuth API service not found; skipping HTTP endpoint registration.")
                return
            }

            try {
                val registration = api.register(plugin, RailwayApiHandler())
                plugin.logger.info("Registered MineAuth HTTP endpoints under ${registration.basePath}")
            } catch (e: EndpointRegistrationException) {
                // 検証エラーで登録に失敗しても AdvanceRailway 本体の起動は継続する。
                plugin.logger.warning("Failed to register MineAuth HTTP endpoints: ${e.message}")
            }
        }
    }
}
