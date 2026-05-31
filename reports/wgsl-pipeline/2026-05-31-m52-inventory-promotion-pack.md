# M52 Inventory Promotion Pack Evidence

Date: 2026-05-31
Milestone: M52
Linear epic: GRA-302
Tickets: GRA-303 through GRA-308

## Scope

M52 promotes a small selected subset of the M51 Skia GM inventory into
generated dashboard evidence. Inventory status remains planning evidence only:
the dashboard rows below are scoped to their explicit generated scene
contracts, artifacts, routes, thresholds, and refusal policy.

The pack intentionally does not claim broad Skia GM support, broad font/text
support, codec support, arbitrary image-filter DAG support, arbitrary SkSL, or
broad Path AA support.

## Selected Rows

| Scene id | Inventory id | Status | Contract |
|---|---|---|---|
| `m52-aa-rect-modes-tight-aa` | `skia-gm-aarectmodes` | `pass` | Tight AA rect mode subset through CPU AA oracle and adapter-backed WebGPU analytic rect coverage. |
| `m52-android-blend-src-over-screen` | `skia-gm-androidblendmodes` | `pass` | Two-layer Android blend subset limited to `SrcOver` and `Screen` blend routes. |
| `m52-fillrect-gradient-linear` | `skia-gm-fillrectgradient` | `pass` | Linear fill-rect gradient subset through generated WGSL gradient route. |
| `m52-hardstop-gradient-linear` | `skia-gm-hardstopgradients` | `pass` | Hard-stop linear gradient subset with exact stop-boundary oracle. |
| `m52-clipped-bitmap-shader-rect` | `skia-gm-clippedbitmapshaders` | `pass` | Bitmap shader repeat subset clipped by a rectangular coverage plan. |
| `m52-bitmap-image-basic` | `skia-gm-bitmapimage` | `pass` | Basic bitmap image draw subset with nearest sampling. |
| `m52-bitmap-rect-test-nearest` | `skia-gm-bitmaprecttest` | `pass` | Bitmap rect nearest sampling subset linked to the GM candidate. |
| `m52-closed-capped-hairlines-edge-budget` | `skia-gm-closedcappedhairlines` | `expected-unsupported` | Path AA hairline cap suite exceeds the current WebGPU edge budget. |
| `m52-big-tile-image-filter-dag-refusal` | `skia-gm-bigtileimagefilter` | `expected-unsupported` | Big tile image-filter DAG still requires an explicit picture/layer pre-pass. |
| `m52-color-emoji-blendmodes-refusal` | `skia-gm-coloremojiblendmodes` | `expected-unsupported` | Color emoji blend modes remain dependency-gated on color glyph delivery. |

## Rejected For This Pack

| Inventory id | Reason |
|---|---|
| `skia-gm-animatedgif` | Codec/animation dependency remains gated; no dashboard support claim. |
| `skia-gm-animcodecplayerexif` | Codec/EXIF dependency remains gated; no dashboard support claim. |
| `skia-gm-dftext` | SDF glyph backend remains gated. |
| `skia-gm-dftextblobpersp` | SDF glyph and perspective text delivery remain gated. |
| `skia-gm-runtimeimagefilter` | Runtime image-filter contract needs a separate descriptor-backed slice. |
| `skia-gm-runtimeintrinsics` | Runtime intrinsic coverage needs separate WGSL descriptor evidence. |
| `skia-gm-gradients2ptconical` | Two-point conical gradient remains outside this narrow linear-gradient pack. |
| `skia-gm-complexclip` | Complex clip/path coverage needs a Geometry/Coverage-specific slice. |

## Evidence Contract

Every promoted M52 row has:

- a top-level `inventoryId`;
- generated provenance with `linearIssue`, `sourceReport`, and
  `artifactRoot`;
- reference, CPU image, CPU diff, CPU route, CPU stats, and top-level stats;
- GPU render/diff/stats and `fallbackReason=none` for `pass` rows;
- GPU route/refusal with stable non-`none` fallback reason for
  `expected-unsupported` rows;
- canonical route diagnostics and stats artifacts under
  `reports/wgsl-pipeline/scenes/artifacts/<scene-id>/`;
- tags in the required namespaces, plus `source.inventory` and either
  `risk.none` or `risk.expected-unsupported`.

## Counters

| Signal | Count |
|---|---:|
| Selected inventory candidates | 10 |
| Promoted generated dashboard rows | 10 |
| Generated `pass` rows | 7 |
| Generated `expected-unsupported` rows | 3 |
| Rejected/deferred inventory candidates documented | 8 |
| New `tracked-gap` rows | 0 |
| New `fail` rows | 0 |

## Limitations

- The seven `pass` rows support only the narrow generated contracts above.
- The three `expected-unsupported` rows are explicit planning/refusal evidence,
  not support claims.
- Font color glyphs, complex shaping, SDF/LCD/glyph-mask work, codecs,
  arbitrary image-filter DAGs, arbitrary SkSL, and broad Path AA remain outside
  this pack unless a later delivery adds generated reference/CPU/GPU evidence.
