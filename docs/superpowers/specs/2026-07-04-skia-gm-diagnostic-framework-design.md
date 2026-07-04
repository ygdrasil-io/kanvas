# Skia GM Diagnostic Framework

**Date:** 2026-07-04
**Status:** Design — awaiting implementation plan
**Scope:** `integration-tests/diagnostic` (new module) + enhancements to `integration-tests/skia`, `integration-tests/test-utils`, and `kanvas-core`

## Problem

The `integration-tests/skia` test infrastructure uses pixel-level comparison with red-highlighted diff images. When a GM fails (currently ~50% of 589 GMs), the developer (or AI agent) can only see that pixels differ — not **which draw calls** caused the divergence, **which pipeline stage** is responsible, or **what aspect of rendering** (color channel, geometry, anti-aliasing) differs.

The dashboard and similarity tracker detect regressions but provide no actionable diagnostics for root-cause analysis.

## Goal

A 3-layer diagnostic framework that produces **structured, agent-consumable** diagnostics in a single `DiagnosticManifest` JSON file plus associated image artifacts. An AI agent can read the manifest, identify the root cause, and propose or apply a fix.

## Non-Goals

- Does not compare Kanvas against Skia native at the pipeline stage level (no Skia JNI)
- Does not modify the production render pipeline behavior
- Does not replace the existing JUnit runner or dashboard — it augments them

## Architecture

### Module Layout

```
integration-tests/
├── test-utils/              (existing — enriched)
│   └── ComparisonUtils.kt   + SSIM computation, enriched delta arrays
├── skia/                    (existing — enriched)
│   ├── SkiaGmRunner.kt      + diagnosticLevel param, DiagnosticManifest output
│   ├── SkiaGmRenderer.kt    + RenderConfig.diagnosticLevel
│   └── SkiaDashboardGenerator.kt  + link to manifest.json
└── diagnostic/              (NEW module)
    ├── build.gradle.kts
    └── src/main/kotlin/org/graphiks/kanvas/diagnostic/
        ├── DiagnosticLevel.kt       // OFF / PIXEL / OP / TRACE
        ├── DiagnosticManifest.kt    // Root JSON schema
        ├── DiffAnalyzer.kt          // Layer 1
        ├── OpInspector.kt           // Layer 2
        ├── PipelineTracer.kt        // Layer 3
        ├── SpatialZoneClassifier.kt // Spatial segmentation
        └── DiagnosticRunner.kt      // Orchestrates the 3 layers
```

### Dependencies

- `diagnostic` depends on `test-utils` (for ComparisonUtils) and `kanvas` (for Picture, DisplayOp, Surface)
- `diagnostic` does NOT depend on `gpu-renderer`
- `diagnostic` uses a listener interface (`RenderOpListener`) injected into `Surface` to receive pipeline events
- `skia` depends on `diagnostic` (compileOnly for optional diagnostic features)

### Diagnostic Levels

```
DiagnosticLevel.OFF    — No capture, no allocation, zero overhead (production/CI)
DiagnosticLevel.PIXEL  — Layer 1 only: enriched diff + heatmap
DiagnosticLevel.OP     — Layer 1 + 2: op-level isolation
DiagnosticLevel.TRACE  — Layer 1 + 2 + 3: full pipeline trace
```

Each level includes all lower levels. `OFF` means no diagnostic code runs at all.

Usage:
- CI / batch runs: `OFF`
- Dev quick check: `PIXEL`
- Debug a failing GM: `TRACE`

## Layer 1: DiffAnalyzer — Enriched Pixel Diff

Transforms the binary red diff into structured, multi-dimensional diagnostic data.

### Components

**HeatmapGenerator**
- Per-pixel delta mapped to a graduated color palette (green → yellow → red)
- Encodes delta magnitude instead of binary match/mismatch
- Option: separate heatmaps per channel (R, G, B, A)

**SsimCalculator**
- SSIM (Structural Similarity Index) computed over 16×16 blocks
- Reports blocks with SSIM < 0.95 as suspect
- Global SSIM = block-weighted average

**SpatialZoneClassifier**
- Classifies each pixel in the reference image into zones:
  - `edges` — high local gradient (anti-aliased boundaries)
  - `solid` — uniform color regions
  - `gradient` — progressive color variation
  - `text` — high edge density
