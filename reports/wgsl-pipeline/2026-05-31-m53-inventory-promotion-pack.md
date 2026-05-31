# M53 Inventory Promotion Pack Evidence

Date: 2026-05-31
Milestone: M53
Linear epic: GRA-309
Tickets: GRA-311 through GRA-315

## Scope

M53 promotes the selected GM Feature Promotion Pack v2 into generated
dashboard evidence. The rows are derived from existing generated scene
evidence, but each row has its own GM inventory id, route semantics, fallback
policy, tags, and M53 derivation contract.

This pack does not claim broad Skia GM parity, broad image-filter DAG support,
broad Path AA support, or dependency-gated font/codec/emoji/shaping/SDF/LCD/
glyph-mask support.

## Promotion Path

`pipelineM53InventoryPromotionPack` reads
`reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json` and
materializes generated rows and artifacts under
`build/reports/wgsl-pipeline-m53-generated/`.

`pipelineGeneratedSceneExport` merges the base generated rows, M52 generated
rows, and M53 generated rows before `pipelineSceneDashboard` and
`pipelineSceneDashboardGate` validate the dashboard.

## Promoted Rows

| Scene id | Inventory id | Family | Status | Contract |
|---|---|---|---|---|
| `m53-degenerate-gradient-linear` | `skia-gm-gradientsdegenerate` | gradients | `pass` | Bounded degenerate linear-gradient subset through CPU gradient oracle and generated WebGPU gradient route. |
| `m53-sweep-gradient-clamp` | `skia-gm-sweepgradient` | gradients | `pass` | Sweep-gradient kClamp path subset reuses the existing CPU/GPU/ref/diff/stats evidence from `sweep-gradient-path-clamp`; two-point conical remains rejected below. |
| `m53-bitmap-premul-alpha` | `skia-gm-bitmappremul` | bitmap/image | `pass` | Premultiplied alpha bitmap sampling subset through CPU oracle and WebGPU bitmap route. |
| `m53-bitmap-filter-linear-subset` | `skia-gm-bitmapfilters` | bitmap/image | `pass` | Bounded bitmap filter subset through WebGPU image sampling route. |
| `m53-arithmode-bounded-blend` | `skia-gm-arithmode` | blend/color-filter | `pass` | Arithmetic blend subset with bounded coefficients. |
| `m53-mode-color-filter-screen` | `skia-gm-modecolorfilters` | blend/color-filter | `pass` | Mode color-filter Screen subset through generated color-filter/blend route. |
| `m53-badpaint-sanitized-state` | `skia-gm-badpaint` | blend/color-filter | `pass` | Sanitized paint-state subset; undefined paint behavior remains out of scope. |
| `m53-clipshader-rect-subset` | `skia-gm-clipshader` | clip/transform/saveLayer | `pass` | Rectangular clip-shader subset with explicit CPU/GPU clip route diagnostics. |
| `m53-convex-poly-clip` | `skia-gm-convexpolyclip` | clip/transform/saveLayer | `pass` | Bounded convex polygon clip subset. |
| `m53-complexclip-boundary-refusal` | `skia-gm-complexclip` | clip/transform/saveLayer | `expected-unsupported` | Complex path clip remains an explicit unsupported boundary with `coverage.complex-clip-path-unsupported`. |
| `m53-imageblur-bounded-prepass` | `skia-gm-imageblur` | bounded image-filter | `pass` | One bounded blur/prepass-style image-filter contract using existing layer ownership semantics. |
| `m53-imagefilters-cropped-boundary` | `skia-gm-imagefilterscropped` | bounded image-filter | `expected-unsupported` | Cropped image-filter boundary keeps stable `image-filter.crop-input-nonnull-prepass-required` refusal. |

## Counters

| Signal | Count |
|---|---:|
| M53 selected candidates | 12 |
| M53 promoted generated rows | 12 |
| M53 promoted `pass` rows | 10 |
| M53 promoted `expected-unsupported` rows | 2 |
| Feature families covered | 5 |
| New `tracked-gap` rows | 0 |
| New `fail` rows | 0 |

## Gate And Bundle Metadata

`pipelinePmBundle` now exposes `m53InventoryPromotion` in `manifest.json` with
selected/promoted/rejected counts, promoted row details, source reports, and
derivation contracts. The PM bundle validation fails a M53 row that lacks
`inventoryId`, `generation.sourceReport`, `generation.derivationContract`,
route diagnostics, required artifacts, `fallbackReason=none` for `pass`, or a
stable non-`none` fallback reason for `expected-unsupported`.

## Rejected Or Deferred

| Inventory id | Reason |
|---|---|
| `skia-gm-duckyyuvblend` | YUV/codec image decode dependency remains gated. |
| `skia-gm-animatedimage` | Animated image decoding remains dependency-gated. |
| `skia-gm-runtimeimagefilter` | Runtime image-filter support needs a descriptor-backed slice; no SkSL/VM rebuild. |
| `skia-gm-shadertext3` | Text/glyph rendering remains dependency-gated. |
| `skia-gm-dftext` | SDF glyph backend remains gated. |
| `skia-gm-gradients2ptconical` | Two-point conical gradient remains outside the sweep-gradient clamp scene contract. |
| `skia-gm-dashcubics` | Broad dashed cubic coverage remains edge-budget gated. |

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.
