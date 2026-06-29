# M34 Text Breadth — Re-Scope Evidence Bundle

- **Date:** 2026-06-29
- **Tickets:** KGPU-M34-002 (color font), KGPU-M34-003 (variable font), KGPU-M34-004 (complex shaping)
- **Decision:** Re-scope honnête. Split claim — `TargetNative` borné (handoff + facts + refus stable) ; `DependencyGated` maintenu pour le rendu GPU.
- **Source assessment:** `docs/superpowers/specs/2026-06-28-m34-text-blockers-assessment.md`

## Constat d'audit

Les 3 tickets étaient `blocked` avec le motif « gated on pure-kotlin-text
artifacts ». L'audit `fichier:ligne` montre que ce motif est **faux** : les
artefacts text-stack existent et sont testés. Le vrai gap est côté
`:gpu-renderer` (contrats GPU absents, layer planner stub, aucune preuve de
rendu/consommation GPU), et dépend de M6/M10/M11/M4.

## Environnement (preflight)

- `git` worktree `lucid-gopher`, inscriptible.
- bind local UDP/TCP : OK.
- `GRADLE_USER_HOME` inscriptible ; `gradlew` exécutable.
- `:gpu-renderer:test` : compile et tourne (`BUILD SUCCESSFUL`).

## Verdicts par claim (fichier:ligne)

| Claim | Verdict | Évidence |
|---|---|---|
| Parsing COLRv0 / CPAL | EXISTS | `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt:361`, `:232` |
| Parsing CBDT/CBLC | EXISTS | `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt:1977` |
| Parsing fvar/gvar/avar + résolution variable→statique | EXISTS (fixtures testées) | `font/sfnt/.../SFNT.kt:10317`, `font/scaler/.../FontScaler.kt:2408`, `:3904`; tests `FontScalerSurfaceTest.kt:1266`, `:3219` |
| Shaping OpenType (seg→script→bidi→cmap→GSUB→GPOS→clusters) | EXISTS | `font/text/.../shaping/ShapingTypes.kt:684` |
| BiDi UAX #9 (X2–X9, W1–W7, N0–N2) | EXISTS | `font/text/.../shaping/BidiSegmentation.kt:84` |
| Fixtures arabe/devanagari/thai-CJK | EXISTS | `font/text/src/test/.../ArabicShapingFixtureTest.kt`, `DevanagariShapingFixtureTest.kt`, `ThaiCjkBoundaryFixtureTest.kt` |
| Handoff `ColorGlyphPlan` + enregistrement registre | EXISTS | `font/gpu-api/.../GPUTextArtifacts.kt:415`, `gpu-renderer/.../routing/KanvasPreparedGPUArtifactRegistry.kt:40` |
| `GPUGlyphRunDescriptor` (typefaceID/script/bidiLevel/glyphIDs/advances/offsets) | EXISTS | `font/gpu-api/.../GPUTextArtifacts.kt:77-88` |
| Refus GPU stable du rendu couleur, sans fallback texture CPU | EXISTS | `font/gpu-api/.../GPUTextRouteRefusals.kt:257-267` (`text.gpu.color-plan-unsupported`) ; gate `COLRColorGlyph` `not-promoted` `gpu-renderer/.../text/TextContracts.kt` |
| `productActivation` artefacts texte | EXISTS = `false` (verrouillé) | `font/gpu-api/.../GPUTextArtifactRegistry.kt:23`, `:46` |
| Contrats GPU `GPUColorGlyphLayerPlan` / `GPUVariableFontInstancePlan` / `GPUShapingIntegrationContract` / `GPUBiDiRunPlan` | ABSENT | sketch de spec uniquement |
| Consommation GPU BiDi pour paint order (`GPUDrawLayerPlanner`) | STUB | `gpu-renderer/.../layers/LayerContracts.kt:653` = `TODO` |
| Rendu GPU COLRv0 / parité variable / scripts complexes | ABSENT | `product_activation: false` ; refus stable émis |
| Tests `*ColorFont*` / `*VariableFont*` / `*ShapingIntegration*` (avant) | ABSENT | 0 test sélectionné par les commandes de validation |

## Split de claim retenu

| Ticket | TargetNative borné (livré + validé) | DependencyGated (non livré) | Gating |
|---|---|---|---|
| M34-002 | handoff `ColorGlyphPlan` + registre + refus stable `text.gpu.color-plan-unsupported` (no CPU texture) | contrats GPU couleur, rasterisation COLRv0/COLRv1/SVG/emoji | M10/M11 |
| M34-003 | résolution variable→statique (text-stack) + handoff statique `GPUGlyphRunDescriptor.typefaceID` (aucun champ d'axe GPU) | `GPUVariableFontInstancePlan`, refus out-of-range, CFF2 vraies polices, rendu GPU | M4 |
| M34-004 | shaping/BiDi (text-stack) + facts `script`/`bidiLevel` portés au handoff | `GPUBiDiRunPlan`, consommation BiDi paint order, budget CJK, rendu scripts complexes | M6/M10/M11 |

## Tests ajoutés (validation)

- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/ColorFontHandoffRouteTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/VariableFontHandoffRouteTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/ShapingIntegrationHandoffRouteTest.kt`

Commande :

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test \
  --tests '*ColorFont*' --tests '*VariableFont*' --tests '*ShapingIntegration*'
```

Résultat : `git diff --check` exit 0 ; **9/9 tests PASSED** ; `BUILD SUCCESSFUL`.
**Aucun code de production modifié** — les tests verrouillent le comportement
déjà vrai (facts portés + refus stable).

## Non-claims

- Aucun rendu GPU de glyphe couleur, de parité variable, ni de script complexe
  n'est revendiqué.
- `product_activation` reste `false` sur tous les artefacts texte.
- Pas de fallback texture CPU (refus stable `text.gpu.CPU-rendered-texture-forbidden`).
- Codes `unsupported.text.color_font.format_unavailable` / `.layer_count` et
  `unsupported.text.shaping_script_unavailable` restent spec-only (sous-scope
  DependencyGated).

## Fichiers modifiés / ajoutés

- Tickets : `KGPU-M34-002/003/004` (front-matter, PM Note, Claim Split, Dashboard Impact, Status Notes).
- `tickets/M34-text-breadth/README.md` (table + section Re-Scope).
- `tickets/STATUS.md` (ligne M34, Total).
- 3 tests `:gpu-renderer` (ci-dessus).
- Ce bundle.
