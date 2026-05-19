package org.skia.gpu.webgpu.benchmarks

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Per-backend statistical summary over a sample of timings.
 *
 * The methodology mirrors JMH's `Mode.AverageTime` reporting :
 *  - **min** : best-case (cold-friendly lower bound).
 *  - **avg** : arithmetic mean ; the headline number.
 *  - **median** : robust against outliers in the tail.
 *  - **p95** : 95th-percentile sample ; cf. the "ignore the worst 5%"
 *    convention. Index = `ceil(0.95 * n) - 1` on the sorted array
 *    (the same definition the pre-G7.x baseline used).
 *  - **stddev** : sample standard deviation (n - 1 normalisation,
 *    matching JMH's `Variance` reporting).
 *  - **relErrPct** : `stddev / avg * 100`. JMH-equivalent of the
 *    "relative error %" line — > 10 % is noisy, > 30 % is unreliable.
 *
 * All values stored in **milliseconds** (the input nanos are converted
 * on construction, not on access — keeps the rendering code simple).
 *
 * Why these stats and not just `avg ± stddev` ? The GPU pipeline has
 * a bimodal distribution on cold-from-cache vs hot iterations ; the
 * median + p95 catches that the mean is hiding.
 */
public data class BenchStats(
    val n: Int,
    val minMs: Double,
    val avgMs: Double,
    val medianMs: Double,
    val p95Ms: Double,
    val stddevMs: Double,
    val relErrPct: Double,
) {
    public companion object {
        public fun fromNanos(samplesNs: List<Long>): BenchStats {
            require(samplesNs.isNotEmpty()) { "empty sample" }
            val ms = samplesNs.map { it / 1_000_000.0 }
            return fromMillis(ms)
        }

        public fun fromMillis(samplesMs: List<Double>): BenchStats {
            require(samplesMs.isNotEmpty()) { "empty sample" }
            val sorted = samplesMs.sorted()
            val n = sorted.size
            val min = sorted.first()
            val avg = sorted.average()
            val median = if (n % 2 == 1) {
                sorted[n / 2]
            } else {
                (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
            }
            val p95Idx = (ceil(0.95 * n).toInt() - 1).coerceIn(0, n - 1)
            val p95 = sorted[p95Idx]
            // Sample stddev with (n - 1) divisor — matches the JMH
            // `variance` line. When n == 1 we report 0 stddev rather
            // than NaN (the caller will see relErr = 0 and know the
            // sample is too small to estimate noise).
            val stddev = if (n > 1) {
                val mean = avg
                val sumSq = sorted.sumOf { (it - mean) * (it - mean) }
                sqrt(sumSq / (n - 1).toDouble())
            } else {
                0.0
            }
            val relErr = if (avg > 0.0) stddev / avg * 100.0 else 0.0
            return BenchStats(
                n = n,
                minMs = min,
                avgMs = avg,
                medianMs = median,
                p95Ms = p95,
                stddevMs = stddev,
                relErrPct = relErr,
            )
        }
    }
}

/**
 * Per-phase statistical summary across a sample of [PhaseTimings].
 *
 * Each phase gets its own [BenchStats] so the relative contribution
 * of (e.g.) `tessellate` vs `readback` is reported per-phase rather
 * than collapsed to a single mean. The G8 trigger reads the
 * `tessellate.avgMs / total.avgMs` ratio directly.
 */
public data class PhaseStats(
    val setup: BenchStats,
    val tessellate: BenchStats,
    val submit: BenchStats,
    val readback: BenchStats,
    val similarity: BenchStats,
    val total: BenchStats,
) {
    public companion object {
        public fun from(samples: List<PhaseTimings>): PhaseStats {
            require(samples.isNotEmpty()) { "empty sample" }
            return PhaseStats(
                setup = BenchStats.fromNanos(samples.map { it.setupNs }),
                tessellate = BenchStats.fromNanos(samples.map { it.tessellateNs }),
                submit = BenchStats.fromNanos(samples.map { it.submitNs }),
                readback = BenchStats.fromNanos(samples.map { it.readbackNs }),
                similarity = BenchStats.fromNanos(samples.map { it.similarityNs }),
                total = BenchStats.fromNanos(samples.map { it.totalNs }),
            )
        }
    }
}
