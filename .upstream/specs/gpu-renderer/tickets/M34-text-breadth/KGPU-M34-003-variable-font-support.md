---
id: KGPU-M34-003
title: "Variable font support"
<<<<<<< HEAD
status: blocked
=======
status: proposed
>>>>>>> master
milestone: M34
priority: P1
owner_area: text
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-003 - Variable font support

## PM Note

<<<<<<< HEAD
La résolution des polices variables (fvar/gvar/avar → glyphes statiques) est
faite par le text stack et testée (fixtures). Le GPU reçoit des glyphes
statiques via `GPUGlyphRunDescriptor` (identité `typefaceID`) et ne fait
aucune logique de variation. Le rendu GPU avec parité CPU, le refus
out-of-range et les vraies polices CFF2 restent DependencyGated (M4).

## Claim Split & Re-Scope (2026-06-29)

Audit `fichier:ligne` : les artefacts text-stack supposés manquants existent en
réalité. Le motif « gated on pure-kotlin-text variable font resolution
artifacts » est faux et corrigé.

**Implémenté mais non promu — reste `DependencyGated` (handoff + facts portés + refus stable) :**

- Parsing fvar/gvar/avar :
  `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt:10317`,
  `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt:2408`.
- Résolution variable→statique (clamp, normalisation, avar, gvar/CFF2 blend) :
  `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt:3904`.
- Parité glyphe résolu vs référence CPU (fixtures générées) :
  `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt:1266` et `:3219`.
- Handoff statique : `GPUGlyphRunDescriptor.typefaceID`
  (`font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt:80`) ;
  le descripteur ne porte aucun champ d'axe/variation, donc le GPU traite des
  glyphes statiques.
- Validation : `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/VariableFontHandoffRouteTest.kt` (2 tests PASSED).

**Dependency-Gated (non livré) :**

- Contrats GPU `GPUVariableFontInstancePlan`, `GPUVariableFontAxis` : absents.
- `RefuseDiagnostic` out-of-range avec tag + valeur : absent (clamp silencieux
  actuel, `FontScaler.kt:7292`).
- Vraies polices CFF2 (fixtures uniquement, auto-documenté
  `FontScaler.kt:4745`) : gated M4.
- Rendu GPU. `product_activation` reste `false`.
=======
Les variable fonts sont résolues par le text stack ; le GPU renderer voit des
glyphs statiques.
>>>>>>> master

## Problem

Variable fonts expose axis-tag/value pairs (weight, width, slant, optical
size, etc.) that the text stack must resolve into concrete glyph outlines.
The GPU renderer must define a contract for accepting per-run axis values and
diagnostic for out-of-range values, but must not perform outline generation,
HarfBuzz variation, or FreeType instance construction.

## Scope

- `GPUVariableFontInstancePlan` — per-run axis values (tag, value, precision).
- Route: axis values consumed by text stack, GPU receives resolved
  `GlyphArtifactPlan` (static glyphs only).
- Diagnostic for out-of-range axis values.

## Non-Goals

- No outline generation in `:gpu-renderer`.
- No HarfBuzz, FreeType, or CoreText variation support in `:gpu-renderer`.
- No variable font axis interpolation or instance construction.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Carry a subspan of an atlas subrun, mask bounds, mask-to-device matrix, glyph range, SDF/LCD metadata, and renderer data as geometry.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Resolve mask format, normalize glyph pixels with padding, add glyphs to a DrawAtlas, and record pending atlas uploads.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUVariableFontAxis(
    val tag: String,
    val value: Float,
    val precision: Int,
)

data class GPUVariableFontInstancePlan(
    val fontKey: GPUFontKey,
    val axes: List<GPUVariableFontAxis>,
)
```

## Acceptance Criteria

<<<<<<< HEAD
> Scope (2026-06-29) : le sous-scope borné « Claim Split » (handoff statique)
> est implémenté et testé, mais ne promeut pas le ticket. Le rendu GPU, le refus
> out-of-range et CFF2 vraies polices ci-dessous restent `DependencyGated` (M4)
> — le ticket reste `blocked`.

=======
>>>>>>> master
- [ ] Text stack accepts axis-tag/value pairs and produces resolved glyphs.
- [ ] GPU treats resolved glyphs as static (no variation logic).
- [ ] Out-of-range axis value → `RefuseDiagnostic` with axis tag and value.

## Required Evidence

<<<<<<< HEAD
> Scope borné couvert par `VariableFontHandoffRouteTest`. Les preuves ci-dessous
> (dump plan GPU, parité rendu, refus out-of-range) restent `DependencyGated` (M4).

=======
>>>>>>> master
- `GPUVariableFontInstancePlan` dump with valid axis-tag/value pairs.
- Refusal fixture: out-of-range weight axis value.
- Static glyph rendering evidence: resolved glyph from variable font
  instance matches CPU reference.

## Fallback / Refusal Behavior

- Out-of-range axis value → clamp or `RefuseDiagnostic`.
- No CPU texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.text.variable-font`
<<<<<<< HEAD
- Expected classification: `DependencyGated` (rendu GPU / CFF2 vraies polices /
  refus out-of-range). Résolution text-stack + handoff statique implémentés/testés
  mais ne promeuvent pas le ticket.
- Claim promotion allowed: no — aucun claim de rendu GPU ni de support CFF2
  vraies polices.
=======
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.
>>>>>>> master

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*VariableFont*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack
  variable font resolution artifacts.
<<<<<<< HEAD
- `proposed → blocked` (2026-06-28): Blocked on pure-kotlin-text variable font resolution artifacts.
- `reste blocked` (2026-06-29): correction du motif. Le motif « pure-kotlin-text
  variable font resolution artifacts » est faux : fvar/gvar/avar + résolution
  variable→statique sont livrés et testés (fixtures). Le ticket **reste `blocked`
  / `DependencyGated`** car l'évidence de rendu GPU est toujours KO
  (`product_activation: false`, `GPUVariableFontInstancePlan` absent, CFF2
  fixtures-only, pas de refus out-of-range). Le sous-scope borné handoff statique
  (`GPUGlyphRunDescriptor.typefaceID`, aucun champ d'axe côté GPU) est implémenté
  et testé (`VariableFontHandoffRouteTest`) mais ne promeut pas le ticket. Vrai
  gate : CFF2 vraies polices M4 + exécution GPU.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
