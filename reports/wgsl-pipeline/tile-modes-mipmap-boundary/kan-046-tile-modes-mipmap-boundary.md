# KAN-046 Tile Modes And Mipmap Boundary

KAN-046 packages selected bitmap sampling evidence for tile modes and mipmap
boundaries. It keeps the scope deliberately bounded: two tile-mode rows are
support rows with existing reference/CPU/GPU/diff/stat/routes, and mipmap
requests without a real mipmap chain remain expected-unsupported.

## Summary

| Metric | Count |
|---|---:|
| Total rows | 4 |
| Tile-mode support rows | 2 |
| Mipmap expected-unsupported rows | 2 |
| Support rows missing artifacts | 0 |
| Routes missing sampling | 0 |
| Routes missing local matrix | 0 |
| Routes missing tile mode | 0 |
| Routes missing mipmap mode | 0 |
| Broad texture claims | 0 |

## Sampling Rows

| Row | Status | Category | Sampling | Tile mode | Local matrix | Mipmap mode | Reason |
|---|---|---|---|---|---|---|---|
| `bitmap-shader-repeat-tile` | `pass` | `tile-mode-support` | `nearest` | `kRepeat/kRepeat` | `identity` | `none` | `none` |
| `bitmap-subset-local-matrix-repeat` | `pass` | `tile-mode-support` | `nearest` | `kRepeat/kRepeat` | `affine-scale-rotate` | `none` | `none` |
| `bitmap-mipmap-sampler-refusal` | `expected-unsupported` | `mipmap-boundary-refusal` | `nearest-with-mipmap-request` | `kClamp/kClamp` | `identity` | `required-but-no-chain` | `image-sampling.mipmap-unsupported` |
| `bitmap-npot-mipmap-sampler-refusal` | `expected-unsupported` | `mipmap-boundary-refusal` | `linear-with-mipmap-request` | `kRepeat/kRepeat` | `affine-scale` | `required-but-no-chain` | `image-sampling.mipmap-unsupported` |

## Guards

| Guard | Rows |
|---|---|
| supportRowsMissingArtifacts | `[]` |
| supportRowsWithFallback | `[]` |
| unsupportedRowsMissingReason | `[]` |
| routesMissingSampling | `[]` |
| routesMissingLocalMatrix | `[]` |
| routesMissingTileMode | `[]` |
| routesMissingMipmapMode | `[]` |
| hiddenArbitraryTextureClaims | `[]` |
| hiddenCodecDecodeClaims | `[]` |
| hiddenPerspectiveClaims | `[]` |
| hiddenMipmapSupportClaims | `[]` |

## Validations

| Validation | Status | Evidence |
|---|---|---|
| `tile-mode-repeat-visible` | `pass` | Two selected pass rows expose sampling, local matrix, tile mode, mipmap mode, reference/CPU/GPU/diff/stat, route, and fallbackReason=none. |
| `mipmap-boundary-visible` | `pass` | Two mipmap request rows remain expected-unsupported with image-sampling.mipmap-unsupported and no materialized mipmap chain. |
| `bitmap-sampling-non-claims` | `pass` | Rows keep non-claims for arbitrary textures, codec decode, perspective sampling, color-managed decode, broad tile modes, and mipmap support. |

## Required Commands

- `validateKan046TileModesMipmapBoundary`
- `pipelineSceneDashboardGate`
- `pipelinePmBundle`

## Non-Claims

- KAN-046 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.
- KAN-046 supports only selected fixture-backed tile-mode rows with existing reference/CPU/GPU/diff/stat/routes.
- KAN-046 keeps mipmap requests without a real mipmap chain as expected-unsupported via image-sampling.mipmap-unsupported.
- KAN-046 does not claim arbitrary texture support, codec decode, perspective sampling, color-managed decode, or broad tile-mode parity.
- KAN-046 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.

No arbitrary texture support, codec decode, perspective sampling, color-managed
decode, broad tile-mode parity, or mipmap support is claimed.
