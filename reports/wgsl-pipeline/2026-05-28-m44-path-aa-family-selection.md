# M44-A Path AA family selection

M44 selects the primitive stroke family for the first real Path AA rendered GPU
promotion:

```text
StrokeRectGM
StrokeCircleGM
```

This is a narrow promotion target, not a broad Path AA renderer. It keeps the
WebGPU AA edge budget at 256 and leaves unrelated broad suites explicitly
expected unsupported.

## Source tests

Selected inventory rows:

- `org.skia.gpu.webgpu.StrokeRectWebGpuTest#StrokeRectGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.StrokeCircleWebGpuTest#StrokeCircleGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.crossbackend.StrokeRectCrossBackendTest#StrokeRectGM matches reference on raster and GPU backends()`
- `org.skia.gpu.webgpu.crossbackend.StrokeCircleCrossBackendTest#StrokeCircleGM matches reference on raster and GPU backends()`

Expected inventory effect if M44-B succeeds: remove exactly these four rows from
`coverage.edge-count-exceeded`, reducing the M37/M44 edge-budget bucket from 50
to 46. If implementation proves only a subset is technically sound, the report
must list the exact rows moved and keep the rest expected unsupported.

## Acceptance thresholds

| GM | GPU floor | Cross-backend raster floor | Cross-backend GPU floor |
| --- | ---: | ---: | ---: |
| `StrokeRectGM` | 95.91% | 93.50% | 95.91% |
| `StrokeCircleGM` | 91.76% | 90.00% | 91.76% |

Thresholds come from the M37 selection report and must not be lowered merely to
clear inventory. Any threshold change requires before/after rendered artifacts,
route diagnostics, and a separate rationale.

## Route expectations

The implementation ticket must expose a deterministic route such as:

```text
webgpu.coverage.path-aa-stroke-primitive
```

Required route diagnostics:

- selected family: `stroke-rect` or `stroke-circle`;
- stroke width/cap/join facts used by the bounded route;
- `fallbackReason=none` for promoted rows;
- `coverage.edge-count-exceeded` retained for out-of-scope rows;
- no CPU readback or CPU fallback may be reported as WebGPU support.

## CPU oracle and dashboard policy

- CPU/reference oracle remains the existing raster/reference PNG oracle for the
  source GM tests.
- GPU evidence must include rendered output, diff, route JSON, and stats.
- Dashboard evidence should add or update a row named `path-aa-stroke-primitive`
  or an equivalent stable scene id.
- The dashboard row must link selected source tests and preserve unsupported
  Path AA boundary evidence.

## Rejected alternatives

| Alternative | Rows | Reason |
| --- | ---: | --- |
| Arc stroke/hairline | 9 | Higher complexity from curve subdivision, caps, and hairline behavior. Queue after primitive strokes. |
| General stroke/dash | 13 | Too heterogeneous; dash/cap/join expansion can hide unrelated behavior. Split later. |
| Fill/convex/path packs | 19 | Broad fixtures mix fill rules and shape families; too large for first promotion. |
| Filter/shader over path | 4 | Composition should wait until the base coverage route is reliable. |
| Benchmark stress | 1 | Performance signal only; not a support claim. |

## Out of scope

- Raising `SkWebGpuDevice.MAX_AA_EDGES` or changing the 256-edge ADR.
- Promoting broad Path AA packs, dash/cap/join families, or path-heavy
  benchmark rows.
- Treating CPU fallback as WebGPU support.
- Porting Ganesh, Graphite, SkSL compiler behavior, or a general path renderer.

## Validation

```bash
rtk git diff --check
```
