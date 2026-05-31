# M48 Sprint Review

Date: 2026-05-31
Milestone: M48 -- Skia Scene Coverage Expansion
Linear: GRA-279 through GRA-286

## Outcome

M48 is complete. The sprint expanded the post-MVP scene dashboard from a clean
but narrow evidence set into a broader MEP planning surface. It added 10 selected
P0/P1 rows: 7 generated support rows and 3 explicit expected-unsupported breadth
rows.

The dashboard remains release-quality as evidence: 0 tracked gaps, 0 failing
support claims, stable fallback reasons for unsupported rows, and no broad Path
AA or arbitrary image-filter support overclaim.

## Final Dashboard Counters

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 5 |
| `fail` | 0 |
| `maturity.generated-evidence` | 21 |
| `maturity.static-evidence` | 2 |
| `maturity.adapter-backed` | 2 |
| CPU/GPU perf `measured` | 2 each |

## Ticket Summary

| Ticket | Result | PR | Merge | Evidence |
|---|---|---|---|---|
| GRA-280 | Defined the M48 MEP scene taxonomy, family priorities, and readiness rule. | #1258 | `cbd5728eda0887153b428b4b405ca4bd527da6f3` | `reports/wgsl-pipeline/2026-05-31-m48-mep-skia-scene-taxonomy.md` |
| GRA-281 | Selected the 10 P0/P1 rows for M48-C/D/E and documented row contracts. | #1259 | `95eff3ef8eb5ba6205630877519edb0ab1841429` | `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md` |
| GRA-282 | Added 4 generated paint, blend, clip, and transform support rows. | #1260 | `ecbc556956aa7bdcd0c43c2b89fd72ca3e0c035a` | `reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md` |
| GRA-283 | Added 3 generated bitmap and gradient support rows. | #1261 | `1c7e4678d2ec249a67dc367948929d94f0e4f58c` | `reports/wgsl-pipeline/2026-05-31-m48-bitmap-gradient-generated-evidence.md` |
| GRA-284 | Added 3 expected-unsupported Path AA/image-filter breadth rows. | #1262 | `02ffeeb82cf9abb4ad10f4eed9d1483a2f41038c` | `reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md` |
| GRA-285 | Synced PM readiness docs and moved Big Target readiness from 35% to 40%. | #1263 | `73dc0f54e6b657f133d5487cba173ff0d6aa53ee` | `reports/wgsl-pipeline/2026-05-31-m48-dashboard-readiness-sync.md` |
| GRA-286 | Closed M48 with this sprint review and M49 recommendation. | This PR | TBD | `reports/wgsl-pipeline/2026-05-31-m48-sprint-review.md` |

## New Scenes By Family

