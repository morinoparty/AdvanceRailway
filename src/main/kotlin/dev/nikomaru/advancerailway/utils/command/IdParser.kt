/*
 * Written in 2024-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.advancerailway.utils.command

import dev.nikomaru.advancerailway.file.DataPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.suggestion.BlockingSuggestionProvider
import java.io.File

/** 1 件の実体（ファイル名から得た ID と、任意の表示名）。 */
data class IdEntry(val id: String, val name: String?)

/**
 * `data/<type>/` フォルダ配下の JSON を、ID と表示名のインデックスとして扱う純粋ロジック。
 * Koin / Bukkit に依存しないため単体テストできる。
 */
object IdIndex {
    private val json = Json { ignoreUnknownKeys = true }

    /** フォルダ内容＋更新時刻の署名（キャッシュ無効化判定用）。 */
    fun signature(folder: File): String {
        val files = folder.listFiles { f: File -> f.isFile && f.extension == "json" }?.sortedBy { it.name }
            ?: return ""
        return files.joinToString("|") { "${it.name}:${it.lastModified()}" }
    }

    /**
     * フォルダ内の各 JSON を [IdEntry] として読み込む。
     * ID はファイル名（拡張子なし）。表示名は [nameField] が指定されていればその JSON フィールドから読む
     * （空文字・欠落・パース失敗は `null` 扱い）。
     */
    fun read(folder: File, nameField: String?): List<IdEntry> {
        val files = folder.listFiles { f: File -> f.isFile && f.extension == "json" }?.sortedBy { it.name }
            ?: return emptyList()
        return files.map { file ->
            val name = nameField?.let { field ->
                runCatching { json.parseToJsonElement(file.readText()).jsonObject[field]?.jsonPrimitive?.contentOrNull }
                    .getOrNull()
            }
            IdEntry(file.nameWithoutExtension, name?.takeIf { it.isNotBlank() })
        }
    }

    /** 補完候補: 名前があれば名前、無ければ ID。 */
    fun suggestions(entries: List<IdEntry>): Set<String> = entries.map { it.name ?: it.id }.toSet()

    /** 入力を、まず表示名として、次に ID として解決する（名前が一致すればその ID、しなければ入力そのもの）。 */
    fun resolveId(entries: List<IdEntry>, token: String): String =
        entries.firstOrNull { it.name == token }?.id ?: token
}

/**
 * Shared implementation for id parsers (Group/Railway/Station) that are all backed by a
 * per-type subfolder under the plugin's data directory (e.g. files under `data/groups/`).
 *
 * When [nameField] is provided, entries also expose a **human-readable display name**. Suggestions then
 * offer the names (nobody can memorise ids like `fti`), and [resolve] accepts either the display name or
 * the raw id. When [nameField] is null (e.g. railways, which have no name), suggestions and resolution
 * fall back to ids only.
 */
abstract class IdParser<C, T : Any>(
    private val subfolder: String,
    private val idFactory: (String) -> T,
    /** JSON field holding the display name, or null if this entity type has no name. */
    private val nameField: String? = null,
) : ArgumentParser<C, T>, BlockingSuggestionProvider.Strings<C> {

    private val folder get() = DataPaths.of(subfolder)

    // 補完はキーストローク毎に呼ばれるため、フォルダ署名でキャッシュし変化が無ければ再パースしない。
    @Volatile
    private var cache: Pair<String, List<IdEntry>>? = null

    /** Ensures the backing data folder exists. Call once at startup, never from suggestions. */
    fun ensureDataFolder() {
        if (!folder.exists()) {
            folder.mkdirs()
        }
    }

    private fun entries(): List<IdEntry> {
        val signature = IdIndex.signature(folder)
        cache?.let { (cachedSig, cached) -> if (cachedSig == signature) return cached }
        val fresh = IdIndex.read(folder, nameField)
        cache = signature to fresh
        return fresh
    }

    // parse / suggestions は Cloud の asyncCoordinator・補完スレッド上で呼ばれるため、
    // ブロッキング IO（フォルダ読み取り）をそのまま行ってよい。
    override fun parse(
        commandContext: CommandContext<C & Any>,
        commandInput: CommandInput,
    ): ArgumentParseResult<T> {
        val token = commandInput.readString()
        val id = IdIndex.resolveId(entries(), token)
        return runCatching { idFactory(id) }.fold(
            onSuccess = { ArgumentParseResult.success(it) },
            onFailure = { ArgumentParseResult.failure(IllegalArgumentException("ID が不正です: $token")) },
        )
    }

    override fun stringSuggestions(
        commandContext: CommandContext<C>,
        input: CommandInput,
    ): Iterable<String> = IdIndex.suggestions(entries())
}
