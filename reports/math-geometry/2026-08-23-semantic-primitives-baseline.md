# Semantic geometry primitives baseline — 2026-08-23

## Scope and environment

- Source base commit: `df2f4eeb0c128b6b4292bcc8ecae446506224e1c`. The benchmark module and this report are introduced together by the following task commit, so that commit cannot self-reference its own hash.
- OS: macOS 26.6.2 (build 25G83), arm64.
- JVM: Eclipse Temurin OpenJDK 25.0.1+8-LTS, 64-bit Server VM.
- Node.js used by Gradle: v24.10.0 (`/Users/chaos/.gradle/nodejs/node-v24.10.0-darwin-arm64/bin/node`).
- Kotlin: 2.4.0.
- kotlinx-benchmark: 0.4.17; JMH: 1.37.
- Generated immutable representation: `FINAL_CLASS`.

This is a single local baseline. It is not a correctness threshold, a cross-runtime comparison, or a performance gate.

## Commands and configuration

```text
rtk ./gradlew :math:geometry-benchmarks:jvmBenchmark
rtk ./gradlew :math:geometry-benchmarks:jsBenchmark
rtk ./gradlew :math:geometry-benchmarks:measureJvmGeometryAllocations
```

Both timing targets used five 300 ms warmup iterations followed by ten 300 ms measurement iterations. The JVM allocation probe used five warmup rounds, then measured 1,000,000 calls to `Matrix3x3F32.transform(Point2F32)` with `com.sun.management.ThreadMXBean`.

## Observed timing output

The values below are the summaries emitted by the two harnesses. A batch operation transforms 1,024 points into existing mutable destinations; a mutable accumulation operation performs 1,024 additions.

| Target | Benchmark | Observed score | Harness error |
|---|---|---:|---:|
| JVM | `mutableVectorAccumulation` | 1,079,910.769 ops/s | ±115,695.099 ops/s |
| JVM | `transformPointBatch` | 599,276.332 ops/s | ±17,224.076 ops/s |
| JVM | `transformSinglePoint` | 712,479,414.326 ops/s | ±14,369,605.909 ops/s |
| JVM | `transformSingleVector` | 776,632,874.541 ops/s | ±15,810,808.611 ops/s |
| Node.js | `mutableVectorAccumulation` | 273,432.790 ops/sec | ±2,081.851 ops/sec |
| Node.js | `transformPointBatch` | 283,180.421 ops/sec | ±15,408.889 ops/sec |
| Node.js | `transformSinglePoint` | 108,578,115.018 ops/sec | ±1,527,012.664 ops/sec |
| Node.js | `transformSingleVector` | 109,425,087.710 ops/sec | ±2,503,098.360 ops/sec |

Raw harness files from this run:

- `math/geometry-benchmarks/build/reports/benchmarks/main/2026-08-23T19.36.52.210754/jvm.json`
- `math/geometry-benchmarks/build/reports/benchmarks/main/2026-08-23T19.38.57.739982/js.json`

The `build/` files are local measurement artifacts and are not versioned; this report preserves their emitted summaries.

## Observed JVM allocation output

`math/geometry-benchmarks/build/reports/allocations.json` contained:

```json
{
  "representation": "FINAL_CLASS",
  "operation": "Matrix3x3F32.transform(Point2F32)",
  "iterations": 1000000,
  "allocatedBytes": 0,
  "allocatedBytesPerOperation": 0.0
}
```

The zero is the counter value observed in this process after JIT warmup. It is not an allocation guarantee. On a JVM without supported/enabled per-thread allocation counters, the probe writes `"status":"unsupported"` instead of numeric zero values.

## Limits

- One host and one run were measured; no repeatability or host normalization study was performed.
- JVM and Node.js use different harness implementations and execution engines; their values are not treated as a direct comparison.
- The single-transform results may include runtime optimization effects such as inlining, escape analysis, or scalar replacement.
- No MFVC backend was compiled or measured, so no MFVC row or comparison is present.
- No timing or allocation value is a pass/fail threshold.
- The normal `check` and `build` graphs do not include the timing execution tasks or allocation probe. Kotlin/JS package metadata for the registered benchmark compilation still participates in Gradle's global npm resolution.

## Non-claim

Non-claim: this baseline does not establish that the generated representation
is allocation-free or faster than a future multi-field value class backend.
