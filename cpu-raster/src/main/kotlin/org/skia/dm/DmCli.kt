package org.skia.dm

import org.skia.foundation.SkColorSpace
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import org.skia.tests.GM

/**
 * Parsed command-line surface for the DM harness — D4.4.
 *
 * Mirrors upstream's
 * [`dm/DM.cpp`](https://github.com/google/skia/blob/main/dm/DM.cpp)
 * flag parsing for the three matrix-shaping options actually
 * exercised by the GM run :
 *
 *  - **`--config <tag>...`** — list of sink tags to instantiate. Each
 *    tag resolves to a `Sink` via [resolveSinks]. Recognised tags :
 *    `8888`, `f16`, `pic-8888`, `pic-f16`. Unknown tags are ignored
 *    with a `null` slot so the caller can warn ; we don't crash on a
 *    typo.
 *  - **`--match <pattern>...`** — name filter using upstream's
 *    `[~][^]substring[$]` syntax. See [matches] / [shouldRun] for
 *    the algorithm — port of `CommandLineFlags::ShouldSkip` that the
 *    upstream harness invokes per-GM.
 *  - **`--skip <config> <src> <srcOptions> <name>` quadruples** —
 *    blacklist using upstream's 4-at-a-time tuple syntax. `_`
 *    matches anything. `~` negates. The plan called this flag
 *    `--blacklist` but upstream calls it `--skip` ; we go with the
 *    upstream name. We always pass `gm` for `<src>` and `_` for
 *    `<srcOptions>` since the kanvas-skia harness only runs GMs.
 *
 * Plus housekeeping :
 *  - `--key <k1> <v1> <k2> <v2>...` — top-level run-key (see
 *    [Report]). Even-indexed entries are keys.
 *  - `--properties <k1> <v1> <k2> <v2>...` — top-level properties.
 *
 * **Argument syntax** : both `--flag value1 value2` (upstream style)
 * and `--flag=value` (POSIX-y) work. Repeating a flag appends to its
 * value list. The first non-flag token ends multi-value parsing for
 * the previous flag.
 *
 * **Out of scope for D4.4** : `--writePath`, `--readPath`, `--shards`,
 * `--threads`, `--bisect`, `--dryRun`, etc. The runner is
 * single-threaded (D4.3 decision) and we don't write per-result PNGs
 * yet ; those flags become useful once a workflow needs them.
 */
