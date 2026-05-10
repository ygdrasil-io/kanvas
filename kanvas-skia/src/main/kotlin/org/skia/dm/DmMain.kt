package org.skia.dm

import org.skia.tests.GM

/**
 * Entry helper for a CLI-driven DM run — D4.4.
 *
 * Composes the three pieces D4.1 / D4.2 / D4.3 / D4.4 deliver into a
 * single function : parse `args`, resolve sinks, filter GMs, run, and
 * return the final [Report]. Mirrors the top-level loop in upstream's
 * [`dm/DM.cpp`](https://github.com/google/skia/blob/main/dm/DM.cpp)
 * but stops short of providing a `main` — the caller decides whether
 * to print the report to stdout, write it to a file, etc.
 *
 * The Kotlin GM registry isn't auto-discoverable (no SPI scan today),
 * so [allGms] is taken as an explicit list. A typical caller :
 *
 * ```kotlin
 * fun main(args: Array<String>) {
 *     val report = DmMain.runFromArgs(
 *         args = args,
 *         allGms = listOf(BigRectGM(), SimpleRectGM(), …),
 *     )
 *     println(report.summary())
 *     System.out.write(report.toJson().toByteArray())
 * }
 * ```
 *
 * Behaviour :
 *  - Empty `--config` → fail loudly (we don't pick a default ; the
 *    user is asking us to run nothing). Mirrors upstream's
 *    "no config → no work" diagnostic.
 *  - Unknown `--config` tag → throw with a message listing known
 *    tags. Upstream would `exit(1)` ; in Kotlin we let the
 *    `IllegalStateException` propagate so the caller can decide.
 *  - `--match` filters drop their non-matches before any sink runs ;
 *    `--skip` quadruples filter `(GM, sink)` pairs at run time so a
 *    GM can be excluded for one config and kept for another.
 */
public object DmMain {

    /**
     * One-shot pipeline : parse [args], resolve sinks, filter [allGms],
     * run, and return the [Report]. Throws on unknown configs / bad
     * flag syntax — caller decides whether to exit-on-error.
     */
    public fun runFromArgs(args: Array<String>, allGms: List<GM>): Report {
        val cli = DmCli.parse(args)
        check(cli.configs.isNotEmpty()) { "DM : no --config given (try --config 8888 f16)" }

        val resolved = cli.resolveSinks()
        val unknown = cli.configs.zip(resolved).filter { it.second == null }.map { it.first }
        check(unknown.isEmpty()) {
            "DM : unknown --config tag(s) : $unknown — known : ${DmCli.KNOWN_CONFIGS}"
        }
        val sinks = resolved.filterNotNull()

        val gms = cli.filterGms(allGms)

        val report = Runner(
            sinks = sinks,
            gms = gms,
            properties = pairUp(cli.properties),
            key = pairUp(cli.key),
        ).run().applySkip(cli)

        // Phase D2.6 — surface unregistered SkSL hashes when the
        // user asks for them. Prints to stderr (so the report
        // payload on stdout stays clean) ; exits with code 0 either
        // way. The set is empty most of the time — these only
        // appear when a GM passes a SkSL string the project hasn't
        // hand-ported yet.
        if (cli.listMissingEffects) {
            val missing = report.missingRuntimeEffectHashes()
            if (missing.isEmpty()) {
                System.err.println("[DM] No missing runtime-effect hashes — all SkSL programs in the run are registered.")
            } else {
                System.err.println("[DM] ${missing.size} missing runtime-effect hash(es) — register an impl in SkRuntimeEffectDispatch :")
                for (hash in missing.sorted()) {
                    System.err.println("  $hash")
                }
            }
        }

        return report
    }

    /**
     * Apply post-run [DmCli.shouldSkipPair] filtering. The Runner
     * already builds one record per (GM × sink) ; we drop the records
     * whose pair was supposed to be skipped per `--skip`. Doing the
     * filter post-Runner keeps the Runner generic — `--skip` is a
     * concern of the CLI layer, not the matrix driver.
     */
    private fun Report.applySkip(cli: DmCli): Report {
        if (cli.skip.isEmpty()) return this
        val keptPassed = passed.filterNot { cli.shouldSkipPair(it.gmName, it.sinkTag) }
        val keptFailed = failed.filterNot { cli.shouldSkipPair(it.gmName, it.sinkTag) }
        return copy(passed = keptPassed, failed = keptFailed)
    }

    /**
     * Convert a flat `--key k1 v1 k2 v2` list into a [Map]. Odd-length
     * input is rejected — `--key` and `--properties` always come in
     * pairs per upstream's docstring ("Space-separated key/value
     * pairs to add to JSON identifying this run.").
     */
    private fun pairUp(flat: List<String>): Map<String, String> {
        require(flat.size % 2 == 0) { "key/properties values must come in pairs ; got ${flat.size}" }
        val result = LinkedHashMap<String, String>(flat.size / 2)
        var i = 0
        while (i + 1 < flat.size) {
            result[flat[i]] = flat[i + 1]
            i += 2
        }
        return result
    }
}