- Uses a simple 3×3 Sobel filter on luminance

**ZoneDeltaAggregator**
- For each spatial zone, aggregates per-channel delta statistics
- Reports: mean delta, mismatch percentage, dominant channel, severity (low/medium/high)

### Interface

```kotlin
object DiffAnalyzer {
    fun analyze(
        actualRgba: ByteArray,
        referenceRgba: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int,
        outputDir: File? = null,
    ): SpatialReport
}
```

### Output

Files saved to `outputDir` when provided:
- `heatmap.png` — graduated heatmap
- `heatmap_R.png`, `heatmap_G.png`, `heatmap_B.png`, `heatmap_A.png` — per-channel heatmaps

JSON in `DiagnosticManifest.spatialReport`:
```json
{
  "ssim": 0.91,
  "ssimBlocks": [{ "x": 320, "y": 200, "score": 0.62 }],
  "zones": [
    {
      "label": "fill_area",
      "bounds": { "x": 100, "y": 50, "w": 300, "h": 200 },
      "dominantChannel": "B",
      "severity": "high",
      "avgDelta": 35.2
    }
  ],
  "heatmapUrl": "diagnostics/brightness_heatmap.png"
}
```

## Layer 2: OpInspector — Per-Operation Isolation

Identifies which drawing operations cause divergence using incremental replay.

### Algorithm: Incremental Divergence Blame

1. Extract the `Picture` from the GM (via `GmCanvas.recordedPicture`)
2. For each operation index N from 0 to totalOps-1:
   - Create a fresh `Surface`
   - Replay operations [0..N] onto it
   - Render and compare against the Skia reference PNG
   - Compute similarity after op N
3. Compute `pixelContribution[N] = max(0, similarity[N-1] - similarity[N])`
4. Mark ops with contribution > 5% as suspect
5. For suspect ops only: save `before.png` (after op N-1), `after.png` (after op N), and `diff.png`

### Optimization

- **Binary search mode**: Instead of O(N) renders, search for divergence boundaries in O(log N) renders
- **Skip stable ops**: Operations that don't change visible pixels (DrawColor backgrounds, Clear, SetTransform) are batched

### Required Change to GmCanvas

Expose the recorded `Picture`:

```kotlin
// In GmCanvas:
internal val recordedPicture: Picture
    get() = canvas.flushAndSnapshot()
```

### Interface

```kotlin
object OpInspector {
    fun inspect(
        picture: Picture,
        referenceRgba: ByteArray,
        gmWidth: Int,
        gmHeight: Int,
        tolerance: Int,
        outputDir: File,
    ): OpTrace
}
```

### Output

Files saved per suspect op:
- `op_{N}_before.png`
- `op_{N}_after.png`
- `op_{N}_diff.png`

JSON in `DiagnosticManifest.opTrace`:
```json
{
  "totalOps": 42,
  "ops": [
    {
      "index": 12,
      "type": "FillPath",
      "pixelContribution": 37.4,
      "isSuspect": true,
      "beforeUrl": "diagnostics/op_11.png",
      "afterUrl": "diagnostics/op_12.png",
      "deltaUrl": "diagnostics/op_12_diff.png"
    }
  ],
  "suspectOps": [12, 18, 31]
}
```

## Layer 3: PipelineTracer — Per-Operation GPU Pipeline Trace

Captures GPU pipeline behavior for each drawing operation without depending on `gpu-renderer`.

### Decoupling via RenderOpListener

The listener interface lives in `kanvas-core` so `Surface` can reference it without depending on `diagnostic`. The `diagnostic` module implements it:

```kotlin
// In kanvas-core (shared, no dependency on diagnostic or gpu-renderer):
interface RenderOpListener {
    fun onOpDispatched(
        index: Int,
        opType: String,
        route: String,
        shaders: List<String>,
        vertexCount: Int,
        blendMode: String,
    )

    fun onOpRefused(
        index: Int,
        opType: String,
        code: String,
        reason: String,
    )
}

// In Surface (kanvas-core):
fun setRenderOpListener(listener: RenderOpListener?)
```

The listener is registered on `Surface` when `DiagnosticLevel >= TRACE`, and the GPU pipeline calls it as each DisplayOp is processed. When `DiagnosticLevel < TRACE`, no listener is registered → zero overhead.

### Captured Data Per Operation

