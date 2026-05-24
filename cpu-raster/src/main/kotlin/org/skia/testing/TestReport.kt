package org.skia.testing

import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregates per-test similarity records observed during a single Gradle
 * run, then writes `kanvas-skia/test-similarity-report.md` on JVM shutdown.
 *
 * Two entry points feed the report:
 *
 *   - [recordScore] is called automatically from [SimilarityTracker.updateScore]
 *     so every test that uses the ratchet shows up — even if it doesn't opt
 *     into the detailed comparison pipeline.
 *   - [recordDetailed] is called explicitly by tests that produce a
 *     [BitmapComparison]; it adds the tolerance, pixel counts, and
 *     per-channel max diff that aren't visible from the score alone.
 *
 * The report is regenerated end-to-end on each run; the persistent
 * best-ever scores stay in `test-similarity-scores.properties`.
 */
public object TestReport {

    private const val REPORT_FILE: String = "test-similarity-report.md"

    /**
     * Mirrors the equivalent gate on [SimilarityTracker] : when this
     * sysprop is `true`, [flush] short-circuits and the on-disk
     * `test-similarity-report.md` is left untouched. Forwarded by
     * `kanvas.ratchet.writes.disabled` in `gradle.properties` (the
     * sprint default — see #671). This closes the gap that let agent
     * test runs (e.g. `:cpu-raster:test` from a freshly-cloned worktree
     * without the gradle.properties wiring picked up) overwrite the
     * canonical report with a single-test-row truncation (see #815).
     */
    private val ratchetWriteDisabled: Boolean =
        System.getProperty("kanvas.ratchet.writes.disabled", "false").toBoolean() ||
            System.getenv("KANVAS_RATCHET_WRITES_DISABLED") == "true"

    private val records: ConcurrentHashMap<String, Record> = ConcurrentHashMap()

    @Volatile
    private var hookInstalled: Boolean = false

    public data class Record(
        val testName: String,
        var similarity: Double,
        var previousSimilarity: Double?,
        var tolerance: Int?,
        var totalPixels: Int?,
        var matchingPixels: Int?,
        var maxDiff: ChannelDiff?,
        var meanDiff: ChannelDiff?,
    )

    public fun recordScore(testName: String, similarity: Double, previous: Double?) {
        val r = records.computeIfAbsent(testName) {
            Record(it, similarity, previous, null, null, null, null, null)
        }
        synchronized(r) {
            r.similarity = similarity
            r.previousSimilarity = previous
        }
        installHook()
    }

    public fun recordDetailed(testName: String, comparison: BitmapComparison) {
        val r = records.computeIfAbsent(testName) {
            Record(
                testName = it,
                similarity = comparison.similarity,
                previousSimilarity = null,
                tolerance = comparison.tolerance,
                totalPixels = comparison.totalPixels,
                matchingPixels = comparison.matchingPixels,
                maxDiff = comparison.maxChannelDiff,
                meanDiff = comparison.meanMismatchDiff,
            )
        }
        synchronized(r) {
            r.similarity = comparison.similarity
            r.tolerance = comparison.tolerance
            r.totalPixels = comparison.totalPixels
            r.matchingPixels = comparison.matchingPixels
            r.maxDiff = comparison.maxChannelDiff
            r.meanDiff = comparison.meanMismatchDiff
        }
        installHook()
    }

    /** Force the report to be flushed now — useful for tests that exercise the writer directly. */
    @Synchronized
    public fun flush() {
        if (records.isEmpty()) return
        if (ratchetWriteDisabled) {
            // Sprint mode : the in-memory map keeps the run's measurements,
            // but the on-disk `test-similarity-report.md` is left untouched
            // so parallel agent test runs don't truncate it to a single row
            // (the failure mode that caused #815 to delete 717 lines).
            return
        }
        File(REPORT_FILE).writeText(buildMarkdown())
    }

    /** Drop all in-memory records. Test-only; production runs accumulate across the JVM lifetime. */
    public fun reset() {
        records.clear()
    }

    /** Drop a single record by name. Used by the tooling self-tests so fixtures don't leak into the final report. */
    public fun remove(testName: String) {
        records.remove(testName)
    }

    private fun installHook() {
        if (hookInstalled) return
        synchronized(this) {
            if (hookInstalled) return
            hookInstalled = true
            Runtime.getRuntime().addShutdownHook(Thread({ flush() }, "TestReport-flush"))
        }
    }

    private fun buildMarkdown(): String {
        val ordered = records.values.sortedBy { it.testName }
        val sb = StringBuilder()
        sb.append("# kanvas-skia GM similarity report\n\n")
        sb.append("Snapshot of the latest `:kanvas-skia:test` run. ")
        sb.append("Best-ever scores tracked in `test-similarity-scores.properties`. ")
        sb.append("Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` ")
        sb.append("when a test trips its threshold.\n\n")
        sb.append("| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |\n")
        sb.append("|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|\n")
        for (r in ordered) {
            val sim = String.format(Locale.ROOT, "%.2f%%", r.similarity)
            val delta = formatDelta(r)
            val tol = r.tolerance?.toString() ?: "-"
            val match = if (r.matchingPixels != null && r.totalPixels != null)
                String.format(Locale.ROOT, "%,d / %,d", r.matchingPixels, r.totalPixels)
            else "-"
            val maxD = r.maxDiff?.let { "${it.a}, ${it.r}, ${it.g}, ${it.b}" } ?: "-"
            val meanD = r.meanDiff?.let { "${it.a}, ${it.r}, ${it.g}, ${it.b}" } ?: "-"
            sb.append("| ").append(r.testName)
                .append(" | ").append(sim)
                .append(" | ").append(delta)
                .append(" | ").append(tol)
                .append(" | ").append(match)
                .append(" | ").append(maxD)
                .append(" | ").append(meanD)
                .append(" |\n")
        }
        return sb.toString()
    }

    private fun formatDelta(r: Record): String {
        val previous = r.previousSimilarity ?: return "(new)"
        val delta = r.similarity - previous
        return when {
            delta > 0.0 -> String.format(Locale.ROOT, "+%.2f%%", delta)
            delta < 0.0 -> String.format(Locale.ROOT, "-%.2f%%", -delta)
            else -> "="
        }
    }
}
