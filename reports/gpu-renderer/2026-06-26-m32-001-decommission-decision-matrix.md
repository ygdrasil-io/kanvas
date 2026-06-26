# KGPU-M32-001 — Per-Family Decommission Decision Matrix

> Authority for Phase 2 port-vs-refuse ticket scope. Each row verified against live
> code (not the stale M31-005 report). All 12 `GpuRendererLegacyRouteFamily` values
> are accounted for; the enum has exactly 12 members as of the checked snapshot.

## Status

**in-progress** — decisions recorded; Phase 2 tickets not yet created.

## Method

Files read for dispatch-status verification:

| File | Key lines | Purpose |
|---|---|---|
| `gpu-raster/.../GpuRendererShadowParityGates.kt` | 9–68 | 12-family enum (authoritative list) |
| `kanvas/.../Surface.kt` | 147–153 | `when` dispatch: FillRect, FillRRect, FillPath, DrawTextRun |
| `kanvas/.../Surface.kt` | 172–224 | `dispatchFillRect` — SolidColor/Identity/WideOpen+DeviceRect/Root/SrcOver only; ImageDraw refused |
| `kanvas/.../Surface.kt` | 226–304 | `dispatchFillRRect` — same constraints + uniform radii required |
| `kanvas/.../Surface.kt` | 324–430 | `dispatchFillPath` — same constraints + tessellated vertices |
| `kanvas/.../Surface.kt` | 432–466 | `dispatchDrawTextRun` — delegates to TextRunDispatchPlanner |
| `kanvas/.../TextRunDispatch.kt` | 91–148 | TextRunDispatchPlanner — A8 atlas glyph quads with CPU oracle |
| `kanvas/src/test/.../TextGpuEvidenceMain.kt` | 1–97 | A8 DrawTextRun GPU↔CPU parity proven (post-M31-005 landing) |
| `kanvas/.../Canvas.kt` | 36–76, 83–217 | Canvas→NormalizedDrawCommand lowering (all 4 command types) |
| `reports/.../2026-06-26-M31-005-fillrrect-evidence.md` | 1–202 | M31-005: FillRect 100%, FillRRect 99.84%, FillPath 100%, DrawImage refused; **DrawTextRun "refuse" claim is stale** |
| `gpu-raster/.../GpuRendererLegacyRetirementGates.kt` | 1–296 | Retirement gate shape (Phase 3 reference) |

**Note on family count:** The plan document references "13 families" but the actual
`GpuRendererLegacyRouteFamily` enum at `GpuRendererShadowParityGates.kt:9–68`
contains exactly 12 values. All 12 are included below. No 13th family exists.

## Dispatch Constraints (unified)

All 4 dispatched command types share these acceptance criteria (verified against
`Surface.kt` dispatch functions and `TextRunDispatchPlanner`):

| Constraint | Accepted | Refused |
|---|---|---|
| Material | `SolidColor` | `LinearGradient`, `RadialGradient`, `SweepGradient`, `ImageDraw` |
| Transform | `Identity` | any other `GPUTransformType` |
| Clip | `WideOpen`, `DeviceRect` | any other `GPUClipKind` |
| Layer | `Root` | any other `GPULayerScopeKind` |
| Blend | `SrcOver` | any other blend mode |
| RRect radii | uniform only | non-uniform |
| Text glyph kind | A8 atlas | color/SDF/emoji |

## Per-Family Decision Matrix

