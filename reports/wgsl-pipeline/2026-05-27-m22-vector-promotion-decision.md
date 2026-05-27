# M22 Vector Promotion Decision

Date: 2026-05-27
Linear: GRA-48
Evidence source: GRA-47 / PR #1137

## Decision

`rejected-remains-scalar-default`

The Java 25 Vector solid-rect kernel remains available only through explicit
force/accepted-gate selection. It is not promoted to default selection because
the measured speedup on the named reference machine is below the required
`1.5x` default-promotion gate.

## Reference Machine

| Field | Value |
|---|---|
| Machine | Mac OS X 26.5 aarch64 |
| JDK | 25.0.1+8-LTS |
| Vendor | Eclipse Adoptium |
| Image | 2048x2048 |
| Warmups | 5 |
| Iterations | 20 |
| Allocation iterations | 20 |

## Benchmark Commands

```text
rtk ./gradlew --no-daemon :render-pipeline:cpuVectorPilotBenchmark
rtk ./gradlew --no-daemon :render-pipeline:cpuVectorAllocationBenchmark
```

## Benchmark Results

| Command | Scalar median/min | Vector median/min | Speedup | Allocation metric | Scalar allocation | Vector allocation | Decision |
|---|---:|---:|---:|---|---:|---:|---|
| `:render-pipeline:cpuVectorPilotBenchmark` | 0.703 ms / 0.454 ms | 0.556 ms / 0.464 ms | 1.264x | `threadAllocatedBytesPerExecute` | 16777440.000 B/op | 16777680.000 B/op | rejected |
| `:render-pipeline:cpuVectorAllocationBenchmark` | 0.546 ms / 0.445 ms | 0.482 ms / 0.424 ms | 1.132x | `threadAllocatedBytesPerExecute` | 16777440.000 B/op | 16777680.000 B/op | rejected |

Promotion target: `>= 1.5x` scalar speedup with allocation evidence.

Canonical hot-loop allocation target: `0.0 B/op`.

The non-zero allocation is accepted as evidence, not as a promotion result:
`CpuScalarPipelineExecutor.execute` allocates the destination `IntArray` /
`PixelBuffer` per benchmark operation. That allocation is outside the
per-pixel fill kernel; the promoted hot-loop target remains `0.0 B/op`.

## Exact Allocation-Aware Output

```text
GRA-28 Java 25 Vector API pilot benchmark
machine=Mac OS X 26.5 aarch64
jdk=25.0.1+8-LTS vendor=Eclipse Adoptium
image=2048x2048 warmups=5 iterations=20
scalarKernel=cpu.scalar.solid_src_over_clear medianMs=0.546 minMs=0.445
vectorKernel=java25.vector.solid_src_over_clear medianMs=0.482 minMs=0.424
vectorDiagnostics=Vector API force-selected: java25.vector.solid_src_over_clear lanes=4 gate=solid_src_over_clear/java25/reference-v1
speedup=1.132
scalarGcCollections=2 scalarGcTimeMillis=1
vectorGcCollections=2 vectorGcTimeMillis=2
allocationMetric=threadAllocatedBytesPerExecute
allocationTargetBPerOp=0.0
allocationIterations=20
scalarAllocBytesPerOp=16777440.000 units=B/op supported=true
vectorAllocBytesPerOp=16777680.000 units=B/op supported=true
allocationException=CpuScalarPipelineExecutor.execute allocates the destination IntArray/PixelBuffer per benchmark operation; promoted hot-loop target remains 0.0 B/op inside the fill kernel.
decision=rejected
```

## PM Summary

The vector path is functionally valid and measurable, but it is not the default
path for M22. Scalar remains the default. Follow-up optimization work is needed
before reconsidering default promotion:

- eliminate or isolate benchmark-operation destination allocation from future
  hot-loop measurements;
- improve the Java 25 Vector kernel enough to meet `>= 1.5x` on the reference
  machine;
- rerun the allocation-aware benchmark and publish a new decision report.

## Verification

- PR #1137: Raster tests (ubuntu) SUCCESS.
- PR #1137: GPU tests (macos) SKIPPED under the current GPU gate waiver.
- PR #1137: merge state CLEAN before squash merge.
- Independent review subagent: `APPROVE_MERGE`.
