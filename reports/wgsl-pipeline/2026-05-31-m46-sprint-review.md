# M46 Sprint Review

Date: 2026-05-31
Milestone: M46 -- Generated Evidence Expansion
Linear: GRA-223 through GRA-230

## Outcome

M46 is complete. The sprint converted five high-value dashboard rows from static
evidence to generated evidence while preserving the public scene set, support
status, thresholds, route semantics, fallback policy, and PM dashboard shape.

The key result is that the dashboard is now closer to a repeatable conformance
tool instead of a mostly static evidence page.

## Before / After

| Signal | Before M46 | After M46 |
|---|---:|---:|
| Scene rows | 13 | 13 |
| `pass` | 11 | 11 |
| `tracked-gap` | 0 | 0 |
| `expected-unsupported` | 2 | 2 |
| `fail` | 0 | 0 |
| `maturity.generated-evidence` | 3 | 8 |
| `maturity.static-evidence` | 10 | 5 |
| `maturity.adapter-backed` | 2 | 2 |

## Delivered Rows

| Row | Result | Evidence preserved |
|---|---|---|
| `solid-rect` | Converted to generated evidence. | P0 adapter-backed GPU capture, `fallbackReason=none`, 100% similarity. |
| `analytic-aa-convex` | Converted to generated evidence. | Composited `SrcOver` AA oracle, adapter-backed convex fan route, not broad Path AA. |
| `path-aa-stroke-primitive` | Converted to generated evidence. | M44 primitive stroke promotion and edge-budget inventory delta. |
| `image-filter-compose-cf-matrix-transform` | Converted to generated evidence. | Bounded M45 image-filter DAG subset and pre-pass/intermediate texture route. |
| `src-over-stack` | Converted to generated evidence. | M43 measured CPU/GPU performance payloads, still reporting-only. |

## Remaining Static Rows

| Row | Why it remains static |
|---|---|
| `runtime-effect-simple` | Should move with generated runtime-effect descriptor evidence. |
| `clip-rect-difference` | Needs dedicated generated clip-lowering diagnostics. |
| `bitmap-shader-local-matrix` | Remains the measured-performance fallback candidate after `src-over-stack`. |
| `path-aa-stroke-outline-fallback` | Expected unsupported Path AA breadth row; should remain visible. |
| `path-aa-edge-budget-boundary` | Expected unsupported edge-budget boundary row; should remain visible. |

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

The dashboard should show 13 scenes: 11 pass, 2 expected unsupported, 8
generated, and 5 static.

## Validation

Verified after the sprint:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

The generated dashboard export contains no duplicate scene ids, no tracked
gaps, no failing support claims, valid tag namespaces, CPU/GPU artifacts for
passing rows, stable fallback reasons for expected unsupported rows, and adapter
metadata for adapter-backed rows.

## Review Notes

- M46 achieved the sprint target without adding new runtime feature scope.
- The support boundary stayed clean: Path AA breadth and general image-filter
  DAG support are still non-claims.
- The next useful sprint should target the remaining static rows in priority
  order, starting with runtime-effect descriptor evidence or clip-lowering
  diagnostics.
