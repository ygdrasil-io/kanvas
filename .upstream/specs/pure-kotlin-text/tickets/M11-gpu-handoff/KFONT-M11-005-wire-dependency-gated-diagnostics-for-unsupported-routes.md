---
id: "KFONT-M11-005"
title: "Wire dependency-gated diagnostics for unsupported routes"
status: "proposed"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "DependencyGated"
depends_on: ["KFONT-M11-001", "KFONT-M11-003"]
legacy_gate: ["dftext", "scaledemoji_rendering", "coloremoji_blendmodes"]
---

# KFONT-M11-005 - Wire dependency-gated diagnostics for unsupported routes

## PM Note

Ce ticket transforme les routes GPU texte manquantes en refus précis au lieu de libellés génériques.

## Problem

Most text artifact routes will remain unsupported until their renderer paths are proven. The GPU handoff needs stable dependency-gated diagnostics for SDF, outline, color glyph, bitmap glyph, SVG glyph, unregistered artifacts, missing upload plans, unsupported transforms, and forbidden CPU-rendered textures. Without this ticket, dashboards can regress to vague "font missing" labels or silently route through unsupported compatibility.

## Scope

- Add route refusal mapping for `SDFGlyphAtlasArtifact`, `OutlineGlyphPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, `SVGGlyphPlan`, unregistered artifacts, missing upload plans, unsupported transforms, stale generations, and CPU-rendered full text textures.
- Emit `gpu-text-route-refusals.json` with artifact type, route family, source text/glyph range, artifact key hash, blocker owner, and stable reason code.
- Map `text.gpu.*` handoff reasons to renderer-facing `unsupported.text.*` diagnostics.
- Keep `DependencyGated` classification only for routes blocked by missing renderer capability; use `fixture-gated` or `GPU-gated` where those are the real blockers.
- Preserve legacy gate traceability for `dftext`, `scaledemoji_rendering`, and `coloremoji_blendmodes`.

## Non-Goals

- Do not implement the unsupported routes in this diagnostic ticket.
- Do not classify missing fixtures as `DependencyGated`.
- Do not create fallback CPU textures.
- Do not alter M9/M10 artifact content.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GPUTextRouteRefusal(
    val commandId: DrawCommandId,
    val textRange: TextRange?,
    val glyphRange: GlyphRange?,
    val artifactType: TextArtifactType,
    val artifactKeyHash: StableHash?,
    val attemptedRoute: GPUTextRoute,
    val blocker: TextRouteBlocker,
    val handoffDiagnostic: String,
    val rendererDiagnostic: String,
)
```

## Acceptance Criteria

- [ ] Unsupported SDF, outline, color, bitmap, and SVG routes each emit a route-specific `unsupported.text.*` diagnostic.
- [ ] Unregistered artifacts emit `text.gpu.artifact-unregistered` and `unsupported.text.artifact_unregistered`.
- [ ] Missing upload plans emit `text.gpu.upload-plan-missing` and `unsupported.text.upload_plan_missing`.
- [ ] CPU-rendered full text textures always emit `text.gpu.CPU-rendered-texture-forbidden` or `unsupported.text.cpu_rendered_texture_forbidden`.
- [ ] Dashboard rows classify blockers by real cause and keep legacy gates linked to route-specific refusals.

## Required Evidence

- `gpu-text-route-refusals.json` fixture covering SDF, outline, color, bitmap, SVG, unregistered artifact, missing upload plan, stale generation, and CPU texture refusal.
- Dashboard classification snapshot showing `DependencyGated` only for missing renderer capability.
- Diagnostic mapping table from `text.gpu.*` to `unsupported.text.*`.

## Fallback / Refusal Behavior

- Unsupported GPU routes refuse; they do not silently use a weaker renderer path.
- Valid text artifacts remain usable evidence even when the renderer route is dependency-gated.
- Legacy gates `dftext`, `scaledemoji_rendering`, and `coloremoji_blendmodes` remain open until route-specific GPU evidence exists.

## Dashboard Impact

- Expected row: `Dependency-gated text GPU routes`.
- Expected classification: `DependencyGated`.
- Claim promotion allowed: no, unless each unsupported route has precise diagnostics and no CPU texture fallback.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextRouteRefusal*'
```

## Status Notes

- `proposed`: Ensures unsupported text GPU routes remain explicit, auditable blockers.
- Move to `ready` only after diagnostic mapping and blocker classification are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:DependencyGated`
- `legacy:dftext`
- `legacy:scaledemoji_rendering`
- `legacy:coloremoji_blendmodes`