**Dispatched operations:**
- Route: `FillPath`, `FillRect`, `StencilCover`, `TextureBlit`, `FullscreenPass`, `ClearPass`
- Shaders: list of WGSL shader names used
- Vertex count
- Blend mode

**Refused operations:**
- Diagnostic code (e.g., `IMG_008`)
- Refusal reason message
- Impact correlation with OpInspector (does this refusal cause visible divergence?)

### Interface

```kotlin
class PipelineTracer : RenderOpListener {
    fun buildTrace(): PipelineTrace
}
```

### Output

JSON in `DiagnosticManifest.pipelineTrace`:
```json
{
  "summary": { "dispatched": 39, "refused": 3 },
  "ops": [
    {
      "opIndex": 12,
      "route": "StencilCover",
      "status": "dispatched",
      "shaders": ["fill_stencil", "fill_cover"],
      "vertexCount": 2456,
      "blendMode": "SrcOver"
    },
    {
      "opIndex": 18,
      "route": "Refused",
      "status": "refused",
      "reason": { "code": "IMG_008", "message": "unsupported sampling options" }
    }
  ]
}
```

## DiagnosticManifest — Agent-Consumable Output

A single JSON file that agents parse as the entry point for diagnosis.

### Structure

```json
{
  "gm": "brightness",
  "diagnosticLevel": "TRACE",
  "generatedAt": "2026-07-04T15:30:00",

  "result": {
    "status": "FAIL",
    "similarity": 78.3,
    "threshold": 95.0,
    "totalPixels": 480000,
    "mismatchingPixels": 104160,
    "perChannel": {
      "R": { "maxDelta": 47, "meanDelta": 12.3, "mismatchPct": 18.2 },
      "G": { "maxDelta": 52, "meanDelta": 14.1, "mismatchPct": 19.1 },
      "B": { "maxDelta": 89, "meanDelta": 31.7, "mismatchPct": 21.7 },
      "A": { "maxDelta": 0,  "meanDelta": 0.0,  "mismatchPct": 0.0 }
    }
  },

  "spatialReport": { /* Layer 1 output */ },
  "opTrace": { /* Layer 2 output */ },
  "pipelineTrace": { /* Layer 3 output */ },

  "agentSummary": {
    "primaryIssue": "Blue channel shows dominant divergence (maxDelta=89, meanDelta=31.7)",
    "alphaChannel": "Alpha channel matches perfectly -> issue is color, not geometry or shape",
    "suspectOps": [
      {
        "index": 12,
        "hypothesis": "FillPath via StencilCover: blending blue channel incorrectly in cover pass",
        "action": "Review blend state between stencil pass and cover pass in GPUPipeline.kt"
      }
    ]
  }
}
```

### Agent Consumption Flow

1. Agent reads `agentSummary` (natural language, actionable)
2. If more detail needed, drills into `spatialReport` / `opTrace` / `pipelineTrace`
3. Image URLs point to files on disk (e.g., `diagnostics/brightness_heatmap.png`)
4. Agent can load and inspect images using the file paths

### Output on Disk

Per GM, a directory `<tempDir>/<gmName>/` contains:
- `manifest.json` — the full DiagnosticManifest
- `heatmap.png`, `heatmap_R.png`, etc. — per-channel heatmaps
- `op_{N}_before.png`, `op_{N}_after.png`, `op_{N}_diff.png` — for suspect ops only

## Integration Points

### SkiaGmRunner Changes

```kotlin
@ParameterizedTest
@MethodSource("allGms")
fun `render GM`(gm: SkiaGm) {
    val diagnosticLevel = System.getProperty("kanvas.diagnostic")?.let {
        DiagnosticLevel.valueOf(it)
    } ?: DiagnosticLevel.OFF

    val config = RenderConfig(diagnosticLevel = diagnosticLevel)
    val result = SkiaGmRenderer.render(gm, config = config)
    // ... existing comparison ...

    if (diagnosticLevel >= DiagnosticLevel.PIXEL) {
        val manifest = DiagnosticRunner.run(
            gm = gm,
            actualRgba = result.rgba,
            referenceRgba = reference,
            picture = gmCanvas.recordedPicture,
            diagnosticLevel = diagnosticLevel,
            outputDir = File(outputDir, gm.name),
        )
        manifest.writeTo(outputDir)
    }
}
```

