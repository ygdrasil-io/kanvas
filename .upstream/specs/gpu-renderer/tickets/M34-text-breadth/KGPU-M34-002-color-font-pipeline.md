---
id: KGPU-M34-002
title: "Color font pipeline"
status: done
milestone: M34
priority: P0
owner_area: text
claim_impact: TargetNative
route_kind: GPUNative
product_activation: true
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-002 - Color font pipeline

## PM Note

Le sous-ensemble COLRv0 est livré de bout en bout. Le text stack parse COLR/CPAL,
rasterise chaque contour de couche en A8 et produit l'atlas. Le renderer conserve
les identités d'artefact et les bornes de chaque couche, puis compose le glyphe
sur la route WebGPU préparée : un encoder, un command buffer et un submit.

La preuve active utilise le vrai fichier Skia `/fonts/skia/colr.ttf`, glyphe 2,
couches 7 et 8, couleurs CPAL rouge/noire. Le rendu GPU 64×64 est identique pixel
par pixel à une référence CPU source-over indépendante (4096/4096). L'ancien
fixture synthétique « A rouge sur B bleu » et son ABI de 528 octets ont été
retirés. L'ABI active fait 784 octets et porte aussi les bornes device de chaque
couche.

COLRv1, SVG OpenType, emoji et shaping complexe restent hors de ce sous-ensemble
et doivent être refusés explicitement ; ils ne sont pas couverts par le statut
`done` de COLRv0.

## Livraison active (2026-07-17)

- façade publique handle-free : `GPUColorGlyphFrameRecorder` ;
- payload sémantique immuable avec SHA-256 des octets A8 exacts ;
- cache de session pour pipeline/layout/sampler et atlas partagé, avec budget de
  remplacement et compteurs de résidence ;
- géométrie indexée et bornes device par couche, atlas `r8unorm`, composite
  prémultiplié source-over ;
- sortie courante sans readback ou readback final explicite ;
- scène produit `colr-v0-color-glyph` branchée sur `prepareSceneFrameSession()` ;
- référence, rendu, statistiques et diagnostics sous
  `reports/gpu-renderer-scenes/offscreen/colr-v0-color-glyph/`.

## Historique — Claim Split & Re-Scope (2026-06-29)

Audit `fichier:ligne` : les artefacts text-stack supposés manquants existent en
réalité. Le motif de blocage initial « gated on pure-kotlin-text COLRv0
parsing artifacts » est faux et corrigé.

**État historique au 2026-06-29 — alors implémenté mais non promu :**

