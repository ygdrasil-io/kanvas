# M38 Image-Filter Child Pre-Pass Closeout

Date: 2026-05-28
Milestone: M38 -- Image-filter Child Pre-pass
Epic: GRA-164
Closeout ticket: GRA-184

## Outcome

M38 is closed. The selected `SkImageFilters.Crop(input = nonNull)` case is no
longer an accepted unsupported image-filter inventory row for the SimpleOffset
fixture shape. The delivered support is intentionally bounded to:

```text
Crop(kDecal, input = Offset(dx, dy, input = null))
```

The implementation materialises the child filter into a WebGPU scratch texture,
then applies the final Crop(kDecal) composite against that scratch. This does
not implement a general image-filter DAG compiler.

## Delivered Tickets

| Ticket | Result | Evidence |
|---|---|---|
| GRA-174 | Designed the selected pre-pass shape, diagnostics, and acceptance policy. | `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-design.md` |
| GRA-181 | Implemented the WebGPU child pre-pass and removed selected SimpleOffset rows from unsupported inventory. | `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-implementation.md` |
| GRA-182 | Updated smoke/inventory policy and promoted `SimpleOffsetImageFilterWebGpuTest`. | `reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md` |
| GRA-183 | Added dashboard row with CPU/GPU artifacts, diffs, route JSON, pre-pass diagnostics, and stats. | `reports/wgsl-pipeline/2026-05-28-m38-image-filter-dashboard-scene.md` |
| GRA-184 | Closed the milestone and updated active post-MVP backlog status. | This report |

## Before And After Counts

| Signal | Before M38 | After M38 |
|---|---:|---:|
| Full inventory total classified rows | 100 | 98 |
| `unsupported-image-filter` | 2 | 0 |
| `image-filter.crop-input-nonnull-prepass-required` selected SimpleOffset rows | 2 | 0 |
| `expected-unsupported-diagnostic` | 50 | 50 |
| `coverage.edge-count-exceeded` | 46 | 46 |
| `coverage.stroke-outline-edge-count-exceeded` | 4 | 4 |
| `adapter-skip` | 48 | 48 |
| `similarity-regression` | 0 | 0 |
| `unexpected-exception` | 0 | 0 |

No similarity floor was reduced to hide image-filter drift.

## Promoted Evidence

Required smoke now includes:

```text
org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest
```

Dashboard scene:

```text
reports/wgsl-pipeline/scenes/data/scenes.json#crop-image-filter-nonnull-prepass
build/reports/wgsl-pipeline-scenes/index.html
```

Dashboard artifacts:

```text
reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/
```

Promoted route diagnostics:

- CPU: `cpu.image-filter.crop-nonnull.offset-oracle`
- GPU: `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite`
- Pre-pass: `LayerCompositeDraw.materializeToIntermediate` into `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch`

## Remaining Image-Filter Scope

The following remain out of scope for M38 and must keep stable diagnostics or
tracked follow-up tickets if they appear in inventory:

| Remaining category | Current policy | Recommendation |
|---|---|---|
| Non-selected `Crop(input = nonNull)` graph shapes | Use `image-filter.crop-input-nonnull-prepass-required` until a bounded implementation exists. | Add one graph family at a time with CPU/GPU/dashboard evidence. |
| General image-filter DAG compilation | Not implemented and not a goal. | Avoid SkSL/Skia DAG compiler ports; register bounded Kotlin/WGSL implementations. |
| Compose/Blur/DropShadow/Matrix/Tile/Magnifier with non-null nested inputs beyond accepted chains | Supported only where existing tests prove materialisation; otherwise stable refusal. | Promote via focused tickets with route JSON and inventory deltas. |
| Font/codec-dependent image-filter scenes | Dependency-gated. | Wait for real font/codec deliveries; do not add substitutes just to clear inventory. |

## Validation

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk git diff --check
```

Results:

| Command | Result |
|---|---|
| `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest` | Pass; required smoke includes `SimpleOffsetImageFilterWebGpuTest`. |
| `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` | Expected non-zero full inventory: `684 tests completed, 50 failed, 48 skipped`; classification emitted. |
| `rtk ./gradlew --no-daemon pipelineSceneDashboard` | Pass; export written to `build/reports/wgsl-pipeline-scenes/index.html`. |
| `rtk git diff --check` | Pass. |

Expected inventory behavior remains non-zero because Path AA coverage breadth
and adapter placeholders are intentionally inventory-only. The required M38
image-filter signal is:

```text
total=98
expected-unsupported-diagnostic=50
unsupported-image-filter=0
adapter-skip=48
similarity-regression=0
unexpected-exception=0
coverage.edge-count-exceeded=46
coverage.stroke-outline-edge-count-exceeded=4
```

## Risks And Follow-Up

- The pre-pass is bounded to SimpleOffset's Crop(kDecal)+Offset(null) shape.
- Broader nested image-filter graphs still need explicit design, diagnostics,
  tests, and dashboard rows before promotion.
- Required smoke cost is controlled by promoting only the WebGPU SimpleOffset
  fixture; cross-backend remains full-inventory parity evidence.
- M39 should focus on route convergence for more integration scenes, not on
  widening image-filter scope without per-family evidence.
