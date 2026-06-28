# GPU Renderer M33-M40 Feature Expansion Milestones — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create 8 milestone directories with READMEs and 32 ticket files in `.upstream/specs/gpu-renderer/tickets/`, then update `STATUS.md` and the ticket catalog `README.md`.

**Architecture:** Each milestone directory contains a `README.md` and N ticket files following the templates in `tickets/templates/`. All tickets start `proposed`. Each ticket has YAML frontmatter + 14 body sections (PM Note through Linear Labels). The `STATUS.md` table and catalog `README.md` are extended with M33-M40 rows.

**Tech Stack:** Markdown + YAML frontmatter. Ticket IDs use `KGPU-M<NN>-<NNN>` convention.

**Strategy:** Dispatch one subagent per milestone plus one for cross-cutting updates (9 subagents total, all parallel-safe).

---

## Reference Templates

Milestone README template: `.upstream/specs/gpu-renderer/tickets/templates/milestone-template.md`
Ticket template: `.upstream/specs/gpu-renderer/tickets/templates/ticket-template.md`
Example milestone: `.upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation/README.md`
Example ticket: `.upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation/KGPU-M1-001-decide-first-route-product-activation-policy.md`
Design doc: `docs/superpowers/specs/2026-06-28-gpu-renderer-m33-m40-milestones-design.md`

---

### Task 1: Create M33 milestone — Geometry Hardening

**Required subagent.** Dispatch with this prompt:

```
Create the M33 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/`.

First read these reference files for style:
- `.upstream/specs/gpu-renderer/tickets/templates/milestone-template.md`
- `.upstream/specs/gpu-renderer/tickets/templates/ticket-template.md`
- `.upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation/README.md` (example milestone)
- `.upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation/KGPU-M1-001-decide-first-route-product-activation-policy.md` (example ticket)

Then read the design doc at `docs/superpowers/specs/2026-06-28-gpu-renderer-m33-m40-milestones-design.md` for the M33 section.

Create these files:

**1. `M33-geometry-hardening/README.md`** — milestone README with:
- Goal: "Promote GPU compute tessellation, advanced stroke expansion (complex dash, path effects), and perspective transform acceptance from TargetNative specs to accepted GPUNative routes with evidence."
- Dependencies: M0 (R0-R6 boundary review) and M1 (first-route product activation)
- Exit criteria: compute tessellation with CPU oracle parity, advanced stroke accepted or refused, perspective proven for rect/rrect
- Ticket table with 3 rows (all proposed, P0 except 002/003 P1, all TargetNative except 003 for rect/rrect, all GPUNative except where CPUPreparedGPU fallback, all adapter_required: true, all depend on KGPU-M1-001)
- Validation bundle: `rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:check`
- Non-claims: no product activation, compute tessellation not for all path types, perspective not for text/image

**2. `KGPU-M33-001-gpu-compute-tessellation.md`** — ticket with:
- frontmatter: id=KGPU-M33-001, title="GPU compute tessellation — GPUNative path fill and stroke route", status=proposed, milestone=M33, priority=P0, owner_area=geometry, claim_impact=TargetNative, route_kind=GPUNative, product_activation=false, release_blocking=false, adapter_required=true, depends_on=[KGPU-M1-001], legacy_gate="legacy path fill"
- PM Note (French): "La tessellation GPU via compute shader remplace le chemin CPUPreparedGPU pour le remplissage et le contour de path. Ce ticket prouve la première route GPUNative pour les paths."
- Problem: Currently all path fill/stroke goes through CPUPreparedGPU. Compute tessellation is spec'd in 25-path-stroke-geometry-pipeline.md but no route exists.
- Scope: GPUComputeTessellationPlan (dispatch grid, WGSL compute module, output buffer), GPUComputeTessellationArtifact registered in CPUPreparedGPUArtifactRegistry, route selection (compute when capabilities accept, else CPUPreparedGPU, else RefuseDiagnostic), WGSL compute module validated via wgsl4k, at least one path fill and one path stroke with CPU oracle parity.
- Non-Goals: no general compute scheduler, no CPU flattening port to compute, no MSAA claim from tessellation alone
- Spec Sources: 25-path-stroke-geometry-pipeline.md GPU Compute Tessellation section, 36-implementation-roadmap.md, 07-validation-conformance.md
- Graphite Algorithm References: GFX-TESSELLATION from GRAPHITE-ALGORITHM-REFERENCES.md — fan tessellation and indirect dispatch patterns, algorithm reference only
- Design Sketch: Kotlin data classes for GPUComputeTessellationPlan, GPUComputeTessellationRoute (Accepted/CapabilityUnavailable/Refused)
- Acceptance Criteria: GPUComputeTessellationPlan produces valid GPUComputeTessellationArtifact, compute shader WGSL validates via wgsl4k for at least one path fill, route fallback compute unavailable → CPUPreparedGPU → RefuseDiagnostic, CPU oracle parity for path fill and path stroke
- Required Evidence: GPUComputeTessellationPlan deterministic dump, WGSLComputeModule validation report, GPUComputePipelineKey preimage, CPU oracle comparison for path fill + stroke, refusal fixtures (vertex budget exceeded, compute capabilities absent)
- Fallback/Refusal: compute unavailable → CPUPreparedGPU(PrecomputedGeometryArtifact), vertex budget exceeded → unsupported.tessellation.vertex_budget_exceeded, WGSL invalid → unsupported.tessellation.wgsl_validation, no CPU-rendered texture fallback
- Dashboard: row=gpu-renderer.geometry.compute-tessellation, classification=TargetNative
- Validation: `rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ComputeTessellation*'`
- Status Notes: proposed — Initial ticket. Awaiting M33 milestone acceptance.
- Linear Labels: gpu-renderer, milestone:M33, area:geometry

