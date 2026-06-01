# M66 GM/reference selection ranking

Date: 2026-06-01
Linear: FOR-38 under FOR-32
Specs: `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`, `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`

## Scope

M66 selects cumulative rows from existing generated evidence and row-specific
M60-M64 artifacts. Static inventory remains planning input only. A row is
selected only when it has a declared `referenceKind`, route diagnostics,
stats/diff artifacts, and either `fallbackReason=none` for support or a stable
non-`none` fallback for expected-unsupported evidence.

## Ranking model

| Rank axis | Weight | Reason |
|---|---:|---|
| Product rendering value | 30 | Common canvas behavior before stress fixtures. |
| Reference provenance | 25 | `skia-upstream` first, then bounded test oracle, then CPU oracle breadth rows. |
| Artifact completeness | 20 | Reference/CPU/GPU/diff/stats/routes for support; stable policy artifacts for refusals. |
| Family balance | 15 | Path AA, image filters, text, blend/color, bitmap, transforms, runtime effects. |
| Refusal value | 10 | Keeps unsupported scope visible without counting it as support. |

## Selected wave

| Row | Family | Status | referenceKind | Provenance | Linear |
|---|---|---|---|---|---|
| `m66-aaclip-bounded-grid-skia` | Path AA / coverage | pass | `skia-upstream` | M57 `skia-gm-aaclip` bounded grid artifacts | FOR-39 |
| `m66-analytic-aa-convex-cpu-oracle` | Path AA / coverage | pass | `cpu-oracle` | analytic AA convex generated artifact | FOR-39 |
| `m66-clip-rect-difference-skia` | Path AA / coverage | pass | `skia-upstream` | generated clip difference row | FOR-39 |
| `m66-bitmap-rect-nearest-skia` | Bitmap/image sampling | pass | `skia-upstream` | generated bitmap rect nearest row | FOR-39 |
| `m66-bitmap-subset-local-matrix-repeat-skia` | Bitmap/image sampling | pass | `skia-upstream` | generated bitmap local-matrix/subset row | FOR-39 |
| `m66-scaled-rects-transform-stack-skia` | Transforms/layers | pass | `skia-upstream` | generated scaled rects row | FOR-39 |
| `m66-crop-image-filter-nonnull-prepass-skia` | Image filters | pass | `skia-upstream` | generated crop/non-null prepass row | FOR-39 |
| `m66-path-aa-stroke-primitive-oracle` | Path AA / coverage | pass | `test-oracle` | bounded stroke primitive test oracle | FOR-39 |
| `m66-image-filter-compose-cf-matrix-transform-oracle` | Image filters | pass | `test-oracle` | bounded compose/color-filter/matrix-transform oracle | FOR-39 |
| `m66-src-over-alpha-stack-oracle` | Paint/blend/color | pass | `test-oracle` | SrcOver stack oracle with performance payload reuse | FOR-39 |
| `m66-linear-gradient-kplus-oracle` | Paint/blend/color | pass | `test-oracle` | linear-gradient plus kPlus color-filter oracle | FOR-39 |
| `m66-sweep-gradient-path-clamp-oracle` | Paint/blend/color | pass | `test-oracle` | sweep-gradient clamp over bounded path oracle | FOR-39 |
| `m66-runtime-effect-simple-descriptor-oracle` | Runtime effects | pass | `test-oracle` | registered SimpleRT descriptor artifacts | FOR-39 |
| `m66-font-latin-outline-cpu-oracle` | Text/glyphs | pass | `cpu-oracle` | Liberation outline text breadth row | FOR-39 |
| `m66-font-positioned-glyph-run-cpu-oracle` | Text/glyphs | pass | `cpu-oracle` | positioned glyph run breadth row | FOR-39 |
| `m66-font-kerning-style-cpu-oracle` | Text/glyphs | pass | `cpu-oracle` | kerning style breadth row | FOR-39 |
| `m66-path-aa-dashing-edge-budget-refusal` | Path AA / coverage | expected-unsupported | `cpu-oracle` | dashed path edge-budget boundary | FOR-40 |
| `m66-image-filter-crop-prepass-refusal` | Image filters | expected-unsupported | `cpu-oracle` | crop null-input/picture-prepass boundary | FOR-40 |
| `m66-font-complex-shaping-refusal` | Text/glyphs | expected-unsupported | `cpu-oracle` | explicit-shaper dependency boundary | FOR-40 |

## Rejected or deferred

| Candidate | Reason |
|---|---|
| `skia-gm-animatedgif` | Codec/animation dependency remains gated; no substitute fixture. |
| `skia-gm-dftext` | SDF text support is not proven by outline glyph rows. |
| `skia-gm-runtimeimagefilter` | Needs registered runtime image-filter descriptors; arbitrary SkSL remains refused. |
| `skia-gm-complexclip` | Still outside bounded rect/rrect/AA clip evidence. |

## Non-claims

The selected M66 rows normalize evidence; they do not claim broad Skia GM
parity, arbitrary image-filter DAG support, broad Path AA, full text shaping,
glyph atlas support, codecs, arbitrary SkSL, or release-blocking performance
movement.
