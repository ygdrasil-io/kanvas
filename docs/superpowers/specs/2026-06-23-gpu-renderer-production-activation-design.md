# GPU Renderer Production Activation — Ticket Wave M12-M23

Date: 2026-06-23
Status: Accepted design
Scope: `:gpu-renderer`, `:gpu-renderer-scenes`, `:gpu-raster`, dependencies (`:font`, `:codec-*`, `wgsl4k`)

## 1. Goal

Activate all GPU renderer draw families as product routes with default-ON
behavior, rollback capability, and scene evidence. Starting from 55 `done`
contract/refusal tickets (M0-M11) and one product-activated route (`FillRect`
solid, M1), expand to production coverage across 12 milestones (M12-M23).

## 2. Constraints

- Do not port Ganesh or Graphite. Use Graphite C++ only as algorithm reference.
- Do not rebuild SkSL compiler, IR, or VM.
- Keep WebGPU via `wgpu4k` as the sole GPU backend.
- Keep WGSL as the shader implementation target.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL descriptors.
- Keep refusal-first discipline: every activation requires rollback parity
  evidence.
- Use existing ticket model: KGPU-M<NN>-<seq> format, same front matter fields,
  PM notes, spec sources, Graphite algorithm references, design sketches,
  acceptance criteria, required evidence, validation commands.
- Hybride Graphite references: exact file+line for critical algorithms
  (tessellation, blend, atlas, stencil), concepts for supporting code.
- Dependencies (text stack, codec, wgsl4k) must be completed first (M12).

## 3. Milestone Map

```
M12 (dependencies) ── séquentiel, doit finir en premier
    │
    ├── M13 (RRect + LinearGradient + Scissor) ──┐
    ├── M15 (Path Fill + Stencil-Cover) ─────────┤ vague 1 (parallèle)
    └── M17 (Image Shader + Codec) ──────────────┘
              │
         ┌────┴────┐
         │         │
    M14 (Radial+Sweep)    M16 (Stroke+Dash)
    M18 (SaveLayer+DR)    M19 (Filter DAG)
              │           vague 2
              │
    M20 (Text A8+SDF) ──┐
    M21 (Runtime Effects)├─ vague 3 (dépendent de M12)
    M22 (Vertices) ──────┘
              │
         M23 (Performance Gates + PM Evidence)
```

## 4. Milestone Details

### M12 — Dependencies (10 tickets)

Prerequisites for all later route activations. Sequential.

#### 12A — Pure Kotlin Text Stack (4 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M12-001 | Finalize SFNT parser + glyf/CFF/CFF2 scaler with deterministic output | `src/gpu/graphite/text/TextAtlasManager.cpp` |
| KGPU-M12-002 | Add A8 glyph rasterizer with strike key + cache invalidation | `src/gpu/graphite/GlyphCache.h` |
| KGPU-M12-003 | Add GPU glyph atlas upload plan with texture region packing | `src/gpu/graphite/AtlasProvider.cpp` |
| KGPU-M12-004 | Wire GPU renderer text handoff: GlyphRunDescriptor → DrawTextRun accepted | `src/gpu/graphite/Device.cpp drawGlyphs` |

Non-claims: Latin glyphs only. No bidi, complex shaping, COLRv1, SVG glyphs.

#### 12B — Codec Pipeline (3 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M12-005 | Add GPU image decode plan: codec selection → decode → RGBA8Unorm pixels | `src/gpu/graphite/ImageUtils.cpp` |
| KGPU-M12-006 | Add GPU texture upload from decoded pixels with format validation | `src/gpu/graphite/TextureProxy.cpp` |
| KGPU-M12-007 | Wire codec provenance into GPUImagePipelinePlan (accept PNG/JPEG/WebP/GIF) | `src/gpu/graphite/ImageProvider.cpp` |

Non-claims: HEIF/AVIF remain dependency-gated. No animated image decode, no HDR.