**3. `KGPU-M33-002-advanced-stroke-path-effects.md`** — ticket with:
- frontmatter: id=KGPU-M33-002, title="Advanced stroke expansion — complex dash and path-effect chain", status=proposed, milestone=M33, priority=P1, owner_area=geometry, claim_impact=TargetNative, route_kind=GPUNative, product_activation=false, release_blocking=false, adapter_required=true, depends_on=[KGPU-M1-001], legacy_gate="legacy stroke"
- PM Note (French): "Les strokes complexes (dash patterns > 2 éléments, chaînes de path effects) étaient refusés. Ce ticket active leur acceptation conditionnelle ou leur refus stable avec diagnostics."
- Problem: GPUStrokeExpansionPlan currently refuses complex dashes and path-effect chains. 25-path-stroke-geometry-pipeline.md defines contracts as TargetNative but without implementation.
- Scope: GPUComplexDashPlan (dash array, phase, classification simple/complex), GPUPathEffectChainPlan (ordered chain of path-effect descriptors), GPUStrokeStyleCompositionPlan (width+cap+join+miter+dash+path-effect → executable expansion), routes: simple dash → GPUNative, complex dash → GPUNative or CPUPreparedGPU, path-effect chain → CPUPreparedGPU or RefuseDiagnostic
- Non-Goals: not all Skia path effects, no full CPU stroke rendering into texture
- Spec Sources: 25-path-stroke-geometry-pipeline.md Advanced Stroke Expansion, 36-implementation-roadmap.md
- Graphite Algorithm References: GFX-STROKE-STYLE — stroke expansion and dash decomposition, algorithm reference only
- Design Sketch: Kotlin data classes for GPUComplexDashPlan, GPUDashClassification enum (SimpleRepeat, ComplexPattern, UnsupportedLength), GPUPathEffectChainPlan
- Acceptance Criteria: 4-element dash with phase offset accepted or refused with stable reason, corner+discrete path-effect chain accepted or refused, dash+path-effect combination produces correct stroke with CPU oracle parity
- Required Evidence: GPUComplexDashPlan dumps (simple+complex), GPUPathEffectChainPlan dump (2-effect chain), GPUStrokeStyleCompositionPlan dump, CPU oracle comparison for complex dash case, refusal fixtures (dash length exceeded, path-effect depth exceeded)
- Fallback/Refusal: dash length exceeded → unsupported.stroke.dash_pattern_length, path-effect depth exceeded → unsupported.stroke.path_effect_chain_depth, no CPU texture fallback
- Dashboard: row=gpu-renderer.geometry.advanced-stroke, classification=TargetNative
- Validation: `rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*AdvancedStroke*'`
- Status Notes: proposed — Initial ticket.
- Linear Labels: gpu-renderer, milestone:M33, area:geometry

**4. `KGPU-M33-003-perspective-transform-acceptance.md`** — ticket with:
- frontmatter: id=KGPU-M33-003, title="Perspective transform acceptance for rect/rrect geometry", status=proposed, milestone=M33, priority=P1, owner_area=coordinates, claim_impact=TargetNative, route_kind=GPUNative, product_activation=false, release_blocking=false, adapter_required=true, depends_on=[KGPU-M1-001], legacy_gate="legacy drawRect"
- PM Note (French): "Les transformations perspective étaient systématiquement refusées. Ce ticket active l'acceptation conditionnelle pour rect/rrect + solid color, avec preuves de bounds projectives conservatives."
- Problem: 30-coordinate-transform-bounds-policy.md defines GPUPerspectiveTransformPlan and GPUPerspectiveBoundsProof as TargetNative but current route refuses all perspective. Rects/rrects are the natural first candidate (4 corners → projection → bounds box).
- Scope: GPUPerspectiveTransformPlan (4x4 matrix classification, finite determinant, w-divide facts), GPUPerspectiveBoundsProof (4-corner projection → conservative device bounds), route: perspective+rect/rrect+solid → GPUNative with homogeneous divide in vertex shader, routes that refuse: path (unproven curve reprojection), text, image, filter, layer
- Non-Goals: no general 3D clipping, no perspective-correct attribute interp before consuming route, no perspective claim for text/image/filter/layer
- Spec Sources: 30-coordinate-transform-bounds-policy.md Perspective Acceptance Policy, 25-path-stroke-geometry-pipeline.md
- Graphite Algorithm References: GFX-TRANSFORM — matrix classification, homogeneous divide, conservative bounds projection, algorithm reference only
- Design Sketch: Kotlin data classes for GPUPerspectiveTransformPlan, GPUPerspectiveBoundsProof, GPUPerspectiveRouteAcceptance enum (Accepted, RefusedAffineOnly, RefusedDegenerate)
- Acceptance Criteria: rect+solid+perspective → correct with CPU oracle parity, rrect+solid+perspective → correct, path+perspective → RefuseDiagnostic with unsupported.transform.perspective_route_rejected.path, behind-camera → RefuseDiagnostic, near-zero determinant → RefuseDiagnostic
- Required Evidence: GPUPerspectiveTransformPlan dump, GPUPerspectiveBoundsProof dump for 4 projected corners, CPU oracle comparison for perspective rect, refusal fixtures (path+perspective, behind-camera, degenerate projection)
- Fallback/Refusal: affine-only route in chain → unsupported.transform.perspective_route_rejected.<name>, behind-camera → unsupported.transform.perspective_degenerate, no CPU texture fallback
- Dashboard: row=gpu-renderer.coordinates.perspective-acceptance, classification=TargetNative
- Validation: `rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerspectiveTransform*'`
- Status Notes: proposed — Initial ticket.
- Linear Labels: gpu-renderer, milestone:M33, area:coordinates

