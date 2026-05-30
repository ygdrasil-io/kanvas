# M48 MEP Skia Scene Taxonomy

Date: 2026-05-31
Linear: GRA-280
Parent epic: GRA-279
Milestone: M48 -- Skia Scene Coverage Expansion

## Purpose

M48 expands the post-MVP evidence platform from a clean but narrow dashboard to a
representative MEP-oriented Skia scene pack. This taxonomy defines which scene
families matter, how the current M47 evidence maps to those families, and what
M48 may add without overstating support.

M48 must keep the dashboard discipline from M47:

- new support claims use generated evidence;
- unsupported breadth is visible as `expected-unsupported` with stable fallback
  reasons;
- no row may become `tracked-gap` just to reserve future work;
- no family-level support claim is made from one representative scene.

## M47 Baseline

M47 closed with:

| Signal | Count |
|---|---:|
| Scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| `maturity.generated-evidence` | 11 |
| `maturity.static-evidence` | 2 |
| `maturity.adapter-backed` | 2 |

The remaining static rows are intentional Path AA policy sentinels:

- `path-aa-stroke-outline-fallback` with `coverage.stroke-outline-edge-count-exceeded`;
- `path-aa-edge-budget-boundary` with `coverage.edge-count-exceeded`.

## Readiness Model

The epic baseline sets Skia integration coverage readiness at 15%. M48 can move
that toward roughly 30-35% only if it adds representative evidence across
multiple families, not by adding many near-duplicate rows to one already-covered
path.

Recommended M48 scoring interpretation:

| Evidence added | Readiness contribution |
|---|---|
| 4-6 generated pass rows across paint/blend/transform/bitmap/gradient | Establishes broader support surface for common MEP drawing primitives. |
| 2-4 explicit expected-unsupported rows across hard Path AA/image-filter/clip breadth | Makes non-support visible and prevents overclaiming. |
| 0 tracked-gap / 0 fail after export | Preserves release-quality dashboard posture. |
| PM docs updated with family-level non-claims | Converts evidence into reviewable readiness instead of optimistic percentages. |

If M48 lands at least 8 new rows with the above mix and keeps 0 tracked-gap / 0
fail, moving Skia integration readiness from 15% to 30-35% is defensible. If the
row count is lower or concentrated in one family, readiness should move less.

## Family Taxonomy

