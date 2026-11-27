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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.lang.classfile.ClassFile
import java.lang.classfile.constantpool.MemberRefEntry
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

/**
 * MineAuth API とのクラスローダ境界で `LinkageError (loader constraint violation)` が起きないことを検証する。
 *
 * 本番では AdvanceRailway（kotlin-stdlib を同梱）と MineAuth が別々のクラスローダで kotlin-stdlib を持ち、
 * MineAuth API の型（`party.morino.mineauth.*`）だけが MineAuth 側から共有される。そのため
 * MineAuth API のメソッド・コンストラクタを **Kotlin 固有型を含むシグネチャ**で呼ぶと、
 * 両ローダの kotlin クラスが衝突して実行時に失敗する（例: デフォルト引数付き `HttpError(...)` が生成する
 * `DefaultConstructorMarker` 付き合成コンストラクタ）。
 */
class MineAuthApiLinkageTest {

    private val mainClassesDir: Path = Path.of(
        RailwayApiHandler::class.java.protectionDomain.codeSource.location.toURI()
    )

    @Test
    @DisplayName("main のクラスは MineAuth API を Kotlin 固有型を含むシグネチャで参照しない")
    fun noKotlinTypesInMineAuthMemberReferences() {
        val violations = Files.walk(mainClassesDir).use { paths ->
            paths.filter { it.extension == "class" }.toList().flatMap { file ->
                ClassFile.of().parse(file.readBytes()).constantPool()
                    .filterIsInstance<MemberRefEntry>()
                    .filter { it.owner().asInternalName().startsWith("party/morino/mineauth/") }
                    .filter { "Lkotlin/" in it.type().stringValue() }
                    .map {
                        "${file.relativeTo(mainClassesDir)}: " +
                            "${it.owner().asInternalName()}.${it.name().stringValue()}${it.type().stringValue()}"
                    }
            }
        }
        assertTrue(violations.isEmpty()) {
            "MineAuth API への参照に Kotlin 固有型が含まれている（別クラスローダで LinkageError になる）。" +
                "HttpError は apiError() 経由で生成すること:\n" + violations.distinct().joinToString("\n")
        }
    }

    @Test
    @DisplayName("本番と同じクラスローダ構成で apiError は MineAuth の HttpError を生成できる")
    fun apiErrorWorksAcrossClassLoaders() {
        withSplitLoaders { mineAuth, plugin ->
            val status = mineAuth.loadClass(HttpStatus::class.java.name).enumConstants
                .single { (it as Enum<*>).name == "BAD_REQUEST" }
            val error = plugin.loadClass("${RailwayApiHandler::class.java.packageName}.ApiErrorsKt")
                .getMethod("apiError", status.javaClass, String::class.java, String::class.java, Map::class.java)
                .invoke(null, status, "same", "same_station", emptyMap<String, String>())

            // MineAuth 側の HttpError（＝ MineAuth の catch (e: HttpError) に捕まる型）であること。
            assertSame(mineAuth.loadClass(HttpError::class.java.name), error.javaClass)
            assertEquals("same_station", error.javaClass.getMethod("getCode").invoke(error))
        }
    }

    @Test
    @DisplayName("デフォルト引数付きで HttpError を直接生成すると LinkageError になる（原因の再現）")
    fun defaultArgumentConstructorFailsAcrossClassLoaders() {
        withSplitLoaders { _, plugin ->
            val fixture = plugin.loadClass(LinkageFixture::class.java.name)
            val thrown = assertThrows<InvocationTargetException> {
                fixture.getMethod("createWithDefaults").invoke(fixture.getField("INSTANCE").get(null))
            }
            assertTrue(thrown.targetException is LinkageError) { "unexpected: ${thrown.targetException}" }
        }
    }

    /**
     * MineAuth 用ローダ（mineauth-api + kotlin-stdlib）と、プラグイン用ローダ（自前の kotlin-stdlib + 本プロジェクトのクラス。
     * `party.morino.*` だけ MineAuth 用ローダへ委譲）を作る。Paper のプラグインクラスローダと softdepend の関係を模したもの。
     */
    private fun withSplitLoaders(block: (mineAuth: ClassLoader, plugin: ClassLoader) -> Unit) {
        val platform = ClassLoader.getPlatformClassLoader()
        val mineAuthApiJar = HttpError::class.java.protectionDomain.codeSource.location
        val kotlinStdlibJar = Unit::class.java.protectionDomain.codeSource.location
        val testClassesDir = LinkageFixture::class.java.protectionDomain.codeSource.location

        URLClassLoader(arrayOf(mineAuthApiJar, kotlinStdlibJar), platform).use { mineAuth ->
            PluginLoader(arrayOf(mainClassesDir.toUri().toURL(), testClassesDir, kotlinStdlibJar), mineAuth).use { plugin ->
                // 本番では両プラグインとも起動後すぐに kotlin の基本クラスを読み込み済みなので、それを再現する。
                Class.forName(MARKER, true, mineAuth)
                Class.forName(MARKER, true, plugin)
                block(mineAuth, plugin)
            }
        }
    }

    private class PluginLoader(urls: Array<URL>, private val mineAuth: ClassLoader) :
        URLClassLoader(urls, getPlatformClassLoader()) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> =
            if (name.startsWith("party.morino.")) mineAuth.loadClass(name) else super.loadClass(name, resolve)
    }

    private companion object {
        const val MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
    }
}

/** 修正前の呼び出し方（デフォルト引数付きコンストラクタ）を再現するためのフィクスチャ。 */
object LinkageFixture {
    fun createWithDefaults(): HttpError = HttpError(HttpStatus.BAD_REQUEST, "same", code = "same_station")
}
