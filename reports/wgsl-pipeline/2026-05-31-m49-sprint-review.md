# M49 Sprint Review: MEP Readiness Gate Toward 60%

Date: 2026-05-31

Linear epic: GRA-287
Decision: 60% earned.

M49 is complete because all required lanes landed with merged evidence: dashboard
invariants, CI-friendly validation, portable PM packaging, adapter-backed
expansion, non-blocking performance trend policy, release checklist, and this
score sync. This does not mean the Post-MVP target is complete or MEP-ready; it
means the PM readiness score for the full target can move from 40% to 60%.

## Final Dashboard Counters

Validated dashboard state after M49:

- 23 scene rows;
- 18 pass;
- 5 expected unsupported;
- 0 tracked-gap;
- 0 fail;
- 21 generated-evidence rows;
- 2 static policy rows;
- 7 adapter-backed rows;
- 0 unavailable references in the portable PM bundle manifest.

The adapter-backed rows are:

- `solid-rect`;
- `analytic-aa-convex`;
- `bitmap-rect-nearest`;
- `linear-gradient-rect`;
- `src-over-stack`;
- `bitmap-shader-local-matrix`;
- `clip-rect-difference`.

## PM Readiness Score

| PM area | Weight | Before M49 | After M49 | M49 outcome |
|---|---:|---:|---:|---|
| Evidence foundation | 25% | 100% | 100% | Preserved dashboard semantics with 0 tracked-gap and 0 fail. |
| Skia integration coverage | 25% | 35% | 45% | Expanded adapter-backed proof from 2 to 7 rows. |
| CI and release gates | 20% | 10% | 60% | Added `pipelineSceneDashboardGate` and negative fixture validation. |
| Performance readiness | 15% | 15% | 35% | Defined a non-blocking measured trend gate contract. |
| PM demo and reporting workflow | 15% | 15% | 45% | Added `pipelinePmBundle` with manifest, dashboard, artifacts, and serve instructions. |

Weighted readiness after M49: 60.25%, rounded to 60%.

## Ticket And Merge Evidence

| Ticket | Lane | PR | Merge commit | Evidence |
|---|---|---|---|---|
| GRA-288 | M49-A gate invariant spec | #1265 | `a03d6a2477aff972f25bf4d27c148131d32fdd3d` | `reports/wgsl-pipeline/2026-05-31-m49-dashboard-gate-invariants.md` |
| GRA-289 | M49-B CI validation task | #1266 | `fa38b07c84c2f43ac539bef4ebb7dc81dfc16b2b` | `pipelineSceneDashboardGate`, `pipelineSceneDashboardGateNegativeFixture`, gate JSON/Markdown reports |
| GRA-290 | M49-C portable PM bundle | #1267 | `48c351b826bf6ed8b7d5725a41209d9010cbc6e1` | `reports/wgsl-pipeline/2026-05-31-m49-portable-pm-bundle.md` |
| GRA-291 | M49-D adapter-backed expansion | #1268 | `658c962c5b4e5408c8e568211cd4ee0187fb4b83` | `reports/wgsl-pipeline/2026-05-31-m49-adapter-backed-expansion.md` |
| GRA-292 | M49-E performance trend gate contract | #1269 | `8a55f4bfd658f2c4a593516bb343d4ac24400b8b` | `reports/wgsl-pipeline/2026-05-31-m49-performance-trend-gate-contract.md` |
| GRA-293 | M49-F MEP release readiness checklist | #1270 | `9b41a49ba7da134ed8a80ea6fa518b10b0b95fea` | `reports/wgsl-pipeline/2026-05-31-m49-mep-release-readiness-checklist.md` |
| GRA-294 | M49-G sprint review and score update | #1271 | `6b88fde54ceeed1743f1c1648d88cb9dc18387d3` | This review plus README, target, and backlog sync. |

## Validation Evidence

M49 validation commands:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
```

M49-D adapter-backed owning tests:

```bash
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest' --tests org.skia.gpu.webgpu.BlendModeTest --tests org.skia.gpu.webgpu.TranslucentSrcOverTest --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest
```

Portable PM bundle path:

- bundle directory: `build/reports/wgsl-pipeline-pm-bundle/`;
- manifest: `build/reports/wgsl-pipeline-pm-bundle/manifest.json`;
- dashboard: `build/reports/wgsl-pipeline-pm-bundle/dashboard/index.html`;
- local serve command:

```bash
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard
```

## Performance Trend Gate Status

Performance readiness moves to 35% because M49 defines the contract for a
non-blocking measured-performance trend gate and keeps measured M43 payloads
visible for `src-over-stack` and `bitmap-shader-local-matrix`.

No release-blocking performance threshold is enabled by M49. Performance data
remains reporting-only until baseline ownership, variance policy, environment
eligibility, and rollback behavior are approved for release gating.

## Limitations And Non-Claims

M49 does not claim:

- MEP completion;
- broad Skia/Ganesh/Graphite parity;
- a SkSL compiler, IR, or VM;
- arbitrary Path AA support;
- arbitrary image-filter DAG support;
- text, font, glyph, emoji, or codec readiness;
- perspective or 3D rendering readiness;
- release-blocking performance thresholds.

Dependency-gated text/font/glyph/emoji/codec gaps remain out of scope until real
deliveries land. Do not clear them with short-lived substitutes.

## Next Sprint Recommendation

M50 should convert the M49 readiness gate from milestone evidence into release
operation:

- wire the dashboard gate into required CI where appropriate;
- stabilize non-blocking GPU inventory reporting and ownership;
- decide performance baseline owner, variance limits, and rollback policy before
  any blocking performance threshold;
- expand scene families only when adapter-backed captures and stable fallback
  diagnostics exist;
- keep dependency-gated font/codec/text work blocked on real dependencies, not
  substitutes.