| Scene id | Family | Status | Artifact root |
|---|---|---|---|
| `draw-paint-full-clip` | paint | `pass` | `reports/wgsl-pipeline/scenes/artifacts/draw-paint-full-clip/` |
| `draw-paint-clipped-rect` | paint / clip | `pass` | `reports/wgsl-pipeline/scenes/artifacts/draw-paint-clipped-rect/` |
| `scaled-rects-transform-stack` | transform / blend | `pass` | `reports/wgsl-pipeline/scenes/artifacts/scaled-rects-transform-stack/` |
| `gradient-color-filter-linear-kplus` | gradient / color-filter / blend | `pass` | `reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/` |
| `bitmap-shader-repeat-tile` | bitmap | `pass` | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-repeat-tile/` |
| `bitmap-subset-local-matrix-repeat` | bitmap / transform | `pass` | `reports/wgsl-pipeline/scenes/artifacts/bitmap-subset-local-matrix-repeat/` |
| `sweep-gradient-path-clamp` | gradient / Path AA | `pass` | `reports/wgsl-pipeline/scenes/artifacts/sweep-gradient-path-clamp/` |
| `path-aa-convexpaths-edge-budget` | Path AA | `expected-unsupported` | `reports/wgsl-pipeline/scenes/artifacts/path-aa-convexpaths-edge-budget/` |
| `path-aa-dashing-edge-budget` | Path AA / stroke / dash | `expected-unsupported` | `reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/` |
| `image-filter-crop-nonnull-prepass-required` | image-filter / crop | `expected-unsupported` | `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/` |

## Pass Rows Added In M48

M48 adds these generated support claims:

- `draw-paint-full-clip`
- `draw-paint-clipped-rect`
- `scaled-rects-transform-stack`
- `gradient-color-filter-linear-kplus`
- `bitmap-shader-repeat-tile`
- `bitmap-subset-local-matrix-repeat`
- `sweep-gradient-path-clamp`

Each pass row has reference, CPU, GPU, diff, stats, route diagnostics, evidence
links, and `fallbackReason=none` for the GPU route.

## Expected-Unsupported Rows Added In M48

M48 adds these generated refusal rows:

| Scene id | GPU route | Stable fallback reason | Non-claim |
|---|---|---|---|
| `path-aa-convexpaths-edge-budget` | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` | Does not claim ConvexPathsGM or broad Path AA support. |
| `path-aa-dashing-edge-budget` | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` | Does not claim dash/cap/join support or edge-budget removal. |
| `image-filter-crop-nonnull-prepass-required` | `webgpu.image-filter.refuse` | `image-filter.crop-input-nonnull-prepass-required` | Does not claim arbitrary image-filter DAG support or recursive crop pre-passes. |

These rows are planning evidence, not support claims.

## Commands Run

Representative M48 implementation commands:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintTest --tests org.skia.gpu.webgpu.ScaledRectsWebGpuTest --tests org.skia.gpu.webgpu.GradientColorFilterTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest --tests org.skia.gpu.webgpu.crossbackend.BitmapSubsetShaderCrossBackendTest --tests org.skia.gpu.webgpu.SweepGradientPathTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Each ticket also ran `rtk git diff --check` and `rtk ./gradlew --no-daemon pipelineSceneDashboard` before merge.

## Readiness Before And After

| PM readiness area | Before M48 | After M48 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | The generated dashboard and evidence rules were already complete through M47; M48 preserved the quality bar. |
| Skia integration coverage | 15% | 35% | M48 added 10 selected rows across multiple Skia-relevant families with 0 tracked-gap / 0 fail. |
| CI and release gates | 10% | 10% | M48 validated dashboard generation but did not create release-grade promotion gates. |
| Performance readiness | 15% | 15% | M48 did not promote measured trends into thresholds or CI gates. |
| PM demo/reporting workflow | 15% | 15% | M48 still uses local static dashboard output. |
| Weighted PM readiness | 35% | 40% | Weighted calculation from `README.md` and target doc. |

The score moved because the M48 scene pack satisfied the GRA-280 rule: at least
8 representative rows across multiple families, 0 tracked-gap, and 0 fail. It
did not move further because the remaining MEP blockers are outside scene breadth
alone.

## PM Demo

Run:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-scenes
```

Open:

```text
http://127.0.0.1:8765/index.html
```

The dashboard should show 23 scenes: 18 pass, 5 expected unsupported, 21
generated evidence rows, 2 static evidence rows, and 2 adapter-backed rows.

## Remaining Risks And Non-Claims

- The dashboard is local/static; it is not yet a deployable PM reporting workflow.
- Adapter-backed coverage is still narrow at 2 rows.
- Performance payloads remain reporting-only; no release threshold or regression gate changed in M48.
- Expected-unsupported rows intentionally preserve Path AA and image-filter refusal policy.
- M48 does not claim text, glyph masks, font, emoji, codec, perspective, arbitrary SkSL, arbitrary image-filter DAG, or broad Path AA support.
- Static Path AA expected-unsupported policy sentinels remain intentional and should not be treated as conversion debt.

## M49 Recommendation

Recommended M49 focus: CI and release gates for the generated scene dashboard.

Rationale:

- M48 expanded scene breadth enough to move Skia integration coverage to 35%.
- The next MEP blocker is not another dashboard hygiene pass; it is making the evidence enforceable.
- CI gates should distinguish required pass rows, allowed expected-unsupported rows, and non-blocking inventory breadth.
- A release gate can also prepare the later performance-readiness sprint by defining where measured trend thresholds will attach.

Suggested M49 tickets:

| Candidate | Purpose |
|---|---|
| M49-A gate inventory | Define required dashboard invariants: no duplicate ids, 0 tracked-gap, 0 fail, stable fallback reasons, required artifact links. |
| M49-B CI task | Add a CI-friendly validation task that fails on support-claim regressions while allowing expected-unsupported rows by explicit policy. |
| M49-C PM artifact bundle | Generate a portable dashboard/report bundle with counters, selected scene links, and Linear/PR references. |
| M49-D release readiness checklist | Link Linear epic, dashboard output, validation commands, allowed refusal rows, and known limitations into a single MEP gate. |
| M49-E performance gate design | Specify how M43 measured payloads become non-blocking trend signals before becoming release thresholds. |

If M49 must prioritize performance instead, start with `M49-E`; otherwise CI and
release gates should come first because they protect all future scene expansion.