public class DmCli internal constructor(
    /** Sink tags requested via `--config` (in order, deduped is *not* applied). */
    public val configs: List<String>,
    /** Match patterns from `--match`. Empty = run everything. */
    public val match: List<String>,
    /**
     * Skip quadruples from `--skip` — flat list of length-4 tuples
     * `(config, src, srcOptions, name)`. Upstream guarantees the size
     * is a multiple of 4 ; [parse] rejects lopsided inputs.
     */
    public val skip: List<String>,
    /** `--key` run-key entries flattened as `[k1, v1, k2, v2, ...]`. */
    public val key: List<String>,
    /** `--properties` entries flattened the same way. */
    public val properties: List<String>,
    /**
     * Phase D2.6 — `--list-missing-effects` boolean flag. When set,
     * [DmMain.runFromArgs] prints the set of unregistered SkSL hashes
     * collected from failing GM records (via
     * [Report.missingRuntimeEffectHashes]) to `stderr` after the run.
     * Devs use the list to prioritise retro-porting effects to
     * [`SkBuiltinSpecialisedEffects`](../../../../../../org/skia/effects/runtime/effects/SkBuiltinSpecialisedEffects.kt)
     * (or the appropriate intrinsics cluster).
     */
    public val listMissingEffects: Boolean = false,
) {

    /**
     * Resolve `--config` tags to live [Sink] instances. Unknown tags
     * land as `null` so callers can warn / skip without the parser
     * deciding the policy.
     *
     * Default colour spaces match what `TestUtils.runGmTest` already
     * uses — `8888` ⇒ sRGB, `f16` ⇒ DM Rec.2020. Picture sinks wrap
     * the corresponding raster sink.
     */
    public fun resolveSinks(): List<Sink?> = configs.map { resolveOneSink(it) }

    private fun resolveOneSink(tag: String): Sink? = when (tag) {
        "8888" -> RasterSink8888()
        "f16" -> RasterSinkF16(dmReferenceColorSpace())
        "pic-8888" -> PictureSink(RasterSink8888())
        "pic-f16" -> PictureSink(RasterSinkF16(dmReferenceColorSpace()))
        "svg" -> SvgSink()
        else -> null
    }

    /**
     * The DM working colour space the GM harness renders into. Must
     * agree bit-for-bit with `TestUtils.DM_REFERENCE_COLOR_SPACE` —
     * we duplicate the construction here rather than depend on the
     * test sources, which aren't on the main classpath.
     */
    private fun dmReferenceColorSpace(): SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!

    /**
     * Filter [allGms] down to the subset that should actually run.
     * Honours both `--match` (via [shouldRun]) and `--skip` (the
     * quadruple form ; for each requested config we drop any
     * `(config, gm, _, name)` quadruple that matches).
     *
     * The skip filter is **per-config** — to know whether a given
     * `(GM, sink)` pair should run, the caller pairs this filter
     * with [shouldSkipPair].
     */
    public fun filterGms(allGms: List<GM>): List<GM> = allGms.filter { shouldRun(it.name()) }

    /**
     * Per-(GM, sink) gate — returns `true` if the pair should be
     * skipped per a `--skip <config> <src> <srcOptions> <name>`
     * quadruple. `srcOptions` is always `_` for our harness.
     */
    public fun shouldSkipPair(gmName: String, sinkTag: String): Boolean {
        if (skip.isEmpty()) return false
        var i = 0
        while (i + 3 < skip.size) {
            val cfg = skip[i]; val src = skip[i + 1]
            val opt = skip[i + 2]; val name = skip[i + 3]
            if (matchToken(cfg, sinkTag) &&
                matchToken(src, "gm") &&
                matchToken(opt, "_") &&
                matchToken(name, gmName)
            ) {
                return true
            }
            i += 4
        }
        return false
    }

    /**
     * Apply the `--match` filter to a single test name. Mirrors
     * upstream's `CommandLineFlags::ShouldSkip` (negated — upstream
     * returns `true` to *skip*, we return `true` to *run*).
     *
     * Algorithm (verbatim port from `tools/flags/CommandLineFlags.cpp`) :
     *  - Empty list → run.
     *  - For each pattern, parse leading `~` (exclude), `^` (anchor
     *    start), trailing `$` (anchor end). Match the (possibly
     *    anchored) substring against [name]. If matched : return
     *    the *opposite* of `~` (excluded ⇒ skip ; included ⇒ run).
     *  - If no pattern matched : run *unless* every pattern was
     *    `~`-prefixed (in which case the absence of a positive match
     *    is itself a "run me").
     */
    public fun shouldRun(name: String): Boolean {
        if (match.isEmpty()) return true
        var anyExclude = false
        for (raw in match) {
            var p = raw
            val exclude = p.startsWith('~')
            if (exclude) {
                anyExclude = true
                p = p.substring(1)
            }
            val anchorStart = p.startsWith('^')
            if (anchorStart) p = p.substring(1)
            val anchorEnd = p.endsWith('$')
            if (anchorEnd) p = p.dropLast(1)

            val hit = when {
                anchorStart && anchorEnd -> name == p
                anchorStart -> name.startsWith(p)
                anchorEnd -> name.endsWith(p)
                else -> name.contains(p)
            }
            if (hit) return !exclude
        }
        // No pattern matched. If every pattern was an exclusion, the
        // name escaped them all → run. Otherwise we required a positive
        // match and didn't get one → skip.
        return anyExclude && match.all { it.startsWith('~') }
    }

    /**
     * Skip-quadruple token comparator. `_` is a wildcard, `~prefix`
     * negates, anything else is a substring match (mirroring
     * upstream's `tools/flags/CommonFlagsConfig.cpp::match`).
     */
    private fun matchToken(token: String, value: String): Boolean {
        if (token == "_") return true
        if (token.startsWith('~')) {
            return !value.contains(token.substring(1))
        }
        return value.contains(token)
    }

    public companion object {

        /** Recognised `--config` tags. `null` from [resolveSinks] for anything else. */
        public val KNOWN_CONFIGS: List<String> =
            listOf("8888", "f16", "pic-8888", "pic-f16", "svg")

        /**
         * Parse `args` into a [DmCli]. Flag tokens accept both the
         * `--flag value1 value2` form (upstream style ; greedy until
         * the next `--flag`) and the `--flag=value` form (POSIX-y ;
         * single value).
         *
         * Throws [IllegalArgumentException] for malformed input
         * (unknown flag, `--skip` with a non-multiple-of-4 tail, etc).
         */
        public fun parse(args: Array<String>): DmCli {
            val configs = mutableListOf<String>()
            val match = mutableListOf<String>()
            val skip = mutableListOf<String>()
            val key = mutableListOf<String>()
            val properties = mutableListOf<String>()
            var listMissingEffects = false

            var current: MutableList<String>? = null
            for (raw in args) {
                if (raw.startsWith("--")) {
                    val (flag, inlineValue) = raw.substringAfter("--").let {
                        val eq = it.indexOf('=')
                        if (eq >= 0) it.substring(0, eq) to it.substring(eq + 1) else it to null
                    }
                    if (flag == "list-missing-effects") {
                        // Boolean flag — accepts no value ; ignore any
                        // inline `=value` form (so `--list-missing-effects=true`
                        // and `--list-missing-effects=false` both treat the
                        // flag as set, matching upstream's permissive style
                        // for boolean toggles).
                        listMissingEffects = true
                        current = null
                        continue
                    }
                    current = when (flag) {
                        "config" -> configs
                        "match", "m" -> match
                        "skip" -> skip
                        "key" -> key
                        "properties" -> properties
                        else -> throw IllegalArgumentException("unknown DM flag : --$flag")
                    }
                    if (inlineValue != null) current.add(inlineValue)
                } else {
                    if (current == null) {
                        throw IllegalArgumentException("positional value '$raw' precedes any flag")
                    }
                    current.add(raw)
                }
            }
            if (skip.size % 4 != 0) {
                throw IllegalArgumentException(
                    "--skip expects (config, src, srcOptions, name) quadruples ; got ${skip.size} values",
                )
            }
            return DmCli(
                configs = configs,
                match = match,
                skip = skip,
                key = key,
                properties = properties,
                listMissingEffects = listMissingEffects,
            )
        }
    }
}
