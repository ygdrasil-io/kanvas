# M35 Full GPU Inventory Audit

Date: 2026-05-27
Linear: GRA-115
Branch: gra-115-final-inventory-audit
Base evidence commit: `7d92391490e59746257297de962f7a6474922307`

## Goal

Run the final full GPU inventory audit after M33 Path AA and M34 image-filter closeout, resolve release-readiness blockers, and prove that every remaining inventory row is either expected unsupported, dependency-gated, or adapter-skipped.

## Blocker Fixed During Audit

The M34 inventory baseline had four `unexpected-exception` rows in `org.skia.gpu.webgpu.SaveLayerTest` for shader-composited layer blend modes:

| Test | Baseline observed behavior |
|---|---|
| `saveLayer with kScreen blendMode lightens background()` | returned source-only blue instead of magenta |
| `saveLayer with kLighten blendMode picks brighter channel()` | returned source-only green instead of yellow |
| `saveLayer with kDifference blendMode subtracts colors()` | returned source-only white instead of cyan |
| `saveLayer with kMultiply blendMode multiplies layer with background()` | returned source-only gray instead of dark red |

Root cause: `NonNativeBlendLayerCompositeDraw` still packed only 32 floats for the snapshot/composite uniform after `layer_composite.wgsl` had grown to the 56-float `LAYER_COMPOSITE_UNIFORM_FLOATS` layout. The snapshot pass uses `layer_composite.wgsl`, so the trailing matrix/image-filter identity fields must be explicitly written as zero for a stable copy of the destination texture.

Fix: both the snapshot and blend-composite uniform payloads now allocate `FloatArray(LAYER_COMPOSITE_UNIFORM_FLOATS)`, leaving all trailing identity slots deterministically zero.

Changed file:

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`

## Full Inventory Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Result: expected Gradle task failure because `:gpu-raster:test` includes classified inventory failures. The classification report was generated successfully:

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`

JUnit summary from the task:

| Metric | Value |
|---|---:|
| Tests | 683 |
| Failed test executions | 52 |
| Skipped | 48 |
| Classified inventory records | 100 |

## Category Summary

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 0 |

## GRA-115 Acceptance Checks

| Check | Result | Evidence |
|---|---|---|
| Latest inventory after M33/M34 captured | Pass | `total=100` classification rows from `gpuInventoryTest` |
| M32 bitmap/image-rect similarity regressions remain zero | Pass | `similarity-regression=0` |
| GRA-100 SaveLayer kScreen unexpected exception absent | Pass | `unexpected-exception=0`; `SaveLayerTest` has no classified rows |
| M33 Path AA edge-budget rows classified | Pass | `expected-unsupported-diagnostic=50`, reason `coverage.edge-count-exceeded` |
| M34 Crop(input = nonNull) rows classified | Pass | exactly 2 rows, reason `image-filter.crop-input-nonnull-prepass-required` |
| No unclassified or untracked unexpected failures | Pass | `unexpected-exception=0`, `adapter-missing=0` |
| No unresolved similarity regression | Pass | `similarity-regression=0` |

## Crop(input = nonNull) Rows

The only image-filter MVP limitation rows are still the two M34-classified `SimpleOffsetImageFilter*` cases:

| Test | Classification | Reason | Source XML |
|---|---|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()` | `unsupported-image-filter` | `image-filter.crop-input-nonnull-prepass-required` | `TEST-org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest.xml` |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()` | `unsupported-image-filter` | `image-filter.crop-input-nonnull-prepass-required` | `TEST-org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest.xml` |

Interpretation: this remains the accepted M34 MVP limitation. It is blocked on render-to-texture pre-pass implementation evidence and is not smoke-eligible while the stable reason remains inventory-only.

## Path AA / Coverage Rows

All 50 failure rows in `expected-unsupported-diagnostic` carry the stable reason `coverage.edge-count-exceeded`.

Interpretation: these are the M33 Path AA edge-budget rows. They remain expected unsupported breadth gaps owned by coverage strategy promotion/fallback work, not release blockers for the WGSL MVP RC.

## SaveLayer Blend Verification

Command:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.SaveLayerTest'
```

Result: passed.

| Metric | Value |
|---|---:|
| `SaveLayerTest` tests | 24 |
| Failures | 0 |
| Skipped | 0 |

The previously unexpected `kScreen`, `kLighten`, `kDifference`, and `kMultiply` cases now pass both in the targeted run and inside the full inventory run.

## Classification Guard Verification

Command:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest'
```

Result: passed.

Covered guard evidence:

- machine-readable and PM-readable inventory artifacts still write correctly;
- M33 edge-budget diagnostics remain expected unsupported and unknown coverage codes fail closed;
- M34 Crop(input = nonNull) rows remain exact and listed under expected unsupported image-filter inventory;
- required inventory categories are still classified from JUnit XML.

## Shader Validation

Command:

```bash
rtk ./gradlew --no-daemon :gpu-raster:wgslValidateAll
```

Result: passed.

`layer_composite.wgsl` parsed with `success=true` and zero diagnostics. `layer_composite_blend.wgsl` parsed with `success=true`; its single reflection-skip diagnostic is the existing accepted diagnostic.

## Diff Hygiene

Command:

```bash
rtk git diff --check
```

Result: passed.

## Release Readiness Interpretation

The final full GPU inventory has no unclassified failures, no unresolved similarity regressions, and no untracked unexpected exceptions. The remaining inventory is limited to:

- 50 expected unsupported coverage breadth rows under `coverage.edge-count-exceeded`;
- 2 accepted M34 image-filter limitation rows under `image-filter.crop-input-nonnull-prepass-required`;
- 48 adapter-dependent placeholder skips.

This satisfies GRA-115 and leaves M35 ready for docs/readiness sync and PM evidence closeout.