Write all 4 files. Commit each file separately with messages like "tickets: add M33 geometry hardening milestone README", "tickets: add KGPU-M33-001 gpu compute tessellation", etc.
```

- [ ] **Dispatch subagent for Task 1**

---

### Task 2: Create M34 milestone — Text Breadth

**Required subagent.** Dispatch with this prompt:

```
Create the M34 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M34-text-breadth/`.

First read these reference files for style:
- `.upstream/specs/gpu-renderer/tickets/templates/milestone-template.md`
- `.upstream/specs/gpu-renderer/tickets/templates/ticket-template.md`
- `.upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation/README.md`
- `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/README.md` (after Task 1 completes)
- `.upstream/specs/gpu-renderer/tickets/M33-geometry-hardening/KGPU-M33-001-gpu-compute-tessellation.md` (for ticket style)

Read the design doc at `docs/superpowers/specs/2026-06-28-gpu-renderer-m33-m40-milestones-design.md` for the M34 section.

Create 6 files:

**1. `M34-text-breadth/README.md`** — milestone README with:
- Goal: "Promote subpixel LCD rendering, color font/emoji support, variable font instances, complex shaping integration, and font fallback chain from specs to accepted routes with evidence."
- Dependencies: M0-M1 baseline. M34-002 through M34-005 depend on pure Kotlin text stack output artifacts. M34-001 depends on adapter pixel geometry query.
- Exit criteria: subpixel LCD route with CPU oracle parity, at least one COLRv0 color font rendered, variable font instances produce correct resolved glyphs, complex shaping and BiDi correctly consumed by GPU renderer, fallback chain produces correct subrun splits
- Ticket table with 5 rows: M34-001 (P0, TargetNative), M34-002 (P0, DependencyGated), M34-003 (P1, DependencyGated), M34-004 (P1, DependencyGated), M34-005 (P1, TargetNative). All GPUNative, all depend on KGPU-M1-001, owner_area=text, product_activation=false, adapter_required=true
- Validation bundle and non-claims as per milestone template

**2. `KGPU-M34-001-subpixel-lcd-rendering.md`** — Subpixel LCD rendering
- PM Note: "Le rendu LCD subpixel (RGB/BGR) améliore la netteté du texte sur écrans à géométrie de pixels connue."
- Problem: GPU renderer only supports A8 and SDF text. Subpixel LCD is TargetNative in 21-text-glyph-pipeline.md but unimplemented.
- Scope: GPUSubpixelLCDPlan (pixel geometry RGB/BGR horizontal, VRGB/VBGR vertical), GPUSubpixelCoverageMask (per-component R,G,B alpha atlas entry), GPUSubpixelLCDRenderStep (WGSL render step, per-component alpha modulation), route activated only when adapter reports pixel geometry AND target format is compatible
- Non-Goals: no rotated display support, no translucent destination without destination-read
- Spec Sources: 21-text-glyph-pipeline.md Subpixel LCD Rendering
- Design Sketch: GPUSubpixelLCDPlan, GPUPixelGeometry enum (RGBHorizontal, BGRHorizontal, VRGBAVertical, VBGRAVertical)
- Acceptance Criteria: at least one A8 glyph run promoted to RGB subpixel with CPU oracle parity, rgba8unorm target accepted others refused, adapter without pixel geometry → RefuseDiagnostic, opaque-only destination accepted
- Required Evidence: GPUSubpixelLCDPlan dump, CPU oracle comparison, refusal fixtures (rotated display, unknown pixel geometry, translucent destination), WGSL validation
- Fallback/Refusal: unknown pixel geometry → unsupported.text.subpixel_pixel_geometry, incompatible target → unsupported.text.subpixel_target_format
- All other template sections filled as per M33-001 pattern

**3. `KGPU-M34-002-color-font-pipeline.md`** — Color font pipeline (COLRv0/v1, CBDT/CBLC, SVG)
- PM Note: "Les polices couleur sont DependencyGated en attendant les artefacts du text stack pure Kotlin."
- Problem: Color fonts are TargetNative/DependencyGated in 21-text-glyph-pipeline.md but text stack must first produce typed artifacts
- Scope: GPUColorGlyphLayerPlan (COLRv0/v1 layers), GPUColorGlyphCompositePlan, GPUCBDTCBLCGlyphPlan, GPUSVGOpenTypeGlyphPlan, GPUEmojiFallbackPlan
- Non-Goals: no COLR/CBDT/SVG parsing in :gpu-renderer, no color font support claim before text stack promotion
- Design Sketch: GPUColorGlyphLayerPlan with layers list, GPUColorFontVersion enum (COLRv0, COLRv1, Unsupported)
- Acceptance Criteria: contracts defined and dumpable, COLRv0 layer tree rasterized, CBDT/CBLC decoded via 22-image-bitmap-codec-pipeline.md, unsupported format → stable refusal
- Required Evidence: GPUColorGlyphLayerPlan dump, GPUCBDTCBLCGlyphPlan dump, refusal fixtures (COLRv1, SVG, layer count exceeded)
- Fallback/Refusal: unsupported format → unsupported.text.color_font.format_unavailable, layer count > max → unsupported.text.color_font.layer_count
- DependencyGated claim impact

**4. `KGPU-M34-003-variable-font-support.md`** — Variable font support
- PM Note: "Les variable fonts sont résolues par le text stack ; le GPU renderer voit des glyphs statiques."
- Scope: GPUVariableFontInstancePlan (per-run axis values), route: axis values consumed by text stack, GPU receives resolved GlyphArtifactPlan, diagnostic for out-of-range values
- Non-Goals: no outline generation in :gpu-renderer, no HarfBuzz/FreeType dependency
- Design Sketch: GPUVariableFontInstancePlan with axes list, GPUVariableFontAxis (tag, value, precision)
- DependencyGated claim impact

**5. `KGPU-M34-004-complex-shaping-integration.md`** — Complex shaping (Arabic, Devanagari, CJK, BiDi)
- PM Note: "Le shaping complexe est délégué au text stack pure Kotlin. Le GPU renderer consomme les métadonnées de shaping comme faits immuables."
- Scope: GPUShapingIntegrationContract (script, language, direction, BiDi levels, cluster map), GPUBiDiRunPlan, GPUScriptComplexityClass (Simple, Complex, CJK)
- Non-Goals: no shaping/BiDi/script detection in :gpu-renderer, no ICU/HarfBuzz/CoreText
- Design Sketch: GPUShapingIntegrationContract with direction enum (LTR, RTL, TTB), GPUScriptComplexityClass enum
- DependencyGated claim impact

**6. `KGPU-M34-005-font-fallback-chain.md`** — Font fallback chain
- PM Note: "Quand la police primaire manque un glyph, la chaîne de fallback sélectionne une police alternative."
- Scope: GPUFallbackGlyphPlan (per-glyph provenance: original font, fallback font, reason), GPUFallbackBatchPolicy (subrun splitting by fallback identity), exhausted chain → unsupported.text.fallback_exhausted
- Non-Goals: no fallback selection logic in :gpu-renderer
- Design Sketch: GPUFallbackGlyphPlan with GPUFallbackReason enum (MissingGlyph, UnsupportedScript, ColorFontPreference)
- TargetNative claim impact

Write all 6 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 2**

---

### Task 3: Create M35 milestone — Color Fidelity

**Required subagent.** Dispatch with this prompt:

```
Create the M35 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M35-color-fidelity/`.

Read reference files as in Task 1, then read design doc M35 section. Create 5 files:

**1. `M35-color-fidelity/README.md`** — milestone README with:
- Goal: "Promote HDR transfer functions, wide-gamut working spaces, gain map pipeline, and ICC profile parsing from TargetNative specs to accepted routes with evidence."
- Dependencies: M0-M1 baseline. M35-003 depends on codec support for Ultra HDR JPEG gain map metadata.
- 4 tickets: M35-001 (P0, HDR transfer functions), M35-002 (P0, wide-gamut), M35-003 (P1, gain map), M35-004 (P1, ICC parsing). All owner_area=color, all TargetNative, all GPUNative, all depend on KGPU-M1-001.

**2. `KGPU-M35-001-hdr-transfer-functions.md`** — HDR (PQ, HLG, scRGB)
- PM Note: "Les fonctions de transfert HDR (PQ, HLG, scRGB) permettent le rendu HDR natif sur cibles compatibles."
- Scope: GPUHDRTransferFunctionPlan (PQ/HLG/scRGB descriptor), GPUHDREOTFPlan (GPU-side display mapping), GPUHDRToneMapPlan (Reinhard/ACES/Hable tone mapping when content > display), route selection (HDR image → transfer function → EOTF → tone map → HDR target or SDR fallback)
- Non-Goals: no HDR on SDR-only targets beyond tone-mapped fallback
- Spec Sources: 29-color-management-pipeline.md HDR Transfer Functions
- Design Sketch: GPUHDRTransferFunctionPlan with transfer function enum (PQ, HLG, scRGBLinear), GPUHDRToneMapPlan with strategy enum (Reinhard, ACES, Hable, Custom)
- Acceptance Criteria: at least one PQ-encoded image decoded with correct EOTF + GPU evidence, HLG scene-referred mapped to display, scRGB linear float rendered without artifacts, HDR-to-SDR tone mapping with CPU oracle parity, WGSL color transform helpers validated via wgsl4k
- Required Evidence: GPUHDRTransferFunctionPlan dump, GPUHDREOTFPlan dump, GPUHDRToneMapPlan dump, CPU oracle comparison for PQ and HLG, refusal fixtures (HDR on SDR-only target, unsupported transfer function)
- Fallback/Refusal: unsupported transfer → unsupported.color.hdr_transfer_function, no HDR target format → unsupported.color.hdr_target_format

**3. `KGPU-M35-002-wide-gamut-working-spaces.md`** — Wide-gamut (P3, AdobeRGB, Rec.2020)
- PM Note: "Les espaces de travail wide-gamut (Display P3, Adobe RGB, Rec.2020) dépassent le sRGB pour le calcul couleur et le stockage intermédiaire."
- Scope: GPUWideGamutWorkingSpacePlan (P3/AdobeRGB/Rec.2020 primaries), GPUWideGamutConversionPlan (matrix+transfer function pairs), GPUWideGamutIntermediateFormat (rgba16float or rgba32float)
- Acceptance Criteria: Display P3 tagged image decoded and rendered with GPU evidence vs sRGB-clamped fallback, layer/saveLayer wide-gamut preserves color fidelity (CPU oracle DeltaE threshold), gradient interpolation in wide-gamut uses transfer-function-aware interpolation
- Fallback/Refusal: unsupported gamut → unsupported.color.wide_gamut_working_space

**4. `KGPU-M35-003-gain-map-pipeline.md`** — Gain map / Ultra HDR
- PM Note: "Les gain maps (Ultra HDR / Android) permettent le rendu adaptatif HDR/SDR à partir d'une seule image."
- Scope: GPUGainmapDecodePlan (base image + gain map image + metadata), GPUGainmapApplyPlan (per-pixel gain map application in WGSL), GPUGainmapDisplayAdaptationPlan (adaptive to current display headroom)
- Acceptance Criteria: Ultra HDR JPEG decoded with gain map metadata preserved, GPU-side gain map application produces HDR output (CPU oracle parity), adaptive rendering (HDR on HDR target, tone-mapped SDR on SDR target)
- Fallback/Refusal: missing metadata → unsupported.color.gainmap_metadata_missing, unvalidated WGSL → unsupported.color.gainmap_apply_wgsl_unvalidated

**5. `KGPU-M35-004-icc-profile-parsing.md`** — ICC profile parsing
- PM Note: "Le parsing ICC profile passe de RefuseDiagnostic à un pipeline accepté pour les profiles matrix/TRC."
- Scope: GPUICCProfileParsePlan (v2/v4 header+tags), GPUICCProfileTransformPlan (matrix+TRC extracted transform), GPUICCProfileCachePlan (cached by profile bytes hash)
- Non-Goals: no ICC v5, no LUT profiles (A2B0/B2A0), no named color profiles
- Acceptance Criteria: v2 and v4 ICC profiles with matrix/TRC tags parsed and transformed correctly, CPU oracle for at least one ICC profile within DeltaE tolerance, profile cache hit/miss telemetry, stable refusal for ICC v5, LUT profiles, unparseable profiles
- Fallback/Refusal: v5 → unsupported.color.icc_profile_version, LUT → unsupported.color.icc_lut_profile

Write all 5 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 3**

---

### Task 4: Create M36 milestone — Image Pipeline Extension

**Required subagent.** Dispatch with this prompt:

```
Create the M36 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M36-image-pipeline/`.

Read reference files as in Task 1, then read design doc M36 section. Create 5 files:

**1. `M36-image-pipeline/README.md`** — milestone README with:
- Goal: "Promote HEIF/AVIF gate criteria, YUV multi-plan texture routes, mipmap auto-generation, and hardware codec descriptors from specs to accepted routes."
- Dependencies: M0-M1 baseline. M36-001 and M36-004 depend on accepted KanvasImageCodec registry entries.
- 4 tickets: M36-001 (P0, DependencyGated), M36-002 (P0, TargetNative), M36-003 (P1, TargetNative), M36-004 (P1, DependencyGated). All owner_area=images, all GPUNative, all depend on KGPU-M1-001.

**2. `KGPU-M36-001-heif-avif-gate-promotion.md`** — HEIF/AVIF gate criteria
- PM Note: "HEIF et AVIF sont dependency-gated. Ce ticket définit les critères de promotion et les contrats de codec."
- Scope: GPUHEIFCodecDescriptor (HEIF container: still, sequence, grid, tiled), GPUAVIFCodecDescriptor (AV1 still, animated, HDR, gain map, alpha), GPUISOBMFFParsePlan (box hierarchy, item refs, decoder config), promotion gates (KanvasImageCodec registered, capability reports still_decode tier, conformance tier beta+, at least one valid HEIF still + one AVIF still with GPU evidence)
- Non-Goals: no patent-encumbered profile silently accepted
- Fallback/Refusal: codec unregistered → unsupported.image.heif_codec_unregistered / unsupported.image.avif_codec_unregistered
- DependencyGated claim impact

**3. `KGPU-M36-002-yuv-multi-plan-texture.md`** — YUV multi-plan texture route
- PM Note: "Les sources YUV multi-plan (JPEG YCbCr, HEIF 4:2:0, AVIF YUV) sont uploadées en textures GPU séparées et converties en RGB dans WGSL."
- Scope: GPUYUVMultiPlanDescriptor (color space BT.601/709/2020, subsampling 4:2:0/4:2:2/4:4:4, plane count/dims), GPUYUVPlaneUploadPlan (per-plane r8unorm/r16unorm), GPUYUVToRGBCoverterPlan (WGSL fragment: sample Y,U,V planes, matrix conversion + transfer function), GPUYUVSamplingPlan (chroma siting, UV scaling)
- Acceptance Criteria: JPEG YCbCr decoded to YUV planes and GPU-converted matching CPU reference within 1-bit tolerance, at least one 4:2:0 and one 4:4:4 source, stable refusal for unsupported chroma siting, BT.2020 YUV, >3 plane formats
- Fallback/Refusal: unsupported color space → unsupported.image.yuv_color_space, unvalidated WGSL converter → unsupported.image.yuv_converter_wgsl_unvalidated

**4. `KGPU-M36-003-mipmap-auto-generation.md`** — Mipmap auto-generation
- PM Note: "La génération automatique de mipmaps améliore la qualité de minification des textures."
- Scope: GPUImageMipmapGenerationPlan (level count, filter box/tent/Kaiser, GPU generation via blit or compute), GPUImageMipmapBlitPlan (WGPU blit), GPUImageMipmapComputePlan (WGSL compute), GPUImageMipmapCachePlan (cached per upload artifact + filter + format)
- Acceptance Criteria: at least one image with mipmaps rendered with correct minification, mip gen does not regress nearest-sampled images, mip level budget enforced
- Fallback/Refusal: mip count > adapter limit → unsupported.image.mipmap_budget_exceeded

**5. `KGPU-M36-004-hardware-codec-descriptor.md`** — Hardware codec descriptor
- PM Note: "Les codecs hardware/plateforme sont acceptés derrière des descripteurs explicites avec politique de nondéterminisme."
- Scope: GPUHardwareCodecDescriptor (codec ID, implementation kind platform/hardware/hybrid, vendor, version, capability flags, approved profiles), GPUHardwareCodecNondeterminismPolicy (documented sources: driver version, GPU vendor, decode variation), GPUHardwareCodecFallbackPlan (hardware → software fallback with reason code)
- Non-Goals: no Android Bitmap/MediaCodec leakage into :gpu-renderer, hardware decode must produce bit-exact or policy-accepted output vs pure Kotlin reference
- Fallback/Refusal: unapproved codec → unsupported.image.hardware_codec_unapproved, nondeterministic without policy → unsupported.image.hardware_codec_nondeterministic
- DependencyGated claim impact

Write all 5 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 4**

---

### Task 5: Create M37 milestone — Filter Breadth

**Required subagent.** Dispatch with this prompt:

```
Create the M37 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M37-filter-breadth/`.

Read reference files as in Task 1, then read design doc M37 section. Create 7 files:

**1. `M37-filter-breadth/README.md`** — milestone README with:
- Goal: "Promote multi-pass separable blur, morphology, drop shadow, lighting, displacement map, and tile-based filter evaluation from TargetNative specs to accepted routes with evidence."
- Dependencies: M0-M1 baseline. M37-003 reuses M37-001 blur contracts. M37-006 depends on filter intermediate texture budgets.
- 6 tickets: M37-001 (P0), M37-002 (P0), M37-003 (P1, depends on M37-001), M37-004 (P1), M37-005 (P1), M37-006 (P1). All owner_area=filters, all TargetNative, all GPUNative, all depend on KGPU-M1-001.

**2. `KGPU-M37-001-multi-pass-separable-blur.md`** — Multi-pass separable blur
- PM Note: "Le blur séparable multi-pass (horizontal puis vertical) avec qualité paramétrable."
- Scope: GPUSeparableBlurPlan (kernel size, sigma, 2-pass default, intermediate texture, tile mode), GPUBlurPassPlan (per-pass horizontal/vertical, precomputed kernel weights, render step), GPUBlurKernelCachePlan (cached weights per sigma), GPUBlurIntermediateArtifact, GPUBlurQualityLevel (Fast 5-tap box, Normal sigma-dependent, High sigma*3 Gaussian)
- Route: GPUFilterNodePlan.Blur → GPUBlurQualityLevel → GPUSeparableBlurPlan (sigma>0, kernel<=max) → horizontal pass → GPUBlurIntermediateArtifact → vertical pass
- Acceptance Criteria: blur sigma=2.0 and sigma=10.0 with CPU oracle parity, Normal and High quality tiers visually correct, separable and non-separable matching within tolerance, sigma=0 → elision
- Fallback/Refusal: sigma out of range → unsupported.filter.blur_sigma_range, intermediate budget exceeded → unsupported.filter.blur_intermediate_budget

**3. `KGPU-M37-002-morphology-filter.md`** — Morphology (dilate/erode)
- PM Note: "Filtres de morphologie dilate (max) et erode (min) avec noyaux rectangulaires ou circulaires."
- Scope: GPUMorphologyPlan (dilate/erode, kernel radius X/Y, rect/circle/ellipse shape), GPUMorphologyPassPlan (single-pass or separable two-pass), GPUMorphologySamplingPlan (gather N samples, min/max reduce)
- Acceptance Criteria: dilate radius 3 and erode radius 3 with CPU oracle parity, rectangular kernel (radius_x != radius_y) correct, radius=0 → elision
- Fallback/Refusal: radius > sample budget → unsupported.filter.morphology_radius_budget

**4. `KGPU-M37-003-drop-shadow-filter.md`** — Drop shadow
- PM Note: "Drop shadow natif GPU: extraction du masque alpha, blur, offset, composite derrière la source."
- Scope: GPUDropShadowPlan (offset dx/dy, sigma X/Y, color, shadow-only or composite mode), GPUDropShadowMaskPlan (extracted alpha from source), GPUDropShadowBlurPlan (reuses GPUSeparableBlurPlan), GPUDropShadowCompositePlan (shadow * blurred alpha, then source over shadow)
- Depends on KGPU-M37-001
- Acceptance Criteria: drop shadow offset(5,5) sigma=2.0 black with CPU oracle parity, shadow composited behind source with SrcOver, refusal when blur unavailable
- Fallback/Refusal: blur unavailable → unsupported.filter.drop_shadow_blur_unavailable

**5. `KGPU-M37-004-lighting-filters.md`** — Lighting filters (directional, specular)
- PM Note: "Filtres d'éclairage directionnel et spéculaire avec normal map."
- Scope: GPULightingPlan (type directional/point/spot/specular, direction/position, surface scale, light color, ambient, specular exponent), GPULightingNormalMapPlan (bump map from alpha gradient or explicit normal map texture), GPULightingWGSL (Phong/Blinn-Phong WGSL fragment)
- Acceptance Criteria: directional lighting with bump map correct (CPU oracle parity), specular lighting with normal map texture, spot lights refused initially
- Fallback/Refusal: missing normal source → unsupported.filter.lighting_normal_source_missing

**6. `KGPU-M37-005-displacement-map-filter.md`** — Displacement map
- PM Note: "Filtre de displacement map: déplace les pixels source selon une texture de déplacement."
- Scope: GPUDisplacementMapPlan (channel select R/G/B/A for X/Y, scale X/Y, tile mode for OOB reads), GPUDisplacementSamplingPlan (sample source at coord+displacement*scale)
- Acceptance Criteria: displacement R=X, G=Y channels scale=10 correct pixel shift (CPU oracle), edge clamp and repeat tile modes correct
- Fallback/Refusal: missing displacement texture → unsupported.filter.displacement_missing_texture

**7. `KGPU-M37-006-filter-tile-based-evaluation.md`** — Filter tile-based evaluation
- PM Note: "Évaluation des filtres par tuiles quand la source dépasse le budget de texture intermédiaire."
- Scope: GPUFilterTilePlan (tile grid, tile size, overlap for separable blur sigma*3), GPUFilterTileRenderPlan (per-tile render to intermediate, composite), GPUFilterTileBudgetPolicy (memory per tile, max tile count)
- Acceptance Criteria: large blur sigma=20 on 4K source tiled 1024x1024 matching non-tiled output, tile overlap correct, refusal when tile < kernel footprint
- Fallback/Refusal: tile smaller than kernel → unsupported.filter.tile_smaller_than_kernel

Write all 7 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 5**

---

### Task 6: Create M38 milestone — Runtime Effects V2

**Required subagent.** Dispatch with this prompt:

```
Create the M38 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M38-runtime-effects-v2/`.

Read reference files as in Task 1, then read design doc M38 section. Create 4 files:

**1. `M38-runtime-effects-v2/README.md`** — milestone README with:
- Goal: "Promote live parameter editing V2, extended effect kinds (Blender/ClipShader/Compute), and dynamic shader graph assembly from TargetNative specs to accepted routes."
- Dependencies: M0-M1 baseline. M38-003 depends on wgsl4k supporting multi-fragment module assembly.
- 3 tickets: M38-001 (P0), M38-002 (P0), M38-003 (P1, depends on wgsl4k). All owner_area=runtimeeffects, all TargetNative, all GPUNative.

**2. `KGPU-M38-001-live-parameter-editing-v2.md`** — Live parameter editing V2
- PM Note: "L'édition live des paramètres d'effets runtime sans recompilation de shader."
- Scope: GPURuntimeEffectLiveParameterSchema (parameter ID, display name, type float/float2/float3/float4/int/color, default, min/max, step), GPURuntimeEffectLiveParameterBinding (maps parameter to uniform byte offset), GPURuntimeEffectLiveState (current values, dirty flags, generation counter), GPURuntimeEffectLiveControlPlan (set by ID, animate, reset, serialize/deserialize preset)
- Acceptance Criteria: at least one registered effect with 3+ live parameters, parameter change between frames correct without pipeline recompilation, dirty-tracking ensures only changed bytes re-uploaded, serialized preset round-trips produce identical output, refusal for effects without live-edit metadata
- Fallback/Refusal: unregistered parameter → unsupported.runtime_effect.live_parameter_unregistered, type mismatch → unsupported.runtime_effect.live_parameter_type_mismatch

**3. `KGPU-M38-002-extended-effect-kinds.md`** — Extended effect kinds (Blender, ClipShader, Compute)
- PM Note: "Le registre d'effets runtime s'étend de Shader/ColorFilter à Blender, ClipShader, et Compute."
- Scope: GPURuntimeEffectKind.Blender (consumes src+dst premul, outputs premul blended), GPURuntimeEffectKind.ClipShader (per-pixel coverage from coords+uniforms), GPURuntimeEffectKind.Compute (compute dispatch with storage buffer I/O), kind-specific contracts (Material consumes coords→unpremul, Blender consumes src+dst→premul result, etc.), kind validation (WGSL entry point matches kind, input/output types match contracts)
- Acceptance Criteria: at least one registered Blender with GPU evidence, one ClipShader with correct coverage (CPU oracle parity), one Compute effect with storage buffer I/O + GPU evidence, kind mismatch → unsupported.runtime_effect.kind_mismatch
- Fallback/Refusal: unregistered kind → unsupported.runtime_effect.kind_not_registered

**4. `KGPU-M38-003-dynamic-shader-graph-assembly.md`** — Dynamic shader graph assembly
- PM Note: "Les effets avec enfants forment un DAG de shaders. Le renderer assemble le module WGSL complet à partir du graphe."
- Scope: GPURuntimeEffectShaderGraph (DAG of effect descriptors, parent+child slots), GPURuntimeEffectShaderGraphAssemblyPlan (topological sort, generate evaluateChild_<slot>() functions, inline uniforms, emit combined module), GPURuntimeEffectShaderGraphBudget (max depth, max children, max WGSL instructions, max uniform buffer size)
- Assembly rules: walk graph, topo sort, inline each node with unique prefix, parent calls evaluateChild_<slot>(coord), combined uniform block merges all uniforms deterministically, combined WGSLModule validated through wgsl4k
- Acceptance Criteria: at least one 2-level shader graph with correct GPU output, cycle detection produces stable refusal before WGSL assembly, budget exceeded produces refusal, graph assembly deterministic (same descriptors → identical WGSL)
- Fallback/Refusal: cycle detected → unsupported.runtime_effect.shader_graph_cycle, depth exceeded → unsupported.runtime_effect.shader_graph_depth_exceeded

Write all 4 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 6**

---

### Task 7: Create M39 milestone — Rendering Architecture

**Required subagent.** Dispatch with this prompt:

```
Create the M39 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M39-rendering-architecture/`.

Read reference files as in Task 1, then read design doc M39 section. Create 5 files:

**1. `M39-rendering-architecture/README.md`** — milestone README with:
- Goal: "Promote MSAA resolve, instanced draw batching, subpass merging, and deferred display list from TargetNative specs to accepted routes."
- Dependencies: M0-M1 baseline.
- 4 tickets: M39-001 (P0, owner_area=state), M39-002 (P0, passes), M39-003 (P1, passes), M39-004 (P1, recording). All TargetNative, all GPUNative, all depend on KGPU-M1-001.

**2. `KGPU-M39-001-msaa-resolve.md`** — MSAA resolve
- PM Note: "Le MSAA (multisample anti-aliasing) améliore la qualité des bords sans surcoût de résolution."
- Scope: GPUMultisamplePlan (sample count 1/4/8, sample mask, alpha-to-coverage), GPUMultisampleResolvePlan (WGPU resolve, custom WGSL, or compute resolve), GPUMultisampleTargetDescriptor (sample count, format compatibility, usage flags), GPUTargetState extended with sampleCount and multisamplePlan, GPURenderPipelineKey includes sampleCount as PipelineStateAffecting axis
- Acceptance Criteria: at least one filled rect at 4x MSAA resolved matching CPU supersampled reference (PSNR threshold), 8x MSAA accepted or refused based on adapter query, alpha-to-coverage tested, stable refusal for MSAA on non-renderable formats, resolve does not corrupt edge colors
- Fallback/Refusal: unsupported sample count → unsupported.target.multisample_count, incompatible format → unsupported.target.multisample_resolve_format

**3. `KGPU-M39-002-instanced-draw-batching.md`** — Instanced draw batching
- PM Note: "Le batching instanced regroupe les draw packets compatibles en un seul draw call GPU."
- Scope: GPUInstancedPacketGroup (N compatible packets: same render step, pipeline key, bind group layout), GPUInstancedUniformStrategy (instance-varying uniforms in single buffer with stride), GPUInstancedVertexStrategy (instanced vertex buffers, divisor=1), GPUInstancedDrawCommand (draw indexed instanced with instanceCount=group size), grouping rules (renderStepIdentifier match, renderPipelineKey match, bindingLayoutKey match, vary-only data in payload slots, no ordering token/dependency edge/atomic group barrier)
- Acceptance Criteria: at least 4 solid-color rect packets batched into one instanced draw, instance-varying uniform data correct, non-batchable packets not grouped, instanced batching does not change pixel output, telemetry reports instanced draw count and batch size distribution
- Fallback/Refusal: incompatible packets → unsupported.stream.instanced_incompatible_packets

**4. `KGPU-M39-003-subpass-merging.md`** — Subpass merging
- PM Note: "Le subpass merging fusionne des render passes producteur/consommateur en une seule passe avec input attachments."
- Scope: GPUSubpassMergePlan (producer pass color attachment, consumer pass input attachment, compatible formats, no intervening barriers), merge conditions (producer color = consumer input, same render pass scope, no copy/upload/barrier/readback between, adapter supports inputAttachment, same sample count)
- Acceptance Criteria: at least one producer-consumer pair (blur horizontal → blur vertical) merged into subpasses with GPU evidence, non-mergeable pair produces stable refusal, subpass merge does not regress pixel output
- Fallback/Refusal: incompatible → unsupported.recording.subpass_merge_incompatible

**5. `KGPU-M39-004-deferred-display-list.md`** — Deferred display list
- PM Note: "La deferred display list permet d'enregistrer une séquence de commandes une fois et de la rejouer sur plusieurs frames."
- Scope: GPUDeferredDisplayList (immutable recorded commands + analysis + layer plans), GPUDeferredDisplayListCompatibilityKey (recording ID, command sequence hash, replay-compatible fields), GPUDeferredDisplayListReplayPlan (apply composed CTM, intersection clip, target substitution, re-execute lightweight analysis, produce new task list), GPUDeferredDisplayListCachePlan (cached per compatibility key + replay CTM class + replay clip class)
- Non-Goals: no public Skia-like API, no cross-device replay, no performance claim without measured evidence
- Acceptance Criteria: recording replayed with different CTM/clip producing correct output, incompatible replay (format change, capability change, device gen mismatch) → refusal
- Fallback/Refusal: incompatible replay → unsupported.recording.deferred_incompatible_replay

Write all 5 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 7**

---

### Task 8: Create M40 milestone — Architecture Capabilities

**Required subagent.** Dispatch with this prompt:

```
Create the M40 milestone directory and all ticket files in `.upstream/specs/gpu-renderer/tickets/M40-architecture-capabilities/`.

