# M48 Dashboard Readiness Sync

Date: 2026-05-31
Linear: GRA-285
Parent epic: GRA-279
Depends on: GRA-282, GRA-283, GRA-284

## Scope

GRA-285 synchronizes PM-facing readiness docs after the M48 scene pack landed.
The changed docs are:

- `README.md`
- `.upstream/target/rendering-conformance-performance-target.md`

## Dashboard Counters

Current merged dashboard counters after M48:

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 21 |
| Static evidence rows | 2 |
| Adapter-backed rows | 2 |

## Readiness Update

Post-MVP Big Target readiness for MEP moves from 35% to 40%.

The only progress axis changed by M48 is Skia integration coverage:

| PM area | Previous | Current | Rationale |
|---|---:|---:|---|
| Skia integration coverage | 15% | 35% | M48 added 10 selected P0/P1 rows across paint, clip, transform, bitmap, gradient, Path AA, and image-filter breadth while preserving 0 tracked-gap / 0 fail. |

Other axes remain unchanged:

| PM area | Progress |
|---|---:|
| Evidence foundation | 100% |
| CI and release gates | 10% |
| Performance readiness | 15% |
| PM demo and reporting workflow | 15% |

Weighted readiness calculation: `25% * 100% + 25% * 35% + 20% * 10% + 15% * 15% + 15% * 15% = 40.25%`, rounded to 40%.

## Evidence Links

- Taxonomy: `reports/wgsl-pipeline/2026-05-31-m48-mep-skia-scene-taxonomy.md`
- Scene pack: `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md`
- Paint/blend/transform support: `reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md`
- Bitmap/gradient support: `reports/wgsl-pipeline/2026-05-31-m48-bitmap-gradient-generated-evidence.md`
- Expected unsupported breadth: `reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md`
- Dashboard output: `build/reports/wgsl-pipeline-scenes/index.html`

## Non-Claims

- 40% is PM readiness for the full Post-MVP target, not sprint completion and not effort burn-down.
- Skia integration coverage is 35%, not higher, because CI gates, performance thresholds, broad adapter-backed captures, text/font/codec coverage, and a deployable PM workflow remain incomplete.
- Expected-unsupported rows remain planning evidence, not support claims.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
