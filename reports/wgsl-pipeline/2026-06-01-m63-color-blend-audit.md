# M63 Color, Blend, and ColorFilter Audit

Date: 2026-06-01
Linear: FOR-20, FOR-21, FOR-22, FOR-23

## Purpose

M63 promotes concrete color, blend, color-filter, and gradient evidence without
claiming broad Skia color parity. The selected rows are generated from existing
reference/CPU/GPU artifacts and keep advanced color-space, HDR, transfer
function, and arbitrary blend-chain support as explicit nonclaims or stable
refusals.

## Candidate Audit

| Scene | Status | Reference | CPU route | GPU route | Fallback | Decision |
|---|---|---|---|---|---|---|
| `src-over-stack` | pass | test-oracle | `cpu.blend.src-over-stack` | `webgpu.blend.src-over.fixed-function` | `none` | Promote as `m63-src-over-alpha-stack`; bounded Src/SrcOver alpha stack with measured performance copied from the base row. |
| `gradient-color-filter-linear-kplus` | pass | test-oracle | `cpu.shader.linear-gradient.color-filter.blend-kplus-oracle` | `webgpu.generated.linear-gradient.color-filter.blend-kplus` | `none` | Promote as `m63-linear-gradient-color-filter-kplus`; selected linear-gradient plus Blend(red,kPlus) color-filter composite. |
| `sweep-gradient-path-clamp` | pass | test-oracle | `cpu.shader.sweep-gradient.path-aa-oracle` | `webgpu.generated.sweep-gradient.path-aa` | `none` | Promote as `m63-sweep-gradient-path-clamp`; bounded sweep-gradient clamp over convex path coverage. |
| `linear-gradient-rect` | pass | test-oracle | `cpu.shader.linear-gradient.rect` | `webgpu.generated.linear-gradient.rect` | `none` | Keep as existing baseline; M63 uses the richer color-filter gradient row instead. |
| `scaled-rects-transform-stack` | pass | skia-upstream | `cpu.gm.scaled-rects.reference-oracle` | `webgpu.transform.scaled-rects.convex-polygon` | `none` | Use only as refusal artifact base for advanced blend-chain nonclaim; do not claim arbitrary blend composition. |
| `draw-paint-full-clip` | pass | test-oracle | `cpu.paint.draw-paint.full-clip-oracle` | `webgpu.paint.draw-paint.full-clip` | `none` | Use only as refusal artifact base for wide-gamut color-space nonclaim. |
| `draw-paint-clipped-rect` | pass | test-oracle | `cpu.paint.draw-paint.clip-rect-oracle` | `webgpu.paint.draw-paint.clip-rect` | `none` | Keep as existing paint/clip baseline; does not add M63-specific color evidence. |
| `bitmap-shader-repeat-tile` | pass | test-oracle | `cpu.shader.bitmap.repeat-tile-oracle` | `webgpu.shader.bitmap.repeat-tile` | `none` | Exclude from M63; it is primarily bitmap tile sampling rather than color/blend/color-filter parity. |
| `bitmap-subset-local-matrix-repeat` | pass | skia-upstream | `cpu.shader.bitmap.subset-local-matrix-repeat` | `webgpu.shader.bitmap.subset-local-matrix-repeat` | `none` | Exclude from M63; it belongs to bitmap/local-matrix breadth. |
| `image-filter-compose-cf-matrix-transform` | pass | test-oracle | `cpu.image-filter.compose.cf-matrix-transform-oracle` | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` | `none` | Already promoted by M61 as bounded image-filter DAG V2; do not double-count it as M63 color evidence. |

## Selected M63 Rows

Supported pass rows:

- `m63-src-over-alpha-stack`
- `m63-linear-gradient-color-filter-kplus`
- `m63-sweep-gradient-path-clamp`

Stable expected-unsupported rows:

- `m63-wide-gamut-color-space-refusal` with
  `color.color-space-wide-gamut-unsupported`
- `m63-advanced-blend-chain-refusal` with
  `blend.advanced-chain-unsupported`

## Nonclaims

M63 does not claim:

- HDR or wide-gamut rendering;
- ICC profile or transfer-function conversion;
- premul/unpremul correctness across every Skia surface format;
- arbitrary color-filter DAGs;
- arbitrary `SkBlendMode` chains or saveLayer blend composition;
- Ganesh or Graphite compatibility.

The selected rows are bounded WebGPU/Kanvas pipeline evidence. The refusal rows
exist to make the advanced color/blend boundary visible in the PM dashboard.

## Validation

Required implementation validation:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```