#### 12C — wgsl4k Parser Integration (3 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M12-008 | Integrate wgsl4k parser into WGSLModuleAssembler for live reflection | `src/gpu/graphite/ShaderUtils.cpp` |
| KGPU-M12-009 | Add WGSL ABI validation: reflected layout vs Kotlin packing byte-match | `src/gpu/graphite/ResourceProvider.cpp` |
| KGPU-M12-010 | Add wgsl4k evolution gate: parser-backed reflection for all first-route WGSL | `src/gpu/graphite/PipelineCache.cpp` |

### M13 — RRect + LinearGradient + Scissor (5 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M13-001 | Add FillRRect execution: analytic rrect coverage WGSL + GPU command stream | `src/gpu/graphite/geom/Rect.h:58`, `src/gpu/graphite/geom/Shape.cpp:42` |
| KGPU-M13-002 | Add LinearGradient material execution: WGSL snippet + uniform layout + payload | `src/gpu/graphite/KeyHelpers.cpp:173`, `src/shaders/graphite/GradientShader.cpp` |
| KGPU-M13-003 | Add scissor clip execution: device-rect clip → WebGPU setScissor + uniform | `src/gpu/graphite/DrawContext.cpp:155` |
| KGPU-M13-004 | Activate M13 routes: FillRRect + LinearGradient + Scissor default ON with rollback | `src/gpu/graphite/Device.cpp drawRect` |
| KGPU-M13-005 | Add gpu-renderer-scenes evidence: rrect-card, gradient-swatch, clipped-stack | `gm/gradients.cpp` |

### M14 — RadialGradient + SweepGradient (3 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M14-001 | Add RadialGradient WGSL: distance-from-center math + tile mode | `src/shaders/graphite/GradientShader.cpp radial` |
| KGPU-M14-002 | Add SweepGradient WGSL: atan2 angle interpolation + tile mode | `src/shaders/graphite/GradientShader.cpp sweep` |
| KGPU-M14-003 | Activate M14 routes: Radial + Sweep gradients default ON with rollback | `src/gpu/graphite/Device.cpp drawPaint` |

Non-claim: TwoPointConical gradient remains refused (deferred to future milestone,
not tracked in M12-M23).

### M15 — Path Fill + Stencil-Cover (4 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M15-001 | Add path tessellation: flatten + fan triangulation → WebGPU vertex buffer | `src/gpu/graphite/geom/Shape.cpp:90`, `src/gpu/tessellate/GrPathTessellation.cpp` |
| KGPU-M15-002 | Add stencil-cover execution: two-pass stencil write + cover resolve with WGSL | `src/gpu/graphite/DrawContext.cpp stencil`, `src/gpu/graphite/render/StencilCoverRenderStep.cpp` |
| KGPU-M15-003 | Add convex fan execution: single-pass analytic AA with triangle list | `src/gpu/graphite/render/AnalyticRRectRenderStep.cpp` |
| KGPU-M15-004 | Activate M15 routes: Path fill native + stencil-cover default ON with rollback | `src/gpu/graphite/Device.cpp drawPath` |

Non-claims: 256-edge budget. No GPU compute tessellation. No path atlas.

### M16 — Stroke + Dash + Clip Expansion (4 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M16-001 | Add stroke expansion: stroke path → fillable contour with join/cap geometry | `src/gpu/graphite/geom/Shape.cpp stroke`, `src/core/SkStroke.cpp` |
| KGPU-M16-002 | Add dash path effect: dash interval decomposition → stroke sub-paths | `src/core/SkDashPathEffect.cpp` |
| KGPU-M16-003 | Add bounded clip expansion: rrect/path clip stacks beyond simple scissor | `src/gpu/graphite/DrawContext.cpp clip` |
| KGPU-M16-004 | Activate M16 routes: Stroke + Dash + bounded clips default ON with rollback | `src/gpu/graphite/Device.cpp drawPath stroke` |

Non-claims: 128-edge stroke budget. No arbitrary path effects beyond dash.

