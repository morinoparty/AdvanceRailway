/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
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
    alias(libs.plugins.spotless)
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

    // Incendo Cloud コマンドフレームワーク（morinoparty 系プラグインと統一）。
    implementation(libs.bundles.commands.cloud)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.csv)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mccoroutine.bukkit.api)
    implementation(libs.mccoroutine.bukkit.core)

    implementation(libs.koin.core)

    compileOnly(libs.squaremap.api)
    compileOnly(libs.protocolLib)

    // MineAuth HTTP API 連携。MineAuth 本体（softdepend）から実行時に提供されるため compileOnly。
    // ハンドラーは @Serializable な DTO をそのまま返し、シリアライズは MineAuth 側が担う。
    compileOnly(libs.mineauth.api)

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
    // ハンドラーが返す DTO / throw する HttpError をテストから参照するため
    // （main では compileOnly のためテストクラスパスには別途載せる）。
    testImplementation(libs.mineauth.api)
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

spotless {
    // detekt と同様、当面は非ゲート（`check`/`build` を失敗させない）。開発者が任意に
    // `./gradlew spotlessApply`（一括付与・更新）/ `spotlessCheck`（検証）を実行する運用とする。
    isEnforceCheck = false

    // ライセンスヘッダーは config/spotless/license-header.kt に一元管理し、$YEAR トークンで年を表す。
    // updateYearWithLatest により、既存の年（2024）を「2024-現在年」の範囲へ更新する
    // （例: 2024 → 2024-2026）。新規ファイルは現在年のみ。全ファイルを対象にするため ratchet は使わない。
    val licenseHeader = rootProject.file("config/spotless/license-header.kt")
    kotlin {
        target("src/**/*.kt")
        licenseHeaderFile(licenseHeader).updateYearWithLatest(true)
    }
    kotlinGradle {
        target("*.gradle.kts")
        // .gradle.kts の最初の非ヘッダー行（build: import / settings: pluginManagement 等）を区切りとする。
        licenseHeaderFile(licenseHeader, "(import|plugins|pluginManagement|dependencyResolutionManagement|rootProject|@file)")
            .updateYearWithLatest(true)
    }
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
        // （MineAuth 連携は @Serializable DTO を返し、MineAuth 側がそのシリアライザを解決するため）。
        relocate("org.koin", "dev.nikomaru.advancerailway.libs.koin")
        relocate("arrow", "dev.nikomaru.advancerailway.libs.arrow")
        // Cloud は relocate しない。Paper の Brigadier ネイティブ連携（cloud-paper）が反射で
        // 内部クラスを解決するため relocate は壊れやすく、プラグイン間はクラスローダで分離されるため不要。
        relocate("com.github.shynixn.mccoroutine", "dev.nikomaru.advancerailway.libs.mccoroutine")
    }
    runServer {
        // 実ターゲットは Paper 26.x（Minecraft 1.21.11）。従来の 1.21.8 は squaremap(api 26.2) が
        // 読み込めず陳腐化していたため更新する。
        minecraftVersion("26.2")
        // 他の worktree のテストサーバと同時起動してもポート衝突しないよう既定の 25565 からずらす。
        args("--port", "25599")
        // hard depend の ProtocolLib / squaremap をテストサーバへ自動導入して onEnable を通す。
        downloadPlugins {
            hangar("squaremap", "1.3.14")
            // 26.x 対応は ProtocolLib の dev-build のみ（安定版 5.4.0 は 1.21.8 まで）。
            github("dmulloy2", "ProtocolLib", "dev-build", "ProtocolLib.jar")
        }
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
        // 生成された plugin.yml の version が project.version と一致するかを PluginYmlTest で検証するため、
        // 期待値をシステムプロパティで渡す。plugin.yml 生成（processResources）にも依存させる。
        dependsOn("processResources")
        systemProperty("advancerailway.projectVersion", project.version.toString())
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
            // プロジェクトのバージョンをそのまま plugin.yml へ反映する。
            // （固定文字列を置くと `ar info` にプレースホルダがそのまま出る。回帰は PluginYmlTest で防ぐ。）
            version = project.version.toString()
            website = "https://github.com/Nlkomaru/AdvanceRailway"
            depend = listOf("ProtocolLib", "squaremap")
            // MineAuth があれば HTTP エンドポイントを登録するが、無くても単体で動作する。
            softDepend = listOf("MineAuth")
            main = "$group.advancerailway.AdvanceRailway"
            authors = listOf("Nikomaru")

            // 権限はロール型の 3 階層で構成する。
            //   admin  (OP)   … すべての操作。user + manage + 運用系(inspect/debug) を束ねる。
            //   user   (TRUE) … すべての閲覧。全員がデフォルトで持つ。
            //   manage (OP)   … すべての編集。op のみ。
            // リーフにも明示的な default を置く。親が default=TRUE でリーフへ true を
            // カスケードする旧構造こそが「全員が書き込み可能」バグの原因だったため、
            // TRUE を持つのは閲覧リーフだけに限定する。回帰は PluginYmlTest が監視する。
            permissions {
                register("advancerailway.admin") {
                    default = Permission.Default.OP
                    children(
                        "advancerailway.user",
                        "advancerailway.manage",
                        "advancerailway.inspect",
                        "advancerailway.debug",
                    )
                }
                register("advancerailway.user") {
                    default = Permission.Default.TRUE
                    children(
                        "advancerailway.info",
                        "advancerailway.station.view",
                        "advancerailway.railway.view",
                        "advancerailway.railway.route",
                        "advancerailway.group.view",
                    )
                }
                register("advancerailway.manage") {
                    default = Permission.Default.OP
                    children(
                        "advancerailway.station.manage",
                        "advancerailway.railway.manage",
                        "advancerailway.group.manage",
                        "advancerailway.file",
                        "advancerailway.reload",
                    )
                }
                // 閲覧リーフ（全員 TRUE）。
                register("advancerailway.info") { default = Permission.Default.TRUE }
                register("advancerailway.station.view") { default = Permission.Default.TRUE }
                register("advancerailway.railway.view") { default = Permission.Default.TRUE }
                register("advancerailway.railway.route") { default = Permission.Default.TRUE }
                register("advancerailway.group.view") { default = Permission.Default.TRUE }
                // 編集・運用リーフ（OP のみ）。
                register("advancerailway.station.manage") { default = Permission.Default.OP }
                register("advancerailway.railway.manage") { default = Permission.Default.OP }
                register("advancerailway.group.manage") { default = Permission.Default.OP }
                register("advancerailway.file") { default = Permission.Default.OP }
                register("advancerailway.reload") { default = Permission.Default.OP }
                register("advancerailway.inspect") { default = Permission.Default.OP }
                register("advancerailway.debug") { default = Permission.Default.OP }
            }
            apiVersion = "1.21"
        }
    }
}