| # | familyId | displayName | Replacement Ticket | Current Kanvas Dispatch Status | Decision | Required Retirement Evidence |
|---|---|---|---|---|---|---|
| 1 | `material-paint` | Material source / paint pipeline | KGPU-M11-009 | **Partial.** `SolidColor` dispatched across all 4 commands. `LinearGradient`/`RadialGradient`/`SweepGradient` materials lowered in `Canvas.kt:40–71` but refused at dispatch (`unsupported_material`). No shader/material pipeline dispatch exists. | `port` (SolidColor baseline) / `refuse` (gradient + shader pipeline) | SolidColor: no additional evidence (covered by families 2/3/6). Gradient/shader port: independent CPU pixel parity for each gradient type + WGSL gradient dispatch in `Surface.kt`. |
| 2 | `solid-rect-drawpaint` | Solid rect and drawPaint rect fill | KGPU-M1-004 | **Port.** `FillRect` dispatched at `Surface.kt:149,172–224`. Proven: 100% similarity vs independent geometric reference + 100% vs Skia raster (`M31-005` §3, §9). Constraints: SolidColor, Identity, WideOpen/DeviceRect, Root, SrcOver. | `port` | Already complete. No additional evidence needed for retirement — bridge↔Skia raster parity + independent geometric parity committed. |
| 3 | `rounded-rect-gradients` | Rounded rect and simple gradients | KGPU-M2-002 | **Partial.** `FillRRect` dispatched at `Surface.kt:150,226–304`. SolidColor uniform-radii rrect proven at 99.84% vs independent geometric reference (`M31-005` §3). Non-uniform radii refused (`Surface.kt:263–265`). Gradient materials (Linear/Radial/Sweep) refused at dispatch. | `port` (solid uniform rrect) / `refuse` (gradients + non-uniform radii) | Solid rrect: already complete. Gradient rrect: independent CPU pixel parity for each gradient type; WGSL gradient-sampling dispatch in `Surface.kt`. Non-uniform radii: SDF extension with per-corner radii; CPU parity. |
| 4 | `rect-rrect-stroke` | Rect/rrect stroke | KGPU-M3-003 | **Refuse.** No stroke dispatch path exists. `FillRect`/`FillRRect` cover fill only. No `StrokeRect`/`StrokeRRect` command type in `NormalizedDrawCommand` sealed interface (`NormalizedDrawCommand.kt:618–724`). No `dispatchStrokeX` function in `Surface.kt`. | `refuse` | Formal `refuse:` diagnostic for stroke commands + KGPU-M3-003 dependency ticket for future stroke port. Per AGENTS.md: no short-lived substitute. |
| 5 | `device-scissor-simple-clips` | Device scissor and simple clips | KGPU-M2-003 | **Partial.** `WideOpen` and `DeviceRect` clips dispatched in all 4 commands. Other clip kinds (`RoundedRect`, `Path`, `Complex`) refused with `unsupported_clip` diagnostic at `Surface.kt:191,244,342` and `TextRunDispatch.kt:101`. Scissor rect computed from clip bounds (`Surface.kt:213–215`, `270–273`, `387–392`). | `port` (WideOpen/DeviceRect baseline) / `refuse` (complex clips) | WideOpen/DeviceRect: already covered by families 2/3/6/9. Complex clips: independent CPU pixel parity for rounded-rect/path clip; WGSL stencil-clip or discard-clip dispatch in `Surface.kt`. |
| 6 | `path-fill-stroke` | Path fill and path stroke | KGPU-M11-007 | **Partial.** `FillPath` fill dispatched at `Surface.kt:151,324–430` via stencil-cover. Proven 100% vs independent winding reference for triangle + star (`M31-005` §4–5). No path stroke dispatch exists. | `port` (path fill) / `refuse` (path stroke) | Path fill: already complete. Path stroke: independent CPU pixel parity; WGSL stroke dispatch (stencil-cover with stroke expansion or analytic SDF); KGPU-M11-007 dependency ticket. |
| 7 | `images-bitmap-codecs-uploads` | Images, bitmap shaders, codecs, and uploads | KGPU-M11-004 | **Refuse.** `DrawImage` lowered to `FillRect` with `ImageDraw` material at `Canvas.kt:170–187`. `dispatchFillRect` refuses `ImageDraw` with `refuse:...:unsupported_material:ImageDraw` at `Surface.kt:183`. No texture upload or sampling dispatch exists. | `refuse` (dependency-gated) | Formal `refuse:` diagnostic for ImageDraw (already exists). KGPU-M11-004 dependency ticket. Per AGENTS.md: dependency-gated by codec/delivery gaps — no short-lived substitute. |
| 8 | `savelayer-destination-read-filters` | saveLayer, destination read, and filter DAGs | KGPU-M11-006 | **Refuse.** All 4 dispatch functions refuse non-`Root` layers at `Surface.kt:194,248,346` and `TextRunDispatch.kt:103` with `unsupported_layer`. No destination-read dispatch exists. No filter DAG dispatch. `NormalizedDrawCommand` sealed interface has no `SaveLayer`/`Restore` command types. | `refuse` (dependency-gated) | Formal `refuse:` diagnostic for saveLayer/destination-read/filter-DAG commands. KGPU-M11-006 dependency ticket. Per AGENTS.md: dependency-gated — no short-lived substitute. |
| 9 | `text-glyphs` | Text and glyphs | KGPU-M6-002 | **Partial.** A8 `DrawTextRun` dispatched at `Surface.kt:152,432–466` via `TextRunDispatchPlanner` (`TextRunDispatch.kt:91–148`) → `drawFullscreenTextureUniformPass` with `TextAtlasGlyphWgsl`. **A8 GPU↔CPU parity proven** in `TextGpuEvidenceMain.kt:81–91` (PASS real GPU A8 text pixels with CPU parity). Color/SDF/emoji text refused: `TextRunDispatchPlanner` requires `SolidColor` material + atlas glyph plan. The stale M31-005 report marked DrawTextRun "refuse" — **this is incorrect**; A8 text landed post-M31-005. | `port` (A8 text fill) / `refuse` (color/SDF/emoji text) | A8 text: already complete (`TextGpuEvidenceMain.kt` parity). Color/SDF/emoji: dependency-gated by font/color pipeline; KGPU-M6-002 dependency ticket. Per AGENTS.md: no short-lived substitute. |
| 10 | `runtime-effects-color-blends` | Runtime effects, color filters, blends, and color management | KGPU-M11-008 | **Partial.** `SrcOver` blend dispatched implicitly in all coverage scenes (verified SrcOver blend parity in `M31-005` §8). Non-`SRC_OVER` blends refused with `unsupported_blend` diagnostic at `Surface.kt:199,253,351` (test: `non-srcover blend emits refuse diagnostic` PASSED, `M31-005` §8). Color filters: no dispatch exists. Runtime effects: no dispatch exists (`SkRuntimeEffect` is a compatibility facade). | `port` (SrcOver blend baseline) / `refuse` (other blends, color filters, runtime effects, color management) | SrcOver: already covered (implicit in all parity scenes). Other blend modes: independent CPU pixel parity for each blend mode (Porter-Duff); WGSL blend dispatch. Color filters/runtime effects: dependency/spec-gated; KGPU-M11-008 dependency ticket. |
| 11 | `vertices-points-meshes` | Vertices, points, and mesh-like draws | KGPU-M8-003 | **Refuse.** No dispatch exists. `NormalizedDrawCommand` sealed interface has no `DrawVertices`/`DrawPoints`/`DrawMesh` command types. No `dispatchVerticesX` function in `Surface.kt`. | `refuse` (dependency-gated) | Formal `refuse:` diagnostic for vertices/points/mesh commands. KGPU-M8-003 dependency ticket. Per AGENTS.md: dependency/spec-gated — no short-lived substitute. |
| 12 | `clear-discard-target-background` | Clear/discard and target background | route-specific-clear-discard-ticket-required | **Port (trivial).** Surface initializes with `DEFAULT_CLEAR_COLOR` = `(0,0,0,0)` at `Surface.kt:145,468–470` via `t.encode(clearColor=...)` before rendering any command. No explicit `ClearDraw` command type; clear is part of the render target initialization contract. Legacy device equivalent: `SkWebGpuDevice` target clear. | `port` (trivial — surface init) | Document that Kanvas surface init clears to transparent black per `GPUOffscreenTargetRequest` contract; no per-command clear dispatch needed. Evidence: verify `nonTransparentPixels == 0` for an empty surface → no legacy clear route dependency remains. |