| Family | Why it matters for MEP | M47 coverage | M48 treatment | Recommended tags | Non-claim boundary |
|---|---|---|---|---|---|
| `paint` | Most product scenes start with paint color, alpha, and coverage composition. Paint rows are the base signal for simple UI, charts, and editor overlays. | Covered indirectly by `solid-rect`, `src-over-stack`, gradients, and runtime-effect rows; no dedicated multi-paint/alpha-modulation row. | Support candidate. Select one or two generated pass rows that isolate paint alpha/color modulation and coverage interaction. | `feature.paint`, `feature.coverage.analytic-rect`, `source.generated`, `maturity.generated-evidence`, `risk.none` | A pass row proves only the selected paint/coverage contract, not full paint effects, color filters, or color-space support. |
| `bitmap` | Bitmap sampling covers icons, textures, sprites, image tiles, and most UI/image content. | `bitmap-rect-nearest` and `bitmap-shader-local-matrix` pass as generated evidence. | Support candidate. Add one generated pass row only if it covers a distinct axis such as filtering/tile mode or composed transform; avoid duplicating nearest/local-matrix evidence. | `feature.image.bitmap`, `feature.sampling.nearest` or `feature.sampling.linear`, `feature.shader.local-matrix` when applicable, `source.generated`, `maturity.generated-evidence`, `risk.none` | Do not claim codec, color management, mipmap, perspective, or all tile modes unless the selected row proves them. |
| `gradient` | Gradients are common in UI and design tooling; stop interpolation and transform behavior are PM-visible. | `linear-gradient-rect` passes as generated evidence. | Support candidate. Add one generated pass row for a distinct gradient axis such as multi-stop, transformed gradient, or non-linear gradient only if an owning test/report exists. | `feature.gradient.linear`, `feature.gradient.radial` or `feature.gradient.sweep`, `feature.coverage.analytic-rect`, `source.generated`, `maturity.generated-evidence`, `risk.none` | A new gradient row must not imply broad gradient interpolation/color-space parity beyond its selected stop/tile/transform contract. |
| `clip` | Clipping is critical for UI containers, masks, scroll regions, and nested drawing. | `clip-rect-difference` passes as generated evidence; simple coverage/clip exists in other rows. | Mixed. Add generated pass evidence for one straightforward clip/transform case if selected; use expected-unsupported for complex clip/mask breadth if selected. | Pass: `feature.clip`, `feature.coverage.clip`, `source.generated`, `risk.none`. Unsupported: `feature.clip`, `route.gpu.expected-unsupported`, `risk.expected-unsupported`. | Do not claim general mask stack, inverse fill, nested clips, or arbitrary path clipping from one rect/rrect row. |
| `path-aa` | Antialiased paths are a major Skia fidelity axis and a frequent source of edge-budget limits. | `analytic-aa-convex` and `path-aa-stroke-primitive` pass; two Path AA policy rows remain expected-unsupported. | Expected-unsupported candidate for hard breadth; support candidate only for narrowly selected adapter-backed or generated convex/stroke variants. | Pass: `feature.path-aa`, `feature.coverage.aa`, `source.generated`, `risk.none`. Unsupported: `feature.path-aa`, `route.gpu.expected-unsupported`, `risk.expected-unsupported`, `risk.edge-budget`. | Do not claim broad Path AA, arbitrary complex paths, dash/cap/join breadth, or budget removal. Edge-budget refusals must remain visible. |
| `image-filter` | Image-filter DAGs matter for blur, shadows, effects, and layer composition. | `crop-image-filter-nonnull-prepass` and `image-filter-compose-cf-matrix-transform` pass as generated evidence for bounded subsets. | Mixed. Add generated pass evidence only for a small selected DAG extension; add expected-unsupported rows for broad DAG shapes outside current pre-pass/layer policy. | Pass: `feature.image-filter`, `feature.color-filter` or `feature.crop`, `feature.matrix-transform`, `source.generated`, `risk.none`. Unsupported: `feature.image-filter`, `route.gpu.expected-unsupported`, `risk.expected-unsupported`. | Do not claim arbitrary DAG scheduling, recursive filters, full crop semantics, blur/shadow breadth, or unbounded intermediate texture policy. |
| `runtime-effect` | Runtime effects are compatibility-critical for user-defined shader-like behavior, but Kanvas intentionally avoids rebuilding SkSL. | `runtime-effect-simple` passes as generated evidence for a registered descriptor-backed Kotlin/WGSL implementation. | Defer for M48 unless selection needs one explicit expected-unsupported row for unregistered/unsupported runtime-effect breadth. | Pass: `feature.runtime-effect`, `source.generated`, `maturity.generated-evidence`, `risk.none`. Unsupported: `feature.runtime-effect`, `route.gpu.expected-unsupported`, `risk.expected-unsupported`. | Do not claim arbitrary SkSL, Skia RuntimeEffect compiler parity, dynamic effect compilation, or VM support. Registered descriptors only. |
| `blend` | Blend behavior controls compositing fidelity for layers, UI overlaps, and editor tools. | `src-over-stack` passes as generated evidence with measured performance payloads; many blend modes remain outside direct evidence. | Support candidate. Add one generated pass row for a distinct, supported blend mode only if route diagnostics and fallback policy are explicit. | `feature.blend.src-over` or `feature.blend.<mode>`, `feature.coverage.analytic-rect`, `source.generated`, `maturity.generated-evidence`, `risk.none` | Do not imply full Porter-Duff or advanced blend-mode coverage from SrcOver plus one extra row. Unsupported blend modes need explicit refusal rows or deferral. |
| `transform` | Transforms affect every scene family: CTM, local matrices, image sampling, gradients, clips, and filters. | Covered through `bitmap-shader-local-matrix` and `image-filter-compose-cf-matrix-transform`; no broad transform pack exists. | Support candidate. Add one generated pass row that isolates composed CTM/local transform semantics, preferably paired with paint/blend or bitmap/gradient selection. | `feature.matrix-transform`, `feature.paint` or family-specific tag, `source.generated`, `maturity.generated-evidence`, `risk.none` | Do not claim perspective, 3D transforms, full matrix stack behavior, or transform correctness across all shaders/filters. |

## M48 Selection Guidance

GRA-281 should select 8-12 P0/P1 scenes with this approximate mix:

| Group | Recommended count | Rationale |
|---|---:|---|
| Paint / blend / transform generated pass rows | 3-4 | Expands common UI composition coverage without opening hard unsupported breadth first. |
| Bitmap / gradient generated pass rows | 2-3 | Adds visible MEP image/gradient axes beyond existing nearest/local-matrix/linear baseline. |
| Explicit expected-unsupported rows | 2-3 | Keeps hard Path AA, broad image-filter DAG, or complex clip/mask limits visible. |
| Deferred rows | 1-2 | Records important MEP families whose owner/test/artifact is not ready for M48. |

Each selected scene must name:

- scene id;
- family and priority;
- status target: generated support evidence, expected-unsupported evidence, or deferred;
- owning command/test/report if support is claimed;
- artifact root;
- reference kind;
- CPU route;
- GPU route or stable refusal reason;
- threshold policy;
- tags;
- exact non-claim boundary.

## Validation Policy

M48 tickets must continue to run:

```text
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Tickets adding generated support rows must also run the owning generated
scene/test command and link artifact roots. Tickets adding expected-unsupported
rows must preserve stable non-`none` GPU fallback reasons and cite the policy or
inventory source that makes the refusal intentional.

## Non-Claims For The Sprint

M48 does not claim:

- complete Skia GM parity;
- broad Path AA support or edge-budget removal;
- arbitrary image-filter DAG support;
- arbitrary runtime-effect or SkSL compatibility;
- all blend modes, gradients, tile modes, codecs, fonts, or color spaces;
- performance release gates.
