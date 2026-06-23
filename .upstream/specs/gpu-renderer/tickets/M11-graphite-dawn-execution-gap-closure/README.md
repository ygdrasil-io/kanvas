# M11 - Graphite/Dawn Execution Gap Closure

## Goal

Close the planning gaps between existing contract gates and live
adapter-backed execution for the Graphite/Dawn comparison areas that are not
already owned by M0-M10. This milestone is a ticket scaffold only; it does not
promote support, activate product routing, or claim adapter-backed behavior.

## Dependencies

Depends on the completed M3, M4, M5, M7, and M9 contract gates where they
define boundary evidence. These tickets remain independent from font delivery
and do not widen M6 text scope.

## Exit Criteria

- [ ] Pipeline/module/layout caches are integrated with execution materialization
      and expose real hit/miss telemetry.
- [ ] Payload bytes and resource binding blocks materialize into upload buffers
      and bind groups before command encoding.
- [ ] Texture, sampler, destination-read, saveLayer, stencil-cover, registered
      runtime-effect, and paint/blend execution lanes have adapter-backed
      evidence or stable refusal diagnostics.
- [ ] No ticket promotes product activation, Graphite/Ganesh/SkSL porting, or
      hidden CPU-rendered compatibility fallback.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M11-001 - Add WGPU module, pipeline, and bind group layout caches](KGPU-M11-001-add-wgpu-module-pipeline-and-bind-group-layout-caches.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-cache` | `KGPU-M0-005`, `KGPU-M9-001` | `cache reporting-only` |
| [KGPU-M11-002 - Add payload upload and bind group materialization lane](KGPU-M11-002-add-payload-upload-and-bind-group-materialization-lane.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `payloads-bind-groups` | `KGPU-M11-001`, `KGPU-M11-003` | - |
| [KGPU-M11-003 - Add resource materialization handles and provider bridge](KGPU-M11-003-add-resource-materialization-handles-and-provider-bridge.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `resources-execution` | `KGPU-M0-005` | - |
| [KGPU-M11-004 - Add texture and sampler live materialization from boundary evidence](KGPU-M11-004-add-texture-and-sampler-live-materialization-from-boundary-evidence.md) | `done` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `textures-samplers` | `KGPU-M4-004`, `KGPU-M11-003` | - |
| [KGPU-M11-005 - Add destination-read copy and intermediate live materialization](KGPU-M11-005-add-destination-read-copy-and-intermediate-live-materialization.md) | `done` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `destination-read` | `KGPU-M5-002`, `KGPU-M11-003`, `KGPU-M11-004` | `blend legacy` |
| [KGPU-M11-006 - Add saveLayer isolated target live materialization](KGPU-M11-006-add-savelayer-isolated-target-live-materialization.md) | `done` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `layers-resources` | `KGPU-M5-001`, `KGPU-M11-003` | `saveLayer legacy` |
| [KGPU-M11-007 - Add bounded stencil-cover adapter-backed lane](KGPU-M11-007-add-bounded-stencil-cover-adapter-backed-lane.md) | `done` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | `KGPU-M3-002`, `KGPU-M11-001`, `KGPU-M11-003` | `path fill legacy` |
| [KGPU-M11-008 - Add registered runtime-effect execution lane](KGPU-M11-008-add-registered-runtime-effect-execution-lane.md) | `done` | `P1` | `DependencyGated` | `GPUNative` | `false` | `true` | `runtime-effects` | `KGPU-M7-001`, `KGPU-M11-001`, `KGPU-M11-002` | `runtime-effect legacy` |
| [KGPU-M11-009 - Add paint dictionary and blend-plan execution boundary](KGPU-M11-009-add-paint-dictionary-and-blend-plan-execution-boundary.md) | `done` | `P1` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `paint-blend` | `KGPU-M7-003`, `KGPU-M11-002`, `KGPU-M11-005` | `blend legacy` |

## Validation Bundle

```bash
rtk git diff --check
```

## Non-Claims

- No product route activation.
- No Graphite, Ganesh, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
- No broad text/font route expansion.
- No adapter-backed support claim until the relevant ticket links live
  WebGPU/WGPU evidence, readback or explicit skipped evidence, diagnostics, and
  review.
- No CPU-rendered complete draw, layer, filter, or scene texture fallback.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
