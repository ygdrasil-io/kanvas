---
name: kanvas-gm-diagnostic
description: Use when debugging Skia GM rendering failures in the Kanvas integration-tests/skia suite — when a GM fails with low similarity, when you need to understand why pixels differ, or when an agent needs structured diagnostic data to fix rendering bugs
compatibility: opencode
metadata:
  audience: developer
  workflow: debug
---

# Kanvas GM Diagnostic

3-layer diagnostic framework that produces agent-consumable structured output for Skia GM test failures. Activated via `DebugLevel` enum: OFF/PIXEL/OP/TRACE.

## Activation

```bash
# Via JUnit runner (single GM)
./gradlew :integration-tests:skia:test -Dkanvas.render.debugLevel=TRACE

# Via system property in code
config = RenderConfig.DEFAULT.copy(debugLevel = DebugLevel.TRACE)
```

**Levels:**
- `OFF` — zero overhead (default, CI)
- `PIXEL` — Layer 1 only: enriched diff
- `OP` — Layer 1 + 2: per-op blame
- `TRACE` — Layer 1 + 2 + 3: pipeline trace (Layer 3 requires GPU wiring, currently deferred)

## Output

Per GM, under `<tempDir>/<gmName>/`:
- `manifest.json` — full structured diagnostic
- `kanvas.png`, `reference.png`, `diff.png` — standard outputs
- `diagnostics/` — enriched diagnostic outputs:
  - `heatmap.png`, `heatmap_R.png`, `heatmap_G.png`, `heatmap_B.png`, `heatmap_A.png` — per-channel graduated heatmaps (green→yellow→red)
  - `op_N_before.png`, `op_N_after.png`, `op_N_diff.png` — per-suspect-op before/after/delta

## Interpreting manifest.json

### Entry point: `agentSummary`

Read this FIRST. Human-readable diagnosis:

```json
{
  "primaryIssue": "B channel shows dominant divergence (maxDelta=110, meanDelta=57.97)",
  "alphaChannel": "Alpha channel matches perfectly",
  "suspectOps": [{ "index": 50, "hypothesis": "...", "action": "..." }]
}
```

- `alphaChannel`: if alpha matches → color/rendering issue, not geometry. If alpha also diverges → geometry or coverage problem.
- `suspectOps`: concrete actions for the agent

### Drill-down: `result`

Pixel comparison summary: status (PASS/FAIL), similarity%, threshold, per-channel max/mean delta and mismatch%.

Use `result.perChannel` to identify the dominant divergent channel (R/G/B/A).

### Layer 1: `spatialReport`

- `ssim`: global structural similarity (0-1). < 0.9 indicates structural differences beyond noise.
- `ssimBlocks`: 16×16 blocks. Filter for `score < 0.3` to find severely divergent regions.
- `zones`: spatial classification (edge/solid/gradient/text) with per-zone delta aggregation.
  - `severity`: low (<5% avgDelta), medium (5-20%), high (>20%)
  - `dominantChannel`: which channel diverges most in this zone

### Layer 2: `opTrace`

- `suspectOps`: indices of operations causing >5% pixel divergence
- Each entry has `pixelContribution` (percentage) and URLs to before/after/delta PNGs
- Operations < 50 use sequential replay; ≥ 50 use binary search (checkpoints at 50%, 100%)

### Layer 3: `pipelineTrace`

- `summary`: dispatched/refused counts
- Per-op GPU route (FillPath, StencilCover, TextureBlit, etc.), shaders used, blend mode
- For refused ops: diagnostic code and reason

Currently produces empty trace (Layer 3 GPU wiring not yet connected to the GPU pipeline recording loop).

## Debugging workflow for an agent

### Step 1: Read `agentSummary`

Determine the dominant error category:
- **Alpha matches** → focus on color, shader modulation, blend modes
- **Alpha diverges** → focus on geometry, tessellation, coverage/stencil
- **Single channel dominant** → check that channel's processing in shaders
- **All channels diverge** → likely systemic pipeline issue

### Step 2: Check `spatialReport.ssimBlocks`

Find spatial regions with very low SSIM (<0.3). Cross-reference with the GM's drawing logic to identify which draw calls affect those regions.

### Step 3: Analyze `opTrace.suspectOps`

For each suspect operation:
1. Note the op type (DrawRect, FillPath, DrawImage, etc.)
2. Check `pipelineTrace` for its GPU route and refusal status
3. Look at `before.png`/`after.png`/`diff.png` to visualize the exact change

### Step 4: Cross-reference with Layer 3 (if available)

For dispatched suspect ops, check:
- Route (StencilCover vs FillPath vs TextureBlit)
- Shaders used
- Blend mode

For refused ops: read the diagnostic code and reason, then check the GPU pipeline's acceptance matrix in `GPURefusalGuards.kt`.

### Step 5: Form hypothesis and fix

Common failure patterns the diagnostic reveals:

| Symptom | Likely root cause | Where to look |
|---------|-------------------|---------------|
| B channel dominant, alpha perfect | Color modulation missing | `GPUMaterialMapper.toMaterial()` |
| Alpha diverges, edge zone high | Anti-aliasing or stencil coverage | `RECT_AA_WGSL`, stencil passes |
| All channels, text zone only | Text rendering/shaping | `GPURenderer.resolveTextColor()` |
| Refused ops > 0 | Material not supported for op type | `GPURefusalGuards.fillGuardRefusalReasonOrNull()` |

## Example session

GM `pictureshader_alpha` at 30.84% similarity:

```
agentSummary → "B channel dominant, alpha perfect → color/rendering issue"
spatialReport → 644 blocks < 0.3, all 3 zones HIGH severity
opTrace → ops 0-51 cause 69% divergence → systemic, not single op
pipelineTrace → 34 refusals

→ Hypothesis: Paint color modulation broken for shader ops
→ Found: GPUMaterialMapper.toMaterial() drops paint.color when shader != null
→ Fix: modulate shader output by paint color
```

## Known limitations

- **Layer 3 (PipelineTrace)** currently produces empty data because the GPU recording loop does not call `RenderOpListener`. Pipeline wiring is a deferred follow-up task.
- **OpInspector** uses binary search (checkpoints at 50%, 100%) for pictures with >50 ops. Full sequential replay is only for ≤50 ops.
- **SSIM** computed on luminance only (Y channel), not per-RGB.
- **Spatial zones** use simple Sobel 3×3 threshold — may misclassify in complex scenes.