### M17 — Image Shader + Codec Upload (5 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M17-001 | Add BitmapShader WGSL: texture sample + nearest/linear filter + clamp tile | `src/shaders/graphite/ImageShader.cpp`, `src/gpu/graphite/KeyHelpers.cpp image` |
| KGPU-M17-002 | Add BitmapRect execution: image rect draw with texture binding + sampler | `src/gpu/graphite/Device.cpp drawImageRect` |
| KGPU-M17-003 | Add GPU image upload materialization: decoded pixels → staging buffer → texture | `src/gpu/graphite/TextureProxy.cpp:73`, `src/gpu/graphite/UploadTask.cpp` |
| KGPU-M17-004 | Add tile mode expansion: Repeat + Mirror + Decal via WGSL math | `src/gpu/graphite/KeyHelpers.cpp tile` |
| KGPU-M17-005 | Activate M17 routes: BitmapShader + BitmapRect default ON with rollback | `src/gpu/graphite/Device.cpp drawImage` |

Non-claims: No mipmap, no anisotropic filter, no color-managed decode, no YUV.

### M18 — SaveLayer + Destination Read (5 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M18-001 | Add SaveLayer execution: offscreen target allocation + clear/load/store | `src/gpu/graphite/Device.cpp saveLayer`, `src/gpu/graphite/render/RenderPassTask.cpp:128` |
| KGPU-M18-002 | Add SaveLayer restore: composite child texture into parent with blend | `src/gpu/graphite/render/LayerCompositeRenderStep.cpp` |
| KGPU-M18-003 | Add destination-read copy strategy: split pass + copy target texture | `src/gpu/graphite/DrawContext.cpp dstRead` |
| KGPU-M18-004 | Add destination-read intermediate strategy: bind existing intermediate texture | `src/gpu/graphite/TextureProxy.cpp intermediate` |
| KGPU-M18-005 | Activate M18 routes: SaveLayer + destination read default ON with rollback | `src/gpu/graphite/Device.cpp restoreLayer` |

Non-claims: No framebuffer-fetch. No layer elision. No backdrop filters. No f16.

### M19 — Filter DAG (4 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M19-001 | Add Gaussian blur filter: 2-pass H/V separable blur with downsample/upsample | `src/gpu/graphite/render/BlurRenderStep.cpp`, `src/core/SkMaskBlurFilter.cpp` |
| KGPU-M19-002 | Add ColorMatrix filter: 4x5 matrix + vector multiply in WGSL | `src/shaders/graphite/ColorFilterShader.cpp matrix` |
| KGPU-M19-003 | Add filter DAG execution: multi-node graphs with intermediate texture ownership | `src/gpu/graphite/DrawContext.cpp filter` |
| KGPU-M19-004 | Activate M19 routes: Blur + ColorMatrix + bounded filter DAG default ON | `src/gpu/graphite/Device.cpp drawFilter` |

Non-claims: No Picture, RuntimeShader, or arbitrary SkSL filters. Bounded DAG only
(single-node filters + 2-node chains max; deeper graphs refused with stable
diagnostic).

### M20 — Text A8 + SDF Glyph Atlas (5 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M20-001 | Add text A8 atlas execution: glyph mask upload → atlas texture → WGSL sample | `src/gpu/graphite/text/TextAtlasManager.cpp:42` |
| KGPU-M20-002 | Add SDF glyph atlas: signed distance field generation + WGSL smoothstep | `src/gpu/graphite/text/SDFTextRenderStep.cpp` |
| KGPU-M20-003 | Add DrawTextRun execution: subrun batch → atlas bind → draw fullscreen pass | `src/gpu/graphite/Device.cpp drawGlyphs`, `src/gpu/graphite/DrawList.cpp text` |
| KGPU-M20-004 | Add text shaper integration: SkShaper → GlyphRunDescriptor → GPU route | `src/gpu/graphite/text/TextUtils.cpp` |
| KGPU-M20-005 | Activate M20 routes: A8 + SDF text default ON with rollback | `src/gpu/graphite/Device.cpp drawText` |

Non-claims: Latin only. No color fonts, no emoji, no COLRv1, no SVG glyphs.

