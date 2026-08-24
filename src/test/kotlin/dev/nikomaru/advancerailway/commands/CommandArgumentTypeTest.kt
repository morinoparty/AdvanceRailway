/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.commands

import dev.nikomaru.advancerailway.commands.group.GroupEditCommand
import dev.nikomaru.advancerailway.commands.group.GroupInfoCommand
import dev.nikomaru.advancerailway.commands.group.GroupMainCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayEditCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayExploreCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayInfoCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayMainCommand
import dev.nikomaru.advancerailway.commands.railway.RailwayRouteCommand
import dev.nikomaru.advancerailway.commands.station.StationEditCommand
import dev.nikomaru.advancerailway.commands.station.StationInfoCommand
import dev.nikomaru.advancerailway.commands.station.StationMainCommand
import dev.nikomaru.advancerailway.domain.id.GroupId
import dev.nikomaru.advancerailway.domain.id.RailwayId
import dev.nikomaru.advancerailway.domain.id.StationId
import org.incendo.cloud.annotations.Command
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * コマンドハンドラの引数型が、Cloud のパーサ登録と噛み合う形で残っているかを見る。
 *
 * 主キーを `@JvmInline value class` にしていた頃、`StationId` はコンパイル時に [UUID] へ
 * インライン展開され、ハンドラの JVM 上の引数型が [UUID] になっていた。その結果 Cloud は
 * 登録済みの [dev.nikomaru.advancerailway.commands.parser.StationIdParser] ではなく組み込みの
 * UUID パーサーを選び、`/ar station tp <slug>` や駅名での実行がすべて弾かれていた。
 *
 * 型の宣言だけを見ても気づけない壊れ方なので、コンパイル後のシグネチャを直接確認する。
 */
class CommandArgumentTypeTest {

    private val commandClasses = listOf(
        GeneralCommand::class.java,
        StationMainCommand::class.java,
        StationInfoCommand::class.java,
        StationEditCommand::class.java,
        RailwayMainCommand::class.java,
        RailwayInfoCommand::class.java,
        RailwayEditCommand::class.java,
        RailwayExploreCommand::class.java,
        RailwayRouteCommand::class.java,
        GroupMainCommand::class.java,
        GroupInfoCommand::class.java,
        GroupEditCommand::class.java,
    )

    private fun commandMethods() = commandClasses.flatMap { type ->
        type.declaredMethods.filter { it.isAnnotationPresent(Command::class.java) }.map { type to it }
    }

    @Test
    @DisplayName("no command handler takes a raw UUID, which would bypass the id parsers")
    fun noHandlerTakesRawUuid() {
        val offenders = commandMethods()
            .filter { (_, method) -> method.parameterTypes.any { it == UUID::class.java } }
            .map { (type, method) -> "${type.simpleName}.${method.name}" }

        assertTrue(
            offenders.isEmpty(),
            "これらのハンドラは UUID を直接受け取っており、slug や表示名で実行できません: $offenders",
        )
    }

    @Test
    @DisplayName("id arguments keep their declared types, so the registered parsers are used")
    fun idArgumentsKeepTheirTypes() {
        val idTypes = setOf(StationId::class.java, RailwayId::class.java, GroupId::class.java)
        val handlersTakingIds = commandMethods()
            .filter { (_, method) -> method.parameterTypes.any { it in idTypes } }

        // ID を取るハンドラが 1 つも見つからなければ、インライン展開などで型が消えている。
        assertTrue(
            handlersTakingIds.size >= 10,
            "ID 型を引数に取るハンドラが ${handlersTakingIds.size} 件しかありません（型が消えている可能性）",
        )
    }

    @Test
    @DisplayName("station tp takes a StationId, so it accepts a slug or a display name")
    fun stationTpTakesStationId() {
        val tp = StationMainCommand::class.java.declaredMethods.single { it.name == "tp" }
        assertTrue(
            tp.parameterTypes.any { it == StationId::class.java },
            "/ar station tp の引数が StationId ではありません: ${tp.parameterTypes.map { it.simpleName }}",
        )
    }
}
