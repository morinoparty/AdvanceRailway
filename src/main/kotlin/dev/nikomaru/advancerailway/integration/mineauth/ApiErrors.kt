/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.integration.mineauth

import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus

/**
 * MineAuth へ返す 4xx エラー（[HttpError]）を生成する。
 *
 * [HttpError] のコンストラクタを Kotlin のデフォルト引数付きで呼ぶと、コンパイラは
 * `HttpError(HttpStatus, String, String, Map, int, kotlin.jvm.internal.DefaultConstructorMarker)`
 * という合成コンストラクタへの呼び出しを生成する。AdvanceRailway は kotlin-stdlib を shadowJar に
 * 同梱しており、`HttpError` 自体は MineAuth のクラスローダから読み込まれるため、
 * `DefaultConstructorMarker` が 2 つのクラスローダで別物として解決され、実行時に
 * `LinkageError (loader constraint violation)` になる。`LinkageError` は MineAuth の
 * `catch (e: Exception)` をすり抜けるため、意図した 4xx ではなく text/plain の 500 が返っていた。
 *
 * これを避けるため、必ず全引数を明示した公開コンストラクタ（Java / MineAuth の型のみを含む）を呼ぶ。
 * MineAuth API の型を Kotlin 固有型が混ざるシグネチャで呼ばないことは
 * `MineAuthApiLinkageTest` が compiled class を検査して保証する。
 */
internal fun apiError(
    status: HttpStatus,
    message: String,
    code: String,
    details: Map<String, String> = emptyMap(),
): HttpError = HttpError(status, message, code, details)