## Summary

| Decision | Count | Families |
|---|---|---|
| `port` (complete) | 2 | `solid-rect-drawpaint` (FillRect 100%), `clear-discard-target-background` (trivial) |
| `port` (partial: baseline ported, remainder refused) | 6 | `material-paint` (SolidColor ported, gradients refused), `rounded-rect-gradients` (solid rrect ported, gradients+non-uniform refused), `device-scissor-simple-clips` (WideOpen/DeviceRect ported, complex refused), `path-fill-stroke` (fill ported, stroke refused), `text-glyphs` (A8 ported, color/SDF/emoji refused), `runtime-effects-color-blends` (SrcOver ported, other blends/runtime-effects/color-filters refused) |
| `refuse` (full) | 4 | `rect-rrect-stroke`, `images-bitmap-codecs-uploads`, `savelayer-destination-read-filters`, `vertices-points-meshes` |

**Bottom line:** 8 families have at least one ported pathway (either complete or partial baseline). 4 families are fully refused (no GPU dispatch path exists). Of the 6 partial families, the unported sub-capabilities must each get a Phase 2 formal-refusal ticket + dependency-linked port ticket.

### Uncertainty / Concerns

1. **`material-paint` material pipeline:** The family is named "Material source / paint pipeline" which implies more than SolidColor. The current `port` decision reflects only the SolidColor baseline. Full material pipeline (gradients, bitmap shaders) is `refuse` (dependency-gated). If the intent of this family is strictly the paint pipeline infrastructure, it could be argued `refuse (revisit)`. Honest uncertainty: the scope is ambiguous.

