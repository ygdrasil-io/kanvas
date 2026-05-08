package org.skia.dm

/**
 * One row of the DM run output — one (GM, sink) pair.
 *
 * Mirrors upstream's
 * [`DM::JsonWriter::BitmapResult`](https://github.com/google/skia/blob/main/dm/DMJsonWriter.h)
 * struct, which is the unit of data the upstream `dm.json` emits per
 * "result". Field names and string conventions match what upstream
 * writes so the Kotlin output is line-diffable against a reference
 * `dm.json` produced by the C++ harness.
 *
 * Failures carry [errorMessage] non-null and leave the bitmap-side
 * fields ([md5], [colorType], …) as empty strings. Successes leave
 * [errorMessage] null.
 */
public data class RunRecord(
    /** GM name (the [`name()`][org.skia.tests.GM.name] string). */
    val gmName: String,
    /** Sink tag — e.g. `"8888"`, `"f16"`, `"pic-8888"`. */
    val sinkTag: String,
    /**
     * Hex-encoded MD5 of the encoded output bytes (PNG for raster /
     * picture-replay sinks). Empty string on failure.
     */
    val md5: String,
    /** File extension of the encoded output (`"png"` for raster sinks). */
    val extension: String,
    /**
     * Upstream-style colour metadata strings — see
     * [Runner.classifyBitmap]. Mirror what upstream writes in the
     * `"options"` sub-object of each `dm.json` result.
     */
    val colorType: String,
    val alphaType: String,
    val gamut: String,
    val transferFn: String,
    val colorDepth: String,
    /**
     * Upstream's `source_type` field. Always `"gm"` for our harness ;
     * upstream also emits `"skp"` and `"image"` from non-GM sources
     * which we do not run.
     */
    val sourceType: String = "gm",
    /**
     * Failure message, if any. `null` for successful runs ; non-null
     * implies the rest of the fields are empty.
     */
    val errorMessage: String? = null,
) {
    public val passed: Boolean get() = errorMessage == null
}

/**
 * Aggregate result of a [Runner.run] invocation.
 *
 * [toJson] emits a JSON document compatible with upstream Skia DM's
 * `dm.json` (see
 * [`DMJsonWriter::DumpJson`](https://github.com/google/skia/blob/main/dm/DMJsonWriter.cpp)).
 * We deliberately keep the JSON writer hand-rolled : the format is
 * tiny (one root object with a `results` array of flat records), and
 * pulling in a JSON library for it would be heavier than the writer
 * itself.
 *
 * **Format** (pretty-printed) :
 * ```json
 * {
 *   "<property name>": "<property value>",  // top-level metadata
 *   "key": { "<key name>": "<key value>" }, // top-level run-key
 *   "results": [
 *     {
 *       "key": {
 *         "name": "<gm name>",
 *         "config": "<sink tag>",
 *         "source_type": "gm"
 *       },
 *       "options": {
 *         "ext": "png",
 *         "gamut": "rec2020", "transfer_fn": "rec2020",
 *         "color_type": "rgba_f16", "alpha_type": "premul",
 *         "color_depth": "f16"
 *       },
 *       "md5": "<32-hex>"
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * Failed records use the same shape but with all option fields empty
 * and `"md5"` carrying the error message prefixed with `"error: "` —
 * mirrors upstream's diagnostic-on-failure convention.
 */
public data class Report(
    val passed: List<RunRecord>,
    val failed: List<RunRecord>,
    /** Top-level properties (e.g. `"build" → "release"`). */
    val properties: Map<String, String> = emptyMap(),
    /** Top-level run-key (e.g. `"os" → "Mac"`, `"compiler" → "Clang"`). */
    val key: Map<String, String> = emptyMap(),
) {

    /** Convenience : every record, in passed-first-then-failed order. */
    public val all: List<RunRecord> get() = passed + failed

    /** Pass / fail / total summary in one line. */
    public fun summary(): String =
        "DM run : ${passed.size} passed, ${failed.size} failed (total ${all.size})"

    /**
     * Emit upstream-compatible `dm.json` text. See class kdoc for the
     * shape ; pretty-printed with 2-space indent so the output is
     * line-diffable against a reference run.
     */
    public fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        for ((k, v) in properties) {
            sb.append("  ").append(jsonStr(k)).append(": ").append(jsonStr(v)).append(",\n")
        }
        sb.append("  ").append(jsonStr("key")).append(": {")
        if (key.isEmpty()) {
            sb.append("},\n")
        } else {
            sb.append("\n")
            val keyEntries = key.entries.toList()
            for ((i, e) in keyEntries.withIndex()) {
                sb.append("    ").append(jsonStr(e.key)).append(": ").append(jsonStr(e.value))
                if (i < keyEntries.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("  },\n")
        }
        sb.append("  ").append(jsonStr("results")).append(": [")
        if (all.isEmpty()) {
            sb.append("]\n}")
            return sb.toString()
        }
        sb.append("\n")
        for ((i, r) in all.withIndex()) {
            sb.append(recordJson(r))
            if (i < all.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }

    private fun recordJson(r: RunRecord): String {
        val sb = StringBuilder()
        sb.append("    {\n")
        sb.append("      \"key\": {\n")
        sb.append("        \"name\": ").append(jsonStr(r.gmName)).append(",\n")
        sb.append("        \"config\": ").append(jsonStr(r.sinkTag)).append(",\n")
        sb.append("        \"source_type\": ").append(jsonStr(r.sourceType)).append("\n")
        sb.append("      },\n")
        sb.append("      \"options\": {\n")
        sb.append("        \"ext\": ").append(jsonStr(r.extension)).append(",\n")
        sb.append("        \"gamut\": ").append(jsonStr(r.gamut)).append(",\n")
        sb.append("        \"transfer_fn\": ").append(jsonStr(r.transferFn)).append(",\n")
        sb.append("        \"color_type\": ").append(jsonStr(r.colorType)).append(",\n")
        sb.append("        \"alpha_type\": ").append(jsonStr(r.alphaType)).append(",\n")
        sb.append("        \"color_depth\": ").append(jsonStr(r.colorDepth)).append("\n")
        sb.append("      },\n")
        if (r.errorMessage != null) {
            sb.append("      \"md5\": ").append(jsonStr("error: ${r.errorMessage}")).append("\n")
        } else {
            sb.append("      \"md5\": ").append(jsonStr(r.md5)).append("\n")
        }
        sb.append("    }")
        return sb.toString()
    }

    /**
     * Minimal JSON string escape — handles the 7 mandatory cases per
     * [RFC 8259 § 7](https://datatracker.ietf.org/doc/html/rfc8259#section-7)
     * (`"`, `\`, control chars), then `\uXXXX`-escapes any other
     * sub-`0x20` byte. We deliberately do not escape non-ASCII : Skia
     * GM names are ASCII-only and the upstream writer leaves UTF-8
     * literal too.
     */
    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c.code == 0x0C -> sb.append("\\f")
                c.code < 0x20 -> sb.append("\\u").append("%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
