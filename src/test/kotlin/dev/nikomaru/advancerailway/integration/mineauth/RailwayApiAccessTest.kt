/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.integration.mineauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mineauth.api.CallerType
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Delete
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Patch
import party.morino.mineauth.api.annotations.Post
import party.morino.mineauth.api.annotations.Put
import party.morino.mineauth.api.annotations.Public
import java.lang.reflect.Method

/**
 * [RailwayApiHandler] の各エンドポイントに宣言されたアクセス制御を検証するテスト。
 *
 * ハンドラーのメソッドを直接呼ぶ結合テストでは MineAuth の認証層を通らないため、
 * アノテーションをリフレクションで読み、読み取り系は認証のみ・書き込み系は管理者限定であることを固定する。
 * suspend 関数でも Java リフレクションならアノテーションを読めるので、kotlin-reflect には依存しない。
 */
class RailwayApiAccessTest {

    /** HTTP ルートを宣言しているメソッドと、そのメソッド・パスの組。 */
    private data class Endpoint(val method: Method, val verb: String, val path: String)

    /** ハンドラーが公開するすべてのエンドポイント。 */
    private val endpoints: List<Endpoint> = RailwayApiHandler::class.java.declaredMethods.mapNotNull { m ->
        when {
            m.isAnnotationPresent(Get::class.java) -> Endpoint(m, "GET", m.getAnnotation(Get::class.java).value)
            m.isAnnotationPresent(Post::class.java) -> Endpoint(m, "POST", m.getAnnotation(Post::class.java).value)
            m.isAnnotationPresent(Patch::class.java) -> Endpoint(m, "PATCH", m.getAnnotation(Patch::class.java).value)
            m.isAnnotationPresent(Put::class.java) -> Endpoint(m, "PUT", m.getAnnotation(Put::class.java).value)
            m.isAnnotationPresent(Delete::class.java) ->
                Endpoint(m, "DELETE", m.getAnnotation(Delete::class.java).value)

            else -> null
        }
    }

    private val reads get() = endpoints.filter { it.verb == "GET" }
    private val writes get() = endpoints.filter { it.verb != "GET" }

    private fun Endpoint.authenticated(): Authenticated {
        val auth: Authenticated? = method.getAnnotation(Authenticated::class.java)
        assertNotNull(auth, "$verb $path に @Authenticated がありません")
        return auth!!
    }

    @Test
    @DisplayName("every endpoint is @Authenticated and none is @Public")
    fun everyEndpointRequiresAuthentication() {
        endpoints.forEach { endpoint ->
            endpoint.authenticated()
            assertFalse(
                endpoint.method.isAnnotationPresent(Public::class.java),
                "${endpoint.verb} ${endpoint.path} が @Public になっています",
            )
        }
    }

    @Test
    @DisplayName("read endpoints need only authentication (no permission) and accept user and service tokens")
    fun readEndpointsAreOpenToSignedInPlayers() {
        reads.forEach { endpoint ->
            val auth = endpoint.authenticated()
            assertEquals("", auth.permission, "GET ${endpoint.path} がパーミッションを要求しています")
            assertEquals(
                setOf(CallerType.USER, CallerType.SERVICE),
                auth.callers.toSet(),
                "GET ${endpoint.path} の callers が想定と異なります",
            )
        }
    }

    @Test
    @DisplayName("write endpoints stay admin-only for user tokens and accept user and service tokens")
    fun writeEndpointsRequireAdmin() {
        writes.forEach { endpoint ->
            val auth = endpoint.authenticated()
            assertEquals(
                RailwayApiHandler.ADMIN_PERMISSION,
                auth.permission,
                "${endpoint.verb} ${endpoint.path} が管理者権限を要求していません",
            )
            assertEquals(
                setOf(CallerType.USER, CallerType.SERVICE),
                auth.callers.toSet(),
                "${endpoint.verb} ${endpoint.path} の callers が想定と異なります",
            )
        }
    }

    @Test
    @DisplayName("the set of public read endpoints is exactly the expected one")
    fun readEndpointSetIsPinned() {
        // 新しい GET を追加したときに、公開してよいデータかを見直すきっかけにするため一覧を固定する。
        val expected = setOf(
            "/stations",
            "/stations/{id}",
            "/stations/{id}/railways",
            "/nearest-station",
            "/railways",
            "/railways/{id}",
            "/groups",
            "/groups/{id}",
            "/groups/{id}/railways",
            "/groups/{id}/stations",
            "/route",
            "/stats",
        )
        assertEquals(expected, reads.map { it.path }.toSet())
        assertEquals(expected.size, reads.size, "同じパスの GET が重複しています")
    }

    @Test
    @DisplayName("the set of admin-only write endpoints is exactly the expected one")
    fun writeEndpointSetIsPinned() {
        val expected = setOf(
            "POST /stations",
            "PATCH /stations/{id}",
            "DELETE /stations/{id}",
            "POST /railways",
            "PATCH /railways/{id}",
            "DELETE /railways/{id}",
            "POST /groups",
            "PATCH /groups/{id}",
            "DELETE /groups/{id}",
            "PUT /groups/{id}/stations",
        )
        assertEquals(expected, writes.map { "${it.verb} ${it.path}" }.toSet())
        assertEquals(expected.size, writes.size, "同じルートの書き込みが重複しています")
    }
}
