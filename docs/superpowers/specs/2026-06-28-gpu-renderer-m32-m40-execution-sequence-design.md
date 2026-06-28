# M32-M40 GPU Renderer Execution Sequence

**Status:** design
**Date:** 2026-06-28
**Spec sources:** `.upstream/specs/gpu-renderer/README.md`, `.upstream/specs/gpu-renderer/tickets/STATUS.md`, `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Context

M0-M31 are 100% done (168 tickets). The pipeline remaining is:

- **M32** (Legacy gpu-raster Decommission): 2 done, 14 review, 3 proposed — the largest remaining milestone. It deletes `:gpu-raster` after all 12 `GpuRendererLegacyRouteFamily` rows are classified as port-or-refuse, shared infra is relocated, and the retirement gate is authorized.
- **M33-M40**: 35 tickets proposed across 8 advanced milestones (geometry hardening, text breadth, color fidelity, image pipeline, filter breadth, runtime effects v2, rendering architecture, architecture capabilities).

All M33-M40 tickets are `TargetNative` / `GPUNative` routes — aspirational, not yet active product. They depend on M0-M1 baseline (contract shapes, route taxonomy, diagnostics).

## Design Decisions

1. **M32 runs in parallel with M33-M40** — legacy decommission does not gate advanced milestone start.
2. **Priority within M33-M40: M33 > M34 > M35 > rest** — geometry foundation first, then text, then color fidelity, then remaining milestones ordered by dependency chain convenience.
3. **Wave-based execution with 2-4 parallel tracks per wave** — each wave produces completable milestones without cross-track blocking.

## Execution Sequence

### Wave A — M32 Review + M33 Geometry (weeks 1-3)

**Track 1: M32 Per-Family Review (14 review tickets)**

The decision matrix (M32-001, done) classified each of the 12 `GpuRendererLegacyRouteFamily` rows. Each ticket (M32-010 through M32-022) must have its decision verified against evidence.

| Family | Ticket | Decision | Status |
|--------|--------|----------|--------|
| material-paint | M32-010 | port (SolidColor) / refuse (gradients+shader) | review |
| solid-rect-drawpaint | M32-011 | port (complete) | review |
| rounded-rect-gradients | M32-012 | port (solid uniform rrect) / refuse (gradients) | review |
| rect-rrect-stroke | M32-013 | refuse | review |
| device-scissor-simple-clips | M32-014 | port (WideOpen/DeviceRect) / refuse (complex) | review |
| path-fill-stroke | M32-015 | port (fill) / refuse (stroke) | review |
| images-bitmap-codecs-uploads | M32-016 | refuse (dependency-gated) | review |
| savelayer-destination-read-filters | M32-017 | refuse (dependency-gated) | review |
| text-glyphs | M32-018 | port (A8) / refuse (color/SDF/emoji) | review |
| runtime-effects-color-blends | M32-019 | port (SrcOver) / refuse (other blends, color filters) | review |
| vertices-points-meshes | M32-020 | refuse (dependency-gated) | review |
| clear-discard-target-background | M32-021 | port (trivial — surface init) | review |

Plus M32-003 (retirement-gate authorization for all 12 families) and M32-005 (remove legacy device, rollback branch, and module include), also in review.

**Track 2: M33 Geometry Hardening (3 tickets, all proposed)**

| Ticket | Content | Priority |
|--------|---------|----------|
| M33-001 | GPU compute tessellation (path fill + stroke with CPU oracle parity) | P0 |
| M33-002 | Advanced stroke expansion (complex dash, path-effect chain) | P1 |
| M33-003 | Perspective transform acceptance (rect/rrect + solid color; path/text refused) | P1 |

**M33 risks:**
- M33-001 requires a full WGSL compute pipeline — first compute shader usage in `:gpu-renderer`. `wgsl4k` validation for compute entry points may be incomplete.
- M33-002 reuses M33-001 tessellation infrastructure.

### Wave B — M32 Finalization + M34 Text + M35 Color (weeks 3-6)

**Track 1: M32 Legacy Decommission Finalization (3 proposed tickets, sequential)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M32-004 | Relocate shared WGSL/conformance/runtime-shader/gate infra out of `:gpu-raster` | M32-003 done |
| M32-005 | Remove `SkWebGpuDevice`, `useLegacyGpuRaster` rollback branch, and `:gpu-raster` module include | M32-004 done |
| M32-006 | Final decommission validation, evidence bundle, and PR | M32-005 done |

**M32-004 risk:** Relocating shared infrastructure may break imports across other Gradle modules.

**Track 2: M34 Text Breadth (5 tickets, all proposed)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M34-001 | Subpixel LCD rendering (adapter pixel geometry query) | M0-M1 |
| M34-002 | Color font pipeline (COLRv0 minimum) | pure-kotlin-text artifacts |
| M34-003 | Variable font support | pure-kotlin-text artifacts |
| M34-004 | Complex shaping integration (BiDi) | pure-kotlin-text artifacts |
| M34-005 | Font fallback chain | pure-kotlin-text artifacts |

**M34 risk:** 4 of 5 tickets depend on pure-kotlin-text stack artifacts. If those are not delivered, M34-002 through M34-005 become `blocked`. Only M34-001 is autonomous.

**Track 3: M35 Color Fidelity (4 tickets, all proposed)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M35-001 | HDR transfer functions (PQ, HLG, scRGB with EOTF and tone map evidence) | M0-M1 |
| M35-002 | Wide-gamut working spaces (Display P3, Adobe RGB, Rec.2020) | M0-M1 |
| M35-003 | Gain map pipeline (decode, apply, display-adapt) | codec Ultra HDR JPEG |
| M35-004 | ICC profile parsing (v2/v4 matrix/TRC; v5/LUT refused) | M0-M1 |

**M35 risk:** M35-003 is codec-gated. M35-001 is the heaviest ticket (PQ/HLG/scRGB EOTF + tone mapping in WGSL).

### Wave C — Image + Filter + Runtime V2 + Rendering Architecture (weeks 6-10)

**Track 1: M36 Image Pipeline (4 tickets)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M36-001 | HEIF/AVIF gate promotion | KanvasImageCodec registry |
| M36-002 | YUV multi-plan texture route (GPU-converted RGB within tolerance) | M0-M1 |
| M36-003 | Mipmap auto-generation (correct minification) | M0-M1 |
| M36-004 | Hardware codec descriptor (nondeterminism policy) | KanvasImageCodec registry |

**M36 risk:** M36-001 and M36-004 are `DependencyGated` on KanvasImageCodec registry entries. M36-002 and M36-003 are autonomous.

**Track 2: M37 Filter Breadth (6 tickets)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M37-001 | Multi-pass separable blur (H+V passes, quality tiers, intermediate textures) | M0-M1 |
| M37-002 | Morphology filter (dilate/erode with rect and circular kernels) | M0-M1 |
| M37-003 | Drop shadow filter (reuses M37-001 blur + SrcOver composite) | M37-001 |
| M37-004 | Lighting filters (directional + specular with bump/normal map sources) | M0-M1 |
| M37-005 | Displacement map filter (channel-select offset sampling with tile modes) | M0-M1 |
| M37-006 | Filter tile-based evaluation (tiled sub-renders with overlap matching) | M0-M1 |

**M37 requirements:**
- `RectOnlyOffscreenRenderer` must be extended to accept new command families
- Validation scenes must produce PNG evidence in `reports/gpu-renderer-scenes/`
- M37-001 → M37-003 is a hard chain

**Track 3: M38 Runtime Effects V2 (3 tickets)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M38-001 | Live parameter editing V2 (dirty-tracking, preset round-trip) | M0-M1 |
| M38-002 | Extended effect kinds (Blender/ClipShader/Compute) | M0-M1 |
| M38-003 | Dynamic shader graph assembly (cycle detection, deterministic WGSL) | wgsl4k multi-fragment support |

**M38 risk:** M38-003 is gated on `wgsl4k` multi-fragment module assembly.

**Track 4: M39 Rendering Architecture (4 tickets, mutually independent)**

| Ticket | Content | Depends on |
|--------|---------|------------|
| M39-001 | MSAA resolve (4x PSNR, 8x per adapter, alpha-to-coverage) | M0-M1 |
| M39-002 | Instanced draw batching (compatible packet grouping, pixel-identical output) | M0-M1 |
| M39-003 | Subpass merging (compatible producer-consumer fusion) | M0-M1 |
| M39-004 | Deferred display list (replay with different CTM/clip) | M0-M1 |

**M39 note:** All 4 tickets are independent. M39-001 (MSAA) is the heaviest — requires per-adapter GPU test evidence.

### Wave D — M40 Architecture Capabilities (weeks 10-13)

| Ticket | Content | Depends on |
|--------|---------|------------|
| M40-001 | Tile-deferred rendering (fixed-size tiles, empty-tile culling, pixel-exact composite parity vs single-pass) | M0-M1 |
| M40-002 | Multi-threaded recording (thread-bound arenas, deterministic merged fragments, validated ordering tokens) | M40-001 |
| M40-003 | Hi-Z occlusion culling (40%+ draw elimination, zero false positives, enforced memory budget) | M40-001 |

**M40 note:** Strict chain — M40-001 must land before M40-002 and M40-003. This is the heaviest architectural milestone, touching the core execution pipeline (recording, passes, analysis). Reuses specs 38-40 from `.upstream/specs/gpu-renderer/`.

## Dependency Map

```
Wave A                Wave B                Wave C                Wave D
───────────────────── ───────────────────── ───────────────────── ──────────────────
M32 review (14) ──→ M32 finalize (3) ✓
M33 geometry (3)                          ──→ M40 tile-deferred (1)
M34 text (5) ──────→ M36 image (4)           ──→ M40 MT recording (1)
                   ──→ M37 filter (6)         ──→ M40 Hi-Z (1)
M35 color (4) ─────→ M38 runtime v2 (3)
                   ──→ M39 rendering arch (4)
```

`──→` = sequential dependency within wave
`──→` = cross-wave dependency or logical ordering

## Constraints

- All M33-M40 tickets require WGSL validation through `wgsl4k`
- No CPU-rendered texture fallback for any promoted route
- Every accepted route requires CPU/GPU evidence or explicit refusal with stable diagnostics
- `:gpu-renderer` module must remain pure — no direct `SkPaint`, `SkShader`, `SkPath` dependencies
- Runtime effects supported only through registered Kanvas descriptors with Kotlin/CPU behavior and parser-validated WGSL GPU implementations

## Non-Goals

- This sequence does not cover the pure-kotlin-text stack or KanvasImageCodec registry deliverables
- Does not assign estimated hours per ticket
- Does not define who works on which track

## Terminal State

After Wave D, all 41 milestones (M0-M40) are `done`. Total: 217 tickets completed, `:gpu-raster` deleted, advanced GPU capabilities accepted with evidence.