### M21 — Runtime Effects Registry (4 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M21-001 | Register SimpleRT: Kotlin CPU oracle + parser-validated WGSL + reflected uniforms | `src/sksl/SkSLRuntimeEffect.cpp`, `src/gpu/graphite/RuntimeEffectDictionary.cpp` |
| KGPU-M21-002 | Register LinearGradientRT + SpiralRT: same pattern, validated WGSL | `src/gpu/graphite/KeyHelpers.cpp runtimeEffect` |
| KGPU-M21-003 | Add RuntimeEffect execution lane: descriptor lookup → WGSL snippet → GPU submit | `src/gpu/graphite/DrawContext.cpp runtimeEffect` |
| KGPU-M21-004 | Activate M21 routes: registered effects default ON, unregistered → refusal | `src/gpu/graphite/Device.cpp drawRuntimeEffect` |

Non-claims: No arbitrary SkSL compilation. Existing hand-written WGSL for SimpleRT/SpiralRT/LinearGradientRT only.

### M22 — Vertices + Mesh Batching (4 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M22-001 | Add DrawVertices execution: triangle list + vertex colors + primitive blend | `src/gpu/graphite/DrawList.cpp vertices`, `src/gpu/graphite/render/VerticesRenderStep.cpp` |
| KGPU-M22-002 | Add vertex buffer materialization: CPU-packed buffers → GPU upload → bind | `src/gpu/graphite/BufferManager.cpp` |
| KGPU-M22-003 | Add mesh batching: sort + merge draw calls by pipeline key | `src/gpu/graphite/DrawList.cpp sort` |
| KGPU-M22-004 | Activate M22 routes: DrawVertices + mesh default ON with rollback | `src/gpu/graphite/Device.cpp drawVertices` |

Non-claims: No index buffer. No custom vertex layouts beyond position+color.

### M23 — Performance Gates + PM Evidence (5 tickets)

| ID | Title | Graphite Ref |
|----|-------|-------------|
| KGPU-M23-001 | Add per-family performance budgets: measured FPS/ms for each draw family | `src/gpu/graphite/Benchmark.cpp` |
| KGPU-M23-002 | Add pipeline cache telemetry: hit rate, eviction, module count per scene | `src/gpu/graphite/PipelineCache.cpp` |
| KGPU-M23-003 | Add frame gate policy: 60fps target, 30fps warning, quarantine on regression | `src/gpu/graphite/Context.cpp` |

Hardware baseline: Apple M-series (M1-M4), Kadre/AppKit/Metal WebGPU present
path. Non-claim: not a release-blocking gate on other platforms or adapters.
| KGPU-M23-004 | Add final PM evidence bundle: all families activated, gates green, rollback tested | `dm/DM.cpp` |
| KGPU-M23-005 | Add gpu-renderer-scenes final catalog: all 45+ scenes render offscreen + windowed | `gm/graphite/*.cpp` |

## 5. Ticket Model

Every ticket follows the existing KGPU model with these sections:

1. Front matter (id, title, status, milestone, priority, owner_area, claim_impact, route_kind, product_activation, release_blocking, adapter_required, depends_on, legacy_gate)
2. PM Note (French)
3. Problem
4. Scope
5. Non-Goals
6. Spec Sources (references to `.upstream/specs/gpu-renderer/` and/or `.upstream/specs/pure-kotlin-text/`)
7. Graphite Algorithm References (with exact file:line for critical algos)
8. Design Sketch (Kotlin-like)
9. Acceptance Criteria (checkboxes)
10. Required Evidence
11. Fallback / Refusal Behavior
12. Dashboard Impact
13. Validation
14. Status Notes

## 6. Activation Pattern

Every "Activate Mxx routes" ticket follows the same pattern as M1 (FillRect activation):

- Add controlled product flag, disabled by default initially
- Implement rollback path (flag off → legacy route)
- Prove parity: flag ON pixels == flag OFF pixels
- Set flag default to ON after parity evidence reviewed
- Keep `productActivation=true`, `releaseBlocking=false`, `readinessDelta=0.0`

## 7. Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk git diff --check
```