Read reference files as in Task 1, then read design doc M40 section. Create 4 files:

**1. `M40-architecture-capabilities/README.md`** — milestone README with:
- Goal: "Promote tile-deferred rendering, multi-threaded recording, and hi-z occlusion culling from TargetNative specs to accepted routes."
- Dependencies: Full R0-R6 completion (recording → submission chain proven). M40-001 is the most complex. M40-002 depends on M40-001 for tile-parallel recording strategy. M40-003 depends on M40-001 for per-tile pyramid interaction.
- 3 tickets: M40-001 (P0, passes), M40-002 (P1, recording, depends on M40-001), M40-003 (P1, analysis, depends on M40-001). All TargetNative, all GPUNative, all depend on KGPU-M1-001 + R0-R6.

**2. `KGPU-M40-001-tile-deferred-rendering.md`** — Tile-deferred rendering
- PM Note: "Le rendu tuilé différé subdivise les grandes cibles en tuiles de taille fixe pour borner la mémoire intermédiaire et permettre le parallelisme."
- Scope: GPUTileGridPlan (tile size 256x256 default, target dims, tile count X/Y, padding), GPUTileGridPolicy (adapter-preferred size, memory budget per tile, min tile count threshold), GPUTileBin (per-tile accumulated draw invocations intersecting tile), GPUTileBinningPass (distribution of accepted draws into per-tile bins after GPUOcclusionTracker), GPUTilePass (per-tile render pass: scissor to tile bounds, sorted packets, tile-private intermediates), GPUTileCompositePass (merge tiles into single target via GPU blit or direct target slice), GPUTileMemoryBudget (per-tile bytes, max concurrent tiles, total pool size = 25% adapter texture memory)
- Two strategies: DirectTargetSlice (tile renders directly into final target sub-rect via scissor+viewport) and TileIntermediateTexture (tile renders to intermediate, composite pass copies to final target)
- Interaction with destination-read: cross-tile destination read refused or deferred to composite pass
- Interaction with clip stencil: atomic groups must not span tiles
- Acceptance Criteria: 2048x2048 target rendered as 8x8 tiles (256x256) with CPU oracle parity, empty tiles culled, tile composite pixel-exact vs single-pass, destination read single-tile accepted cross-tile refused, memory budget enforced, telemetry: tile count, culled count, pass count, composite time
- Fallback/Refusal: budget exceeded → unsupported.tile.budget_exceeded, cross-tile dst read → unsupported.tile.cross_tile_destination_read, cross-tile clip atomic group → unsupported.tile.cross_tile_clip_atomic_group

