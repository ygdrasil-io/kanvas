# M57 Path AA / Clip Micro-Promotion

Linear: `GRA-341`, `GRA-342`

Result: one bounded Path AA / clip generated `pass` row is added while broad
unsupported rows remain explicit.

## Added Row

| Row | Status | Base evidence | GPU route | Fallback |
|---|---|---|---|---|
| `m57-aaclip-bounded-grid` | `pass` | `AaclipSceneCaptureTest` row-specific artifacts | `webgpu.coverage.aaclip-bounded-grid` | `none` |

The row is generated from
`reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json`
by `pipelineM57PathAaClipMicroPromotionPack`. The source render evidence is
captured by
`org.skia.gpu.webgpu.AaclipSceneCaptureTest`, not copied from an existing Path
AA row.

## Artifacts

The materialized row carries:

- `skia.png`
- `cpu.png`
- `gpu.png`
- `cpu-diff.png`
- `gpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `stats.json`

## Boundary Policy

These existing rows remain expected unsupported:

- `path-aa-stroke-outline-fallback`
- `path-aa-edge-budget-boundary`
- `path-aa-convexpaths-edge-budget`
- `path-aa-dashing-edge-budget`
- `m52-closed-capped-hairlines-edge-budget`
- `m53-complexclip-boundary-refusal`
- `m54-dash-circle-boundary`

## Non-Claims

M57 does not claim broad Path AA support, dash/cap/join support,
stroke-outline support, complex clip support, large clipped path support, or a
larger WebGPU edge budget.
