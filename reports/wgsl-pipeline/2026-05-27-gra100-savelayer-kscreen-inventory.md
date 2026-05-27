# GRA-100 SaveLayer kScreen inventory classification

Date: 2026-05-27

Issue: GRA-100

## Scope

GRA-100 was opened from the M32 bitmap/image-rect closeout because the final GPU inventory recorded one non-M32 `unexpected-exception`:

- Test: `org.skia.gpu.webgpu.SaveLayerTest#saveLayer with kScreen blendMode lightens background()`
- Observed failure in the closeout inventory: expected magenta at the layer center but got blue.
- Constraint: do not change bitmap/image-rect smoke, floor, or closeout status.

## Classification

The current branch based on `origin/master` after the M32 closeout no longer reproduces the `SaveLayerTest` failure.

Classification: accepted inventory-only evidence.

Rationale:

- The focused `SaveLayerTest` path passes locally, including `saveLayer with kScreen blendMode lightens background()`.
- The full GPU inventory no longer contains `SaveLayerTest#saveLayer with kScreen blendMode lightens background()`.
- The inventory category summary reports `unexpected-exception=0`.
- No runtime, shader, smoke, or M32 image-rect floor change is required.

## Validation

Commands run from `/Users/chaos/.codex/worktrees/125c/kanvas`:

```text
rtk git diff --check
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.SaveLayerTest.saveLayer with kScreen blendMode lightens background'
```

Result: passed.

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.SaveLayerTest'
```

Result: passed. All 24 `SaveLayerTest` tests passed, including the `kScreen` case.

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
```

Result: expected non-blocking inventory failure only.

Inventory summary:

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 0 |

The command still exits non-zero because the inventory intentionally includes expected unsupported WebGPU breadth gaps, but the GRA-100 signal is gone.

## Outcome

GRA-100 does not require a code fix. The original closeout signal is resolved by current inventory evidence:

- `SaveLayerTest` is green.
- `unexpected-exception` is zero.
- M32 bitmap/image-rect status remains unchanged.