**3. `KGPU-M40-002-multithreaded-recording.md`** — Multi-threaded recording
- PM Note: "L'enregistrement multi-threadé permet le parallélisme de recording avec fragments mergés déterministiquement."
- Scope: GPURecordingFragment (immutable partial recording from one thread), GPURecordingFragmentMerger (validates ordering tokens, resolves cross-fragment deps, produces single GPURecording), GPUThreadBoundArena (thread-local temp allocations, released on fragment production), GPUMergeOrderingToken (per-command ordering that survives fragment boundaries), GPUConcurrencyTelemetry (fragment count, thread count, merge time, cross-fragment dep count)
- Thread model: GPURecorder not shared across threads, GPUResourceProvider shared + thread-safe, GPUExecutionContext thread-safe read, GPUMaterialDictionary thread-safe read, diagnostics/telemetry thread-safe with atomic counters
- Determinism contract: multi-threaded recording must produce same GPURecording as single-threaded (same MaterialKey, SortKey, payload slot assignment, merge order)
- Parallel strategies: Disjoint Command Ranges (commands[N/2] per thread, no cross-thread ordering needed), Layer-Scope Partitioning (independent saveLayer scopes in parallel), Tile-Parallel Recording (each thread records one tile's bin from M40-001)
- Depends on M40-001
- Acceptance Criteria: at least one recording split into 2 fragments merged correctly with identical output, cross-thread dependency detection blocks correctly, fragment merge deterministic across runs, pipeline cache hit rate not degraded by concurrent misses, thread-bound arena memory released, stable refusal for split inside atomic group/layer scope/dst-read chain
- Fallback/Refusal: unsafe split → unsupported.recording.fragment_split_unsafe, merge cycle → unsupported.recording.fragment_merge_cycle

**4. `KGPU-M40-003-hi-z-occlusion-culling.md`** — Hi-Z occlusion culling
- PM Note: "Le Hi-Z culling utilise une pyramide de profondeur GPU pour éliminer les draws occlus."
- Scope: GPUHiZPyramid (mip chain of depth buffers, each level = max depth of 2x2 block from level below), GPUHiZPyramidBuildPlan (compute pass from Z-prepass depth or previous frame depth), GPUHiZOcclusionTest (project draw bounds to screen, sample highest pyramid level where bounds <= 4 texels, compare conservative min depth vs pyramid max depth), GPUHiZOcclusionResult (Visible/Occluded/Uncertain)
- Source depth: Z-prepass (dedicated depth-only pass before color, most accurate), Previous frame depth (re-projected, faster but inaccurate for moving objects), Hybrid (Z-prepass for moving, prev-frame for static)
- Depth format: depth32float preferred, depth24plus accepted, depth16unorm refused
- Integration: extends GPUOcclusionTracker, culls only opaque draws (not translucent, not saveLayer, not destination-read), ZERO tolerance for false positives
- Tile interaction: per-tile pyramid from tile depth region (cheaper than full-target for large targets)
- Depends on M40-001
- Acceptance Criteria: scene with 50+ opaque rects 50% occluded, Hi-Z reduces rendered draw count by >=40%, Z-prepass depth produces correct pyramid (CPU oracle), ZERO false positives, false negatives measured as culling efficiency %, depth pyramid memory budget enforced
- Fallback/Refusal: unsupported depth format → unsupported.occlusion.depth_format_unsupported, depth not readable as texture → unsupported.occlusion.depth_not_readable

Write all 4 files. Commit each separately.
```

- [ ] **Dispatch subagent for Task 8**

---

### Task 9: Update STATUS.md and tickets README.md

**Required subagent.** Dispatch with this prompt:

```
Update two files after all M33-M40 milestones have been created.

**File 1: `.upstream/specs/gpu-renderer/tickets/STATUS.md`**

Read the file. In the status table, find the M32 row. After it, add these 8 rows:

M33 | 3 | 0 | 0 | 0 | 0 | 0
M34 | 5 | 0 | 0 | 0 | 0 | 0
M35 | 4 | 0 | 0 | 0 | 0 | 0
M36 | 4 | 0 | 0 | 0 | 0 | 0
M37 | 6 | 0 | 0 | 0 | 0 | 0
M38 | 3 | 0 | 0 | 0 | 0 | 0
M39 | 4 | 0 | 0 | 0 | 0 | 0
M40 | 3 | 0 | 0 | 0 | 0 | 0

Update the Total row to: | **Total** | **35** | **0** | **0** | **0** | **14** | **168** |

**File 2: `.upstream/specs/gpu-renderer/tickets/README.md`**

Read the file. In the milestones table, find the M32 row. After it, add:

| M33 | [M33-geometry-hardening](M33-geometry-hardening/README.md) | 3 | Promote GPU compute tessellation, advanced stroke, and perspective transform acceptance. |
| M34 | [M34-text-breadth](M34-text-breadth/README.md) | 5 | Add subpixel LCD, color fonts, variable fonts, complex shaping, and font fallback. |
| M35 | [M35-color-fidelity](M35-color-fidelity/README.md) | 4 | Add HDR transfer functions, wide-gamut spaces, gain maps, and ICC profile parsing. |
| M36 | [M36-image-pipeline](M36-image-pipeline/README.md) | 4 | Promote HEIF/AVIF gates, YUV multi-plan, mipmap auto-gen, and hardware codecs. |
| M37 | [M37-filter-breadth](M37-filter-breadth/README.md) | 6 | Add separable blur, morphology, drop shadow, lighting, displacement, and tile-based filter eval. |
| M38 | [M38-runtime-effects-v2](M38-runtime-effects-v2/README.md) | 3 | Add live editing V2, extended effect kinds, and dynamic shader graph. |
| M39 | [M39-rendering-architecture](M39-rendering-architecture/README.md) | 4 | Add MSAA resolve, instanced batching, subpass merging, and deferred display list. |
| M40 | [M40-architecture-capabilities](M40-architecture-capabilities/README.md) | 3 | Add tile-deferred rendering, multi-threaded recording, and hi-z occlusion culling. |

Commit each file update separately with messages:
- "tickets: add M33-M40 rows to STATUS summary"
- "tickets: add M33-M40 to milestone catalog"
```

- [ ] **Dispatch subagent for Task 9**

---

## Completion Check

- [ ] 8 milestone directories created (`M33-*` through `M40-*`)
- [ ] 8 milestone READMEs with ticket tables, exit criteria, validation bundles
- [ ] 32 ticket files with full YAML frontmatter + 14-section body
- [ ] All tickets `status: proposed`, `product_activation: false`
- [ ] All tickets follow `KGPU-MXX-NNN` ID convention
- [ ] `STATUS.md` includes M33-M40 rows
- [ ] `tickets/README.md` includes M33-M40 in milestones table
- [ ] No `TODO` or `TBD` in any file
