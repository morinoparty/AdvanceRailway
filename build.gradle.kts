/*
 * Written in 2024 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import xyz.jpenilla.resourcefactory.bukkit.Permission

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory)
    alias(libs.plugins.detekt)
}

group = "dev.nikomaru"
version = "1.0-SNAPSHOT"

fun captureVersion(dependency: Dependency): String {
    return dependency.version ?: throw IllegalArgumentException("Version not found for $dependency")
}


repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://plugins.gradle.org/m2/")
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    compileOnly(libs.paper.api)

    compileOnly(kotlin("stdlib"))

    implementation(libs.lamp.common)
    implementation(libs.lamp.bukkit)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.csv)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mccoroutine.bukkit.api)
    implementation(libs.mccoroutine.bukkit.core)

    implementation(libs.koin.core)

    compileOnly(libs.squaremap.api)
    compileOnly(libs.protocolLib)

    // MineAuth HTTP API 連携。MineAuth 本体（softdepend）から実行時に提供されるため compileOnly。
    compileOnly(libs.mineauth.api)
    // ハンドラーの戻り値を生 JSON（TextContent）として返すために利用。Ktor も MineAuth 側が提供する。
    compileOnly(libs.ktor.http)

    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)

    testImplementation(libs.junit.jupiter)
    // JUnit 6 ではテスト実行時に Platform Launcher を明示的にクラスパスへ載せる必要がある。
    testRuntimeOnly(libs.junit.platform.launcher)

    // エンドポイントを実サーバ相当で駆動するための結合テスト用依存。
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockk)
    // paper-api は main では compileOnly のため、テスト実行時に別途載せる。
    // MockBukkit 4.110.0 が実装するレジストリ ABI に合わせ、テストでは 1.21.11 の paper-api を使う
    // （main の 26.x は compileOnly なのでテスト実行クラスパスには載らず、競合しない）。
    testImplementation(libs.paper.api.test)
    // ハンドラーが返す TextContent / throw する HttpError をテストから参照するため
    // （main では compileOnly のためテストクラスパスには別途載せる）。
    testImplementation(libs.mineauth.api)
    testImplementation(libs.ktor.http)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

detekt {
    parallel = true
    buildUponDefaultConfig = true
    allRules = true
    // 段階的に導入するため、指摘があってもビルドは失敗させない。
    ignoreFailures = true
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        compilerOptions.javaParameters = true
    }
    compileTestKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    }
    build {
        dependsOn("shadowJar")
    }
    shadowJar {
        // 他プラグインとの衝突を避けるため、AdvanceRailway 固有のライブラリを relocate する。
        // kotlin stdlib / kotlinx.serialization / coroutines は relocate しない
        // （MineAuth 連携は TextContent で生 JSON を返すためシリアライザの共有は不要）。
        relocate("org.koin", "dev.nikomaru.advancerailway.libs.koin")
        relocate("arrow", "dev.nikomaru.advancerailway.libs.arrow")
        relocate("revxrsal.commands", "dev.nikomaru.advancerailway.libs.lamp")
        relocate("com.github.shynixn.mccoroutine", "dev.nikomaru.advancerailway.libs.mccoroutine")
    }
    runServer {
        minecraftVersion("1.21.8")
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}



sourceSets.main {
    resourceFactory {
        bukkitPluginYaml {
            name = "AdvanceRailway"
            version = "miencraft_plugin_version"
            website = "https://github.com/Nlkomaru/AdvanceRailway"
            depend = listOf("ProtocolLib", "squaremap")
            // MineAuth があれば HTTP エンドポイントを登録するが、無くても単体で動作する。
            softDepend = listOf("MineAuth")
            main = "$group.advancerailway.AdvanceRailway"
            authors = listOf("Nikomaru")

            permissions {
                register("advancerailway.admin") {
                    default = Permission.Default.TRUE
                    children(
                        "advancerailway.command.group",
                        "advancerailway.command.station",
                        "advancerailway.command.railway",
                        "advancerailway.command.common"
                    )
                }
            }
            apiVersion = "1.21"
        }
    }
}