2. **`device-scissor-simple-clips`:** Scissor rect (from clip bounds) is implemented in all dispatch functions, but this isn't a standalone "clip" dispatch — it's embedded in each draw command. A true standalone clip stack dispatch would require saveLayer/restore support. Decision is `port` for the embedded scissor but `refuse` for standalone clip dispatch.

3. **`runtime-effects-color-blends` scope:** This family groups runtime effects, color filters, blends, AND color management. Blends are partially ported (SrcOver); everything else is refused. The family is too broad for a single yes/no decision — Phase 2 should split it.

4. **`clear-discard-target-background` replacement ticket:** RESOLVED 2026-06-26. The `defaultReplacementTicket` was the placeholder `route-specific-clear-discard-ticket-required`. Replaced with `KGPU-M32-022` (`clear-discard-route-ownership`) — the surface-init clear is trivially covered by the Kanvas surface contract (`Surface.kt:145,468-470`). See `KGPU-M32-022-clear-discard-route-ownership.md`.

5. **Text A8 port vs M31-005 stale claim:** The M31-005 evidence report marks DrawTextRun as "refused" (§ Family Coverage Status table). This is stale — A8 text was implemented and proven after that report (see `TextGpuEvidenceMain.kt`). The stale claim must be corrected in Phase 1 (Task 1.2 per the plan).

## Files Referenced

- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/GpuRendererShadowParityGates.kt` — 12-family enum definition
- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/GpuRendererLegacyRetirementGates.kt` — retirement gate shape (Phase 3 reference)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/Surface.kt` — dispatch: FillRect (149,172), FillRRect (150,226), FillPath (151,324), DrawTextRun (152,432); refusal points: 183,199,237,244,265,339,342,346,351,358,378; clear: 145,468
- `kanvas/src/main/kotlin/org/graphiks/kanvas/TextRunDispatch.kt` — TextRunDispatchPlanner (91–148), TextRunCpuOracle (154–195), TextAtlasGlyphWgsl (19–44)
- `kanvas/src/main/kotlin/org/graphiks/kanvas/Canvas.kt` — command lowering: drawRect (83), drawRRect (96,115), drawPath (134), drawImage (170), drawTextBlob (189); gradient materials (40–75)
- `kanvas/src/test/kotlin/org/graphiks/kanvas/TextGpuEvidenceMain.kt` — A8 text GPU↔CPU parity (PASS at 91)
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt` — sealed interface (618–724): FillRect (656), FillRRect (672), FillPath (693), DrawTextRun (722)
- `reports/gpu-renderer/2026-06-26-M31-005-fillrrect-evidence.md` — FillRect 100%, FillRRect 99.84%, FillPath 100%, SrcOver coverage; DrawImage refused; DrawTextRun stale-claim "refuse"
- `docs/superpowers/plans/2026-06-26-legacy-gpu-raster-decommission.md` — full decommission plan (Task 0.2 is this report)
