# M10 Legacy Inventory And Archive Hygiene

## Tickets

- `KGPU-M10-001 - Inventory legacy gpu-raster route ownership`
- `KGPU-M10-004 - Add archived evidence hygiene for migrated routes`

## Status

Implemented and independently reviewed.

## Inventory Matrix

| Legacy family | Current legacy owner | Current gpu-renderer replacement state | Migration/retirement state |
|---|---|---|---|
| Material source / paint pipeline | `SkWebGpuDevice.drawRect`, `drawPath`, gradient rect helpers, runtime-effect rect helper, bitmap-shader dispatch, color-filter packing, and blend planning | M2 material/WGSL foundations are `done`; M4 image source/upload evidence is partial; M7 runtime-effect descriptor route remains `proposed`; M7 blend destination-read remains `blocked`; M9 cache source-map is `done` for reporting only | Legacy material interpretation remains active; no arbitrary source, dynamic SkSL, destination-read blend, or source cache support is promoted by inventory |
| Solid rect and `drawPaint` rect fill | `SkWebGpuDevice.drawRect`, `drawFillRect`, `drawPaint`, and `GpuRendererShadowAdapter` first-route shadow hook | M1 first-route policy/flag/rollback tickets are `done`; M2 rect/rrect/gradient/scissor evidence is `done`; product activation remains false | Legacy default remains active; no route retired; per-family shadow parity must stay route-specific |
| Rounded rect and simple rect/rrect gradients | Legacy rect/path dispatch in `SkWebGpuDevice` with shader-specific rect draw records | KGPU-M2-001 and KGPU-M2-002 are `done` as evidence; no broad product route activation | Legacy default remains active until route-specific parity and activation evidence are accepted |
| Rect/rrect stroke | `SkWebGpuDevice.drawRect` stroke dispatch, `drawStrokeRect`, `drawHairlineRect`, and `drawAnnularStrokeRect` | Target matrix keeps rect/rrect stroke as future `TargetNative`; current accepted M3 stroke evidence is simple path/stroke `CPUPreparedGPU`, not legacy rect/rrect stroke retirement evidence | Legacy stroke behavior remains active; no broad stroke parity, hairline parity, cap/join parity, or stroke route retirement is claimed |
| Device scissor and simple clips | Legacy scissor/active clip state in `SkWebGpuDevice`, plus `WebGpuCoveragePlanSelector` diagnostics | KGPU-M2-003 is `done`; M3 prepared clip evidence, atlas refusals, and the KGPU-M3-002 stencil-cover contract gate are `done` | No arbitrary clip-stack support; native/path clip migration still requires a separate promoted stencil/pass/readback product route |
| Path fill and path stroke | `SkWebGpuDevice.drawPath`, path-effect dispatch, mask-filter helpers, and `WebGpuCoveragePlanSelector` | KGPU-M3-001, KGPU-M3-002, KGPU-M3-003, KGPU-M3-004, and KGPU-M3-005 are `done`; KGPU-M3-002 is contract-gate evidence only | Prepared artifacts and stencil-cover contract gates are evidence only; legacy path behavior remains until a promoted native replacement is accepted |
| Images, bitmap shaders, codecs, and uploads | `SkWebGpuDevice.drawImageRect`, bitmap shader dispatch, texture upload/cache helpers | KGPU-M4-001, KGPU-M4-002, KGPU-M4-003, and KGPU-M4-004 are `done`; KGPU-M4-004 is sampler-boundary contract/refusal evidence only | No broad codec, animation, mipmap, perspective sampling, native sampler execution, or CPU-rendered compatibility texture claim |
| saveLayer, destination read, and filter DAGs | `SkWebGpuDevice` layer allocation/composite/filter helpers and intermediate resources | KGPU-M5-004 is `done`; KGPU-M5-001 remains `proposed`; KGPU-M5-002 and KGPU-M5-003 are `blocked` | No saveLayer, destination-read, or filter route can be retired until native intermediate/copy/filter evidence lands |
| Text and glyphs | `SkWebGpuDevice`, `SkWebGpuGlyphAtlas`, dftext-compatible legacy tests | All M6 tickets are `blocked` on pure Kotlin text artifacts and adapter-backed upload/binding evidence | Legacy text remains; renderer must not reshape text, parse fonts, or fabricate glyph artifacts |
| Runtime effects, color filters, blends, and color management | Legacy runtime shader/color-filter/blend paths in `SkWebGpuDevice` | KGPU-M7-004 is `done`; KGPU-M7-001 remains `proposed`; KGPU-M7-002 and KGPU-M7-003 are `blocked` | No arbitrary SkSL, dynamic shader, destination-read blend, HDR/profile, or all-blend support is implied |
| Vertices, points, and mesh-like draws | Legacy vertices/points test coverage and `SkWebGpuDevice` draw-family handling | All M8 tickets are `blocked` on blend/destination-read and adapter-backed vertex/index evidence | No `DrawVertices` route, vertex/index upload, batching, or CPU-rasterized mesh texture fallback is promoted |
| Clear/discard and target background | `SkWebGpuDevice` background clear value, render-pass load/clear behavior, and intermediate initialization state | Target matrix lists clear/discard as `TargetNative`, but no M10 retirement ticket has route-specific clear/discard replacement evidence | Legacy target clear/discard behavior remains active; no pass load/store migration or target background route retirement is claimed |
| Performance, cache, and release gates | Legacy and renderer PM evidence/reporting lanes | KGPU-M9-001 is `done`; KGPU-M9-002 and KGPU-M9-003 remain `proposed` | Cache/source-map evidence is reporting-only; no release-blocking frame gate or readiness movement |

## Root PM Packaging Boundary

- Root `pipelinePmBundle` packaging remains adapter-independent for the R6
  activation-candidate evidence.
- Adapter-backed executed R6 PM evidence is opt-in diagnostic evidence and must
  not become a root `pipelinePmBundle` dependency.
- `productRouteActivated=false`, `releaseBlocking=false`, and
  `readinessDelta=0.0` remain required for this inventory wave.

## Archive Hygiene Checklist

- Active tickets must cite active `.upstream/specs/gpu-renderer/` specs or
  current target docs for acceptance criteria.
- Archived migration plans and root upstream snapshots are historical evidence
  only. They may explain provenance, but they cannot define active backlog,
  support, readiness, or retirement acceptance criteria.
- Any migrated route must link current route-specific evidence, review, PM
  output, and rollback evidence before legacy code can be retired.
- Generic M10 inventory rows cannot retire routes; they only identify owners,
  replacement tickets, blockers, and remaining gates.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
```

## Non-Claims

- No legacy route is deleted, bypassed, or disabled.
- No default `gpu-raster` behavior changes.
- No product route activation, release-blocking gate, or readiness delta is
  claimed.
- No per-family parity result is inferred from another family.
- Archived checkbox state is not active backlog.

## Remaining Gates

- Independent review `019ec878-7c64-7e42-ab70-bb80043e53d1` accepted
  KGPU-M10-001 and KGPU-M10-004 for `done` after remediation added explicit
  material/paint, rect/rrect stroke, and clear/discard inventory rows.
- `KGPU-M10-002` remains blocked until adapter-backed shadow parity evidence
  exists per family, including before/after dumps, PM rows, rollback labels, and
  skipped/refused diagnostics.
- `KGPU-M10-003` remains blocked until KGPU-M10-002 is accepted and each
  retirement row names an accepted replacement ticket, activation decision,
  rollback evidence, old-path usage evidence, and PM evidence.