### Gradle / CLI

```
# Run with diagnostics enabled (uses System property)
./gradlew :integration-tests:skia:test -Dkanvas.diagnostic=TRACE

# Run specific GM with full trace
./gradlew :integration-tests:skia:test --tests "*brightness" -Dkanvas.diagnostic=TRACE
```

### Dashboard Integration

The `SkiaDashboardGenerator` links to `manifest.json` for each failing GM in the dashboard HTML.

## File Inventory

### New Files

| File | Purpose |
|------|---------|
| `integration-tests/diagnostic/build.gradle.kts` | Module build config |
| `diagnostic/.../DiagnosticLevel.kt` | Enum: OFF, PIXEL, OP, TRACE |
| `diagnostic/.../DiagnosticManifest.kt` | JSON schema + serialization |
| `diagnostic/.../DiffAnalyzer.kt` | Layer 1: heatmap, SSIM, zone classification |
| `diagnostic/.../OpInspector.kt` | Layer 2: incremental replay + blame |
| `diagnostic/.../PipelineTracer.kt` | Layer 3: RenderOpListener impl |
| `diagnostic/.../SpatialZoneClassifier.kt` | Sobel-based zone segmentation |
| `diagnostic/.../DiagnosticRunner.kt` | Orchestrator |

### Modified Files

| File | Change |
|------|--------|
| `integration-tests/test-utils/.../ComparisonUtils.kt` | Add `computeSSIM()`, per-channel delta array exposure |
| `integration-tests/skia/.../SkiaGmRunner.kt` | Add `DiagnosticLevel` support, manifest output |
| `integration-tests/skia/.../SkiaGmRenderer.kt` | Pass `DiagnosticLevel` through `RenderConfig` |
| `integration-tests/skia/.../GmCanvas.kt` | Expose `recordedPicture` property |
| `integration-tests/skia/.../SkiaDashboardGenerator.kt` | Link to manifest.json for failures |
| `kanvas/src/.../surface/RenderConfig.kt` | Add `diagnosticLevel` field |
| `kanvas/src/.../surface/Surface.kt` | Add `setRenderOpListener()` |
| `kanvas/src/.../surface/RenderOpListener.kt` | New interface (in kanvas-core, not diagnostic) |
| `gpu-renderer/.../recording/` | Call `RenderOpListener` during recording |

## SSIM Implementation Notes

SSIM per 16×16 block uses the standard formula:

```
SSIM(x, y) = (2μxμy + C1)(2σxy + C2) / ((μx² + μy² + C1)(σx² + σy² + C2))
```

Where:
- `μ` = local mean over the block
- `σ` = local variance
- `σxy` = covariance between reference and actual blocks
- `C1 = (0.01 * 255)²`, `C2 = (0.03 * 255)²`

Only luminance (Y channel) is used: `Y = 0.299R + 0.587G + 0.114B`.

## Testing Strategy

- **Unit tests**: `DiffAnalyzer` on known synthetic buffers (identical, inverted, shifted)
- **Unit tests**: `SpatialZoneClassifier` on synthetic patterns (checkerboard, gradient, solid)
- **Unit tests**: `DiagnosticManifest` JSON serialization round-trip
- **Integration tests**: Run with `DiagnosticLevel.PIXEL` on a known-passing GM, verify manifest structure
- **Integration tests**: Run with `DiagnosticLevel.TRACE` on a known-failing GM, verify all three layers produce output
- **Smoke test**: Verify `DiagnosticLevel.OFF` produces no files and no measurable overhead

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| OpInspector O(N) renders too slow for large GMs | Binary search mode + skip stable ops |
| PipelineTracer listener called from GPU thread | Listener collects data via thread-safe structures |
| Picture replay doesn't produce identical pixels to original render | Accept approximation; op blame uses directional similarity trend, not exact match |
| SpatialZoneClassifier misclassifies complex scenes | Zones are advisory, not used for pass/fail decisions |

## Design Decisions

- **agentSummary generation**: Uses deterministic templated heuristics based on diagnostic data (e.g., "blue channel dominant + StencilCover route → check blend state"). No LLM dependency.
- **Per-channel heatmaps**: Generated at PIXEL level and above. Four additional PNGs (heatmap_R/G/B/A.png) provide channel-isolated views.