- Parsing COLRv0 / CPAL : `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt:361` et `:232`.
- Parsing CBDT/CBLC : `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt:1977`.
- Handoff `ColorGlyphPlan` + enregistrement registre :
  `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt:415`,
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/routing/KanvasPreparedGPUArtifactRegistry.kt:40`.
- Refus GPU stable du rendu couleur, sans fallback texture CPU :
  `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusals.kt:257-267`
  (`text.gpu.color-plan-unsupported`), gate `COLRColorGlyph` = `not-promoted`
  (`gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`).
- Validation : `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/ColorFontHandoffRouteTest.kt` (4 tests PASSED).

**Historique au 2026-06-29 — rendu GPU alors non livré :**

- Contrats GPU `GPUColorGlyphLayerPlan`, `GPUColorGlyphCompositePlan`,
  `GPUCBDTCBLCGlyphPlan` : absents (sketch de spec uniquement).
- Rasterisation GPU d'un glyphe COLRv0, COLRv1, SVG OpenType, emoji ; codes
  `unsupported.text.color_font.format_unavailable` / `.layer_count`.
- Ce gate historique a été levé pour le sous-ensemble COLRv0 décrit dans la
  livraison active ci-dessus. Il reste applicable aux formats hors scope.

## Problem

Color fonts (COLRv0/v1, CBDT/CBLC, SVG OpenType, emoji sequences) require
layered glyph compositing, embedded bitmap decoding, and per-glyph color
rasterization. The GPU renderer must define contracts for consuming these
artifacts from the pure Kotlin text stack, but cannot implement COLR/CBDT/SVG
parsing within `:gpu-renderer`.

## Scope

- COLRv0 : couches de glyphes à couleur CPAL solide, raster A8 et composition
  dans l'ordre déclaré.
- `GPUColorGlyphCompositePlan` / payload préparé — composition des couches via
  atlas partagé et bornes device exactes.

Sous-scopes différés, non couverts par le statut `done` :

- `GPUCBDTCBLCGlyphPlan` — embedded bitmap decode + color + GPU upload ;
- `GPUSVGOpenTypeGlyphPlan` — SVG per-glyph CPU raster → GPU upload ;
- `GPUEmojiFallbackPlan` — emoji sequence fallback contract.

## Non-Goals

- No COLR/CBDT/SVG parsing in `:gpu-renderer`. All parsing lives in the pure
  Kotlin text stack.
- No COLRv1 or SVG OpenType rendering until text stack delivers parsing
  artifacts.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md` (CBDT/CBLC
  codec path)
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-BITMAP-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-bitmap-text-step) - source [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59); Choose A8/LCD/color variants, append per-glyph instance data, bind up to four atlas textures, and produce coverage or primitive color from indexed atlas samples.
- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Carry a subspan of an atlas subrun, mask bounds, mask-to-device matrix, glyph range, SDF/LCD metadata, and renderer data as geometry.
- [`GFX-TEXT-ATLAS-GLYPH-UPLOAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-glyph-upload) - source [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237); Resolve mask format, normalize glyph pixels with padding, add glyphs to a DrawAtlas, and record pending atlas uploads.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch historique (formats différés inclus)

```kotlin
enum class GPUColorFontVersion {
    COLRv0,
    COLRv1,
    Unsupported,
}

data class GPUColorGlyphLayerPlan(
    val layers: List<GPUColorGlyphLayer>,
    val composite: GPUColorGlyphCompositePlan,
)

sealed class GPUColorGlyphLayer {
    data class SolidFill(val color: GPURGBA) : GPUColorGlyphLayer()
    data class Gradient(val gradient: GPUGradientFill) : GPUColorGlyphLayer()
    data class GlyphRef(val glyphId: Int, val fontKey: GPUFontKey) : GPUColorGlyphLayer()
}

data class GPUCBDTCBLCGlyphPlan(
    val glyphId: Int,
    val bitmapData: ByteArray,
    val format: GPUBitmapFormat,
    val colorSpace: GPUColorSpace,
)

data class GPUSVGOpenTypeGlyphPlan(
    val glyphId: Int,
    val svgDocument: GPUSVGDocumentRef,
    val rasterSize: GPUSize,
)
```

## Acceptance Criteria

Le statut `done` du sous-ensemble COLRv0 exige et possède :

- un dump stable d'un glyphe COLRv0 à au moins deux couches ;
- une autorité unique sur atlas, couleurs, ordre, bornes device et uniform ABI ;
- un refus avant allocation pour une couche invalide, un budget dépassé ou plus
  de 16 couches ;
- une preuve GPU réelle sans snapshot CPU sur le chemin principal ;
- une référence CPU indépendante, un rendu, un diff/stat et des diagnostics de
  route stables ;
- une politique explicite pour COLRv1, SVG OpenType, CBDT/CBLC et emoji, sans
  promotion implicite.

## Fallback / Refusal Behavior

- Unsupported color font format →
  `unsupported.text.color_font.format_unavailable`.
- Layer count exceeds maximum →
  `unsupported.text.color_font.layer_count`.
- Silent fallback to CPU-rendered color glyph texture is not allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.text.color-font`
- Expected classification: `TargetNative` pour COLRv0 solide borné.
- Claim promotion allowed: oui, uniquement pour le sous-ensemble COLRv0 prouvé.
  Les autres formats restent `DependencyGated` ou explicitement refusés.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ColorFont*'
```

## Status Notes

- `proposed`: Initial ticket. Promotion to `ready` requires text stack COLRv0
  parsing artifacts.
- `proposed → blocked` (2026-06-28): Blocked on pure-kotlin-text COLRv0 parsing artifacts.
- `reste blocked` (2026-06-29): correction du motif. Le motif « pure-kotlin-text
  COLRv0 artifacts » est faux : parsing COLRv0/CPAL/CBDT + handoff `ColorGlyphPlan`
  sont livrés (voir Claim Split). Le ticket **reste `blocked` / `DependencyGated`**
  car l'évidence de rendu GPU couleur est toujours KO (`product_activation: false`,
  contrats GPU absents, refus stable `text.gpu.color-plan-unsupported`). Le
  sous-scope borné handoff + refus est implémenté et testé
  (`ColorFontHandoffRouteTest`) mais ne promeut pas le ticket. Vrai gate :
  exécution GPU M10/M11.
- `blocked → review` (2026-06-29): **promotion**. Le rendu GPU COLRv0 est implémenté
  de bout en bout (Plans 1–3c): handoff `GPUColorGlyphLayerPlan` + pont,
  `GPUColorGlyphRoutePlanner`, shader WGSL composite validé wgsl4k (`loop {}`),
  intégration recording, `drawColorGlyphPass` backend + cache-keys + atlas
  placements. **Preuve de rendu sur GPU réel** (Apple M2 Max): `GPUColorGlyphRenderSmokeTest`
  (`skipped=0, failures=0`) + scène enregistrée `colr-v0-color-glyph` avec
  `reports/gpu-renderer-scenes/offscreen/colr-v0-color-glyph/render.png` committée
  (décodée: 664 px rouges + 1963 bleus). Gate `COLRColorGlyph` => `promoted = true`,
  `product_activation: true`. Régression: 1077 tests, seuls les 4 échecs
  pré-existants de master restent (hors M34). **En attente de revue indépendante
  d'acceptation pour passer à `done`** (règle `STATUS.md`).
- `review → done` (2026-06-29): **accepté.** 3 revues indépendantes APPROVE (PR #1905,
  wgsl4k PR #13, commit `88a1c6c`/`general-flash`) + parité **CPU↔GPU 100%**
  (`reports/.../colr-v0-color-glyph/parity.txt`: `similarity=1.0000`, `matchingPixels=4096/4096`,
  maxΔ=0) — référence CPU, rendu GPU, diff/stat, diagnostics de route et politique
  de refus tous présents. Pas d'activation cachée.
- `done` revalidé (2026-07-17) : l'ancienne preuve A/B, circulaire et non issue
  d'une table COLR, est remplacée par le vrai `colr.ttf`. La scène passe par la
  façade publique et la session préparée, utilise l'ABI 784 octets et obtient
  4096/4096 pixels identiques à l'oracle CPU indépendant.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
