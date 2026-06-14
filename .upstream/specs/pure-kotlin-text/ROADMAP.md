# Roadmap: système font pure Kotlin complet

## But

Construire un système font/text/glyph complet en pure Kotlin pour Kanvas:
chargement des fontes, parsing SFNT/OpenType, scaling des glyphes, shaping
(mise en forme des glyphes), paragraph layout (mise en page de paragraphes),
sélection de représentations glyph, artifacts CPU/GPU, diagnostics, validation
et migration de la façade Skia-like vers ce cœur.

Cette roadmap vise la cible décrite par `.upstream/specs/pure-kotlin-text/`.
Elle doit rester autoportante: les anciennes specs font peuvent disparaître
après migration des gates durables, des diagnostics legacy et des baselines de
preuve dans ce pack.

## Contraintes non négociables

- Rester pure Kotlin pour le comportement normatif.
- Ne pas dépendre de HarfBuzz, FreeType, Fontations, AWT, JNI, ICU natif,
  platform shapers ou renderers SVG natifs.
- Ne pas porter Ganesh ou Graphite.
- Garder WebGPU comme backend GPU.
- Garder `SkRuntimeEffect` et les API Skia-like comme façades de compatibilité,
  pas comme moteur font/text normatif.
- Ne pas déclarer un support complet sans fixture, dump déterministe,
  oracle CPU, diagnostics stables et preuve GPU si une route GPU est revendiquée.

## Modèle de claims

Chaque ligne de support doit appartenir à une seule catégorie.

| Catégorie | Sens | Preuve minimale |
|---|---|---|
| `target-supported` | La cible pure Kotlin est implémentée et validée. | Fixture, dump, oracle CPU, diagnostics, commande de validation. |
| `current-supported` | Le comportement actuel est supporté dans un périmètre borné. | Test existant, limites documentées, non-claims explicites. |
| `tracked-gap` | La cible est voulue mais pas encore implémentée. | Ticket, spec source, raison de non-support. |
| `DependencyGated` | La route dépend d'un composant pure Kotlin non livré. | Diagnostic stable et gate dashboard. |
| `fixture-gated` | Le code existe partiellement mais manque de fixtures, goldens ou provenance normative. | Fixture manifest, source bytes ou refus de promotion. |
| `GPU-gated` | Le support CPU existe mais la route GPU n'a pas de preuve ou d'artifact promu. | CPU oracle, diagnostic GPU, absence de claim GPU. |
| `expected-unsupported` | Hors cible volontairement. | Spec ou décision d'architecture. |
| `drift-only` | Comparaison informative avec moteur externe. | Rapport d'écart, jamais utilisé comme oracle normatif. |

La classification doit refléter le blocker réel. Une feature non prouvée ne
devient `DependencyGated` que si le composant requis est absent; elle doit être
`fixture-gated` si la preuve normative manque, ou `GPU-gated` si seule la route
GPU manque.

## Definition of Done générale

Une feature font/text pure Kotlin est terminée seulement si tous ces éléments
sont présents:

- API ou contrat stable dans le module propriétaire.
- Fixture déterministe, préférablement embarquée dans le repo.
- Dump sémantique lisible et stable.
- Test unitaire du module propriétaire.
- Test d'intégration si la feature traverse plusieurs modules.
- Diagnostic stable pour chaque refus ou fallback.
- Dashboard ou report mis à jour si la feature change un claim public.
- Preuve GPU seulement lorsque la route GPU est revendiquée.

## Séquence globale

| Rang | Milestone | Dépend de | Débloque |
|---|---|---|---|
| M0 | Claims, CI et diagnostics | Rien | Tous les autres milestones |
| M1 | Identité font et sources | M0 | Scalers, fallback, fixtures |
| M2 | SFNT/OpenType parser durci | M1 | Scalers, shaping, color fonts |
| M3 | TrueType `glyf` complet | M2 | Outline glyphs, A8/SDF, text path |
| M4 | CFF/CFF2 scalers | M2 | OTF/CFF support réel |
| M5 | Unicode segmentation et bidi | M0 | Shaping multi-script, paragraph |
| M6 | OpenType Layout shaping | M2, M5 | Complex text, fallback runs |
| M7 | Fallback et system fonts | M1, M2, M6 partiel | Text runs multi-font |
| M8 | Paragraph engine | M5, M6 | Rich text, layout, hit testing |
| M9 | Glyph artifacts A8/SDF/outline | M3, M6 | GPU handoff, atlas |
| M10 | Color fonts, bitmap, SVG, emoji | M2, M6, M9 | Emoji/color GM promotion |
| M11 | GPU handoff typé | M8, M9, M10 partiel | `DrawTextRun` général |
| M12 | Performance et telemetry | M3-M11 | Release gates |
| M13 | Migration façade Skia-like | M1-M12 par tranche | Retrait des chemins legacy |

## Couverture specs vers milestones

| Spec cible | Milestones principaux |
|---|---|
| `00-architecture-and-module-boundaries.md` | M0, M1, M9, M11, M13 |
| `01-font-source-sfnt-and-scalers.md` | M1, M2, M3, M4, M7 |
| `02-opentype-layout-shaping-engine.md` | M5, M6, M7 |
| `03-paragraph-engine.md` | M8 |
| `04-glyph-representation-and-artifacts.md` | M9, M11 |
| `05-color-fonts-bitmap-svg-emoji.md` | M10, M11 |
| `06-gpu-renderer-handoff.md` | M9, M10, M11 |
| `07-validation-conformance-and-drift.md` | M0, every support promotion |
| `08-performance-budgets-and-telemetry.md` | M12 |
| `09-migration-from-current-font-pack.md` | M13 |

## Règle de slicing Linear

Chaque ticket issu de cette roadmap doit inclure:

- spec source et section cible;
- non-goals explicites;
- fixture ou raison `fixture-gated`;
- dump attendu;
- diagnostic attendu;
- gate legacy éventuelle et diagnostic target correspondant;
- commande de validation;
- impact dashboard/report;
- route CPU/GPU revendiquée ou refusée.

## M0 - Claims, CI et diagnostics

Objectif: empêcher les faux positifs et rendre chaque future promotion
auditable.

Livrables:

- Ajouter les modules pure Kotlin font actuels ou candidats dans la CI:
  `:font:core`, `:font:sfnt`, `:font:scaler`, `:font:text`, `:font:glyph`,
  `:font:gpu-api`. Les packages cibles restent normatifs; les noms exacts de
  modules peuvent être figés dans le planning d'implémentation.
- Ajouter `.upstream/specs/pure-kotlin-text/**` aux paths qui déclenchent la CI.
- Définir une taxonomie de diagnostics stable:
  `font.source.*`, `font.sfnt.*`, `font.scaler.*`, `text.shaping.*`,
  `text.paragraph.*`, `glyph.artifact.*`, `text.gpu.*`.
- Mettre à jour les dashboards pour refuser les labels génériques comme
  `font missing`.
- Séparer explicitement `outline/path`, `simple-latin atlas`, `complex shaping`,
  `fallback`, `emoji/color`, `SDF`, `LCD`.

Mapping de diagnostics GPU:

| Couche | Namespace attendu |
|---|---|
| Handoff pure Kotlin | `text.gpu.*` |
| Renderer/refus route GPU | `unsupported.text.*` |
| Glyph artifact planning | `glyph.artifact.*` |
| Ancien ou transitionnel | Mapper vers un namespace ci-dessus avant promotion |

Acceptance criteria:

- Une PR qui casse `font:*` échoue en CI.
- Une feature non prouvée est classée selon son blocker réel:
  `tracked-gap`, `DependencyGated`, `fixture-gated` ou `GPU-gated`.
- Les reports listent clairement support, non-support et refus.

Tickets de départ:

- KFONT-000: Wire pure Kotlin font modules into CI.
- KFONT-001: Add pure-kotlin-text specs to CI trigger paths.
- KFONT-002: Introduce stable diagnostic taxonomy.
- KFONT-003: Harden dashboard claim classification.
- KFONT-004: Freeze module/package layout for the pure Kotlin font core.

## M1 - Identité font et sources

Objectif: rendre chaque fonte, face, variation, palette et provenance
reproductible.

Livrables:

- `FontSourceID` fondé sur provenance, content hash, kind, host marker et
  parser generation.
- `TypefaceID` fondé sur source hash, collection index, PostScript name,
  family/style facts, variation coordinates, palette, table facts et scaler kind.
- Modèle explicite pour sources bundled, generated fixture, user data, stream,
  file et system scan.
- Dossier de fixtures minimales pour TTF, TTC, OTF/CFF, variable font,
  malformed directory, missing required table.
- Dumps `font-source.json` et `typeface-id.json`.

Acceptance criteria:

- Deux scans du même dossier produisent les mêmes IDs.
- Une variation ou palette différente change le `TypefaceID`.
- Les fontes système host-dependent sont marquées comme non normatives tant que
  leurs bytes ne sont pas capturés comme fixtures.

Tickets de départ:

- KFONT-010: Complete `FontSourceID` provenance model.
- KFONT-011: Complete `TypefaceID` glyph-affecting identity.
- KFONT-012: Add deterministic source/typeface dumps.
- KFONT-013: Add bundled source fixture manifest.

## M2 - SFNT/OpenType parser durci

Objectif: produire des faits OpenType complets, bornés et isolés des scalers.

Livrables:

- Parser défensif pour SFNT single-face, TTC, OTC.
- Tables obligatoires: `cmap`, `head`, `hhea`, `hmtx`, `maxp`, `name`,
  `OS/2`, `post`, `loca`, `glyf`.
- Tables de shaping: `GDEF`, `GSUB`, `GPOS`, `BASE`, `kern`.
- Tables verticales: `vhea`, `vmtx`.
- Tables variables: `fvar`, `avar`, `gvar`, `HVAR`, `VVAR`, `MVAR`.
- Tables color/bitmap/SVG: `COLR`, `CPAL`, `CBDT`, `CBLC`, `sbix`, `SVG`.
- `cmap` formats 4, 12, 14, 6, 0 et refus diagnostiqué des formats non supportés.
- Dumps `sfnt-directory.json`, `sfnt-tables.json`, `cmap-map.json`.

Acceptance criteria:

- Les tables inconnues restent copiables sans planter.
- Les tables malformées isolent leur erreur sans invalider toute la fonte si la
  spec permet un fallback.
- Les erreurs de bounds/offset/length sont testées.

Tickets de départ:

- KFONT-020: Normalize SFNT/TTC parser entry points.
- KFONT-021: Add bounded table directory diagnostics.
- KFONT-022: Complete cmap format coverage.
- KFONT-023: Add OpenType table fact dumps.
- KFONT-024: Add malformed SFNT fixture suite.

## M3 - TrueType `glyf` complet

Objectif: finaliser le scaler TrueType comme première route outline complète.

Livrables:

- Simple glyph outlines avec `on-curve` et `off-curve`.
- Composite glyphs avec transforms, nested components et cycle detection.
- Metrics horizontales et verticales.
- Bounds exacts et phantom points.
- `gvar` complet pour simple et composite glyphs.
- IUP interpolation, `fvar`, `avar`, HVAR/VVAR/MVAR advances.
- Dumps `glyph-outline.json`, `glyph-metrics.json`, `variation-deltas.json`.

Acceptance criteria:

- Path hashes stables pour fixtures TrueType.
- Bounds et advances stables pour axes min/default/max.
- Glyph malformed produit `.notdef` ou refus diagnostiqué sans crash global.

Tickets de départ:

- KFONT-030: Complete composite glyph transform coverage.
- KFONT-031: Implement TrueType IUP interpolation tests.
- KFONT-032: Add phantom point and advance delta support.
- KFONT-033: Add vertical metric coverage.
- KFONT-034: Add glyf malformed isolation suite.

## M4 - CFF et CFF2 scalers

Objectif: lever le plus gros trou de scaler OTF.

Livrables:

- CFF INDEX/DICT/FDArray/Private parser borné.
- Type 2 charstring interpreter.
- CFF operators: move, line, curve, flex, hint operators ignorés de façon
  contrôlée si non nécessaires au path.
- Subroutines locales/globales avec depth limits.
- CFF metrics, bounds et `.notdef`.
- CFF2 charstrings et variation data.
- Dumps `cff-charstring-trace.json` et `cff2-variation-trace.json`.

Acceptance criteria:

- OTF/CFF fixtures produisent paths, metrics et bounds.
- Recursion/subroutine abuse produit un diagnostic stable.
- CFF/CFF2 ne dépend d'aucun moteur natif.

Tickets de départ:

- KFONT-040: Implement CFF INDEX and DICT parser.
- KFONT-041: Implement Type 2 charstring stack machine.
- KFONT-042: Add CFF subroutine limits and diagnostics.
- KFONT-043: Implement CFF scaler path output.
- KFONT-044: Implement CFF2 variation path output.

## M5 - Unicode segmentation et bidi

Objectif: remplacer les heuristiques minimales par des données Unicode
versionnées.

Livrables:

- Données Unicode pinées dans le repo ou générées de manière reproductible.
- Grapheme cluster segmentation conforme UAX #29 pour les scripts ciblés.
- Word/line segmentation facts réutilisables par paragraph.
- Bidi resolver conforme UAX #9 pour runs de shaping.
- Script itemization avec Script_Extensions.
- Dumps `unicode-segments.json`, `bidi-runs.json`, `script-runs.json`.

Acceptance criteria:

- La version Unicode utilisée apparaît dans chaque dump de shaping.
- Les tests ne dépendent pas silencieusement de la version JDK.
- Emoji ZWJ et combining marks sont cluster-safe même avant le rendu emoji complet.

Tickets de départ:

- KFONT-050: Add pinned Unicode data generation.
- KFONT-051: Replace basic grapheme segmenter.
- KFONT-052: Replace basic bidi resolver.
- KFONT-053: Add Script_Extensions itemizer.
- KFONT-054: Add cluster safety regression suite.

## M6 - OpenType Layout shaping

Objectif: livrer le moteur GSUB/GPOS/GDEF pure Kotlin.

Livrables:

- Pipeline: normalize input -> grapheme clusters -> bidi -> script itemization
  -> fallback segmentation -> cmap -> GSUB -> GPOS -> run compaction.
- GSUB lookups: single, multiple, alternate, ligature, context, chaining context,
  extension substitution and reverse chaining where required.
- GPOS lookups: single adjustment, pair adjustment, cursive, mark-to-base,
  mark-to-ligature, mark-to-mark, contextual positioning, chaining contextual
  positioning, extension positioning, device tables and variation adjustments.
- GDEF glyph classes, ligature caret and mark attachment facts.
- Feature selection: default features by script and explicit user features.
- Script matrix: Latin, Greek, Cyrillic, Hebrew, Arabic, Devanagari, Thai, CJK,
  Emoji.
- Dumps `shaping-plan.json`, `gsub-trace.json`, `gpos-trace.json`,
  `shaped-glyph-run.json`.

Acceptance criteria:

- Each target script has positive fixtures and expected refusals.
- Missing lookup support is diagnostic, not silent.
- `SkCanvas.drawString` remains simple; complex shaping uses explicit APIs.

Tickets de départ:

- KFONT-060: Define `OpenTypeLayoutEngine` contract and dumps.
- KFONT-061: Implement GSUB single/multiple/ligature lookups.
- KFONT-062: Implement GSUB contextual lookups.
- KFONT-063: Implement GPOS single/pair positioning.
- KFONT-064: Implement mark and cursive positioning.
- KFONT-065: Add script-specific default feature policy.
- KFONT-066: Add Arabic shaping fixtures.
- KFONT-067: Add Devanagari shaping fixtures.
- KFONT-068: Add Thai and CJK shaping boundaries.
- KFONT-069: Implement GSUB/GPOS extension, chaining and variation-adjustment lookups.

## M7 - Fallback et system fonts

Objectif: rendre le fallback déterministe et visible.

Livrables:

- Catalog bundled avec family/style/generic/script/locale/emoji facts.
- System scan pure Kotlin, déterministe, marqué host-dependent.
- Fallback policy variable-axis-aware.
- Fallback segmentation cluster-safe.
- Diagnostics pour missing family, missing glyph, unsupported script,
  host-dependent source, conflicting face facts.
- Dumps `font-catalog.json`, `resolved-font-runs.json`,
  `fallback-decision-trace.json`.

Acceptance criteria:

- Aucun fallback platform caché.
- Chaque glyph manquant montre la fonte tentée et le diagnostic final.
- Les fontes système ne deviennent jamais preuve normative sans bytes capturés.

Tickets de départ:

- KFONT-070: Add bundled deterministic font catalog.
- KFONT-071: Add fallback decision trace.
- KFONT-072: Add variable-axis-aware fallback.
- KFONT-073: Add cluster-safe fallback segmentation tests.
- KFONT-074: Add host-dependent system scan diagnostics.

## M8 - Paragraph engine

Objectif: fournir le layout textuel riche au-dessus du shaping.

Livrables:

- `ParagraphBuilder`, `ParagraphStyle`, `TextStyle`, placeholders et spans.
- Rich text multi-style.
- UAX #14 line breaking.
- Bidi visual order for lines.
- Max lines, ellipsis, strut, height, baseline policy.
- Selection boxes, hit testing, caret positions.
- Placeholder metrics and alignment.
- Dumps `paragraph-input.json`, `line-breaks.json`, `paragraph-layout.json`,
  `hit-test-map.json`.

Acceptance criteria:

- Fixtures couvrent wrapping, mixed styles, bidi, placeholders, selection,
  hit testing, ellipsis.
- Layout ne parse pas les fonts directement; il consomme resolver/shaper/scaler
  via contrats.
- Les erreurs de font restent visibles dans `TextRouteDiagnostics`.

Tickets de départ:

- KFONT-080: Expand `TextStyle` and paragraph style contracts.
- KFONT-081: Implement multi-style shaping segmentation.
- KFONT-082: Implement UAX #14 line breaker.
- KFONT-083: Implement ellipsis and max-lines policy.
- KFONT-084: Implement selection and hit-test maps.
- KFONT-085: Implement placeholder layout metrics.

## M9 - Glyph artifacts A8, SDF, outline et cache

Objectif: transformer les shaped runs en artifacts typés sans dépendre du
renderer.

Livrables:

- `GlyphStrikeKey` complet: typeface, size, transform bucket, variation,
  palette, representation, mask format, edging, SDF spread, renderer version,
  Unicode data version.
- `GlyphArtifactPlan` pour outline, A8 mask, SDF mask, color, bitmap, SVG,
  unsupported.
- A8 rasterization complète des outlines quadratiques/cubiques.
- SDF generation complète avec spread/range explicite.
- Atlas packer avec identity, eviction, invalidation et stale diagnostics.
- Cache budget and telemetry.
- Dumps `glyph-artifact-plan.json`, `glyph-atlas.json`,
  `glyph-cache-inventory.json`.

Acceptance criteria:

- Le planner peut partir d'un `ShapedGlyphRun`, pas seulement de
  représentations préfabriquées.
- Aucune allocation GPU dans `font:glyph`.
- Eviction et invalidation sont testées.

Tickets de départ:

- KFONT-090: Complete `GlyphStrikeKey`.
- KFONT-091: Promote `GlyphArtifactPlan` route taxonomy.
- KFONT-092: Implement quadratic/cubic outline rasterization for A8.
- KFONT-093: Implement production SDF generator boundaries.
- KFONT-094: Add atlas eviction and invalidation tests.
- KFONT-095: Add glyph cache telemetry.

## M10 - Color fonts, bitmap, SVG et emoji

Objectif: couvrir les représentations glyph non-outline.

Livrables:

- COLR/CPAL v0 complete layer rendering.
- COLRv1 paint graph support by operation class:
  `PaintSolid`, `PaintVarSolid`, `PaintLinearGradient`,
  `PaintRadialGradient`, `PaintSweepGradient`, variable gradient stops,
  `PaintGlyph`, `PaintColrGlyph`, transforms, translate, scale, rotate, skew,
  composites, clips, cycle limits, recursion budgets and bounds computation.
- PNG bitmap glyph decode for CBDT/CBLC and sbix.
- Pure Kotlin SVG glyph subset renderer:
  static, glyph-scoped, bounded, `use`, `symbol`, `defs`, fill, stroke,
  opacity, color inheritance, bounds, no script, no network, no animation, no
  external resources, and explicit refusals for CSS/dynamic behavior.
- Emoji sequence shaping: variation selectors, ZWJ sequences, keycaps, flags,
  skin tone modifiers, fallback from color to outline.
- Dumps `color-glyph-plan.json`, `colrv1-paint-graph.json`,
  `bitmap-glyph-plan.json`, `svg-glyph-plan.json`, `emoji-route-trace.json`.

Acceptance criteria:

- Metadata-only parsing is not counted as rendering support.
- Unsupported SVG constructs produce specific refusals.
- Emoji fallback is cluster-safe and visible in route diagnostics.

Tickets de départ:

- KFONT-100: Complete COLRv0 plan to artifact path.
- KFONT-101: Implement COLRv1 solid/glyph/colr-glyph operation group.
- KFONT-106: Implement COLRv1 gradient and variable-gradient operation group.
- KFONT-107: Implement COLRv1 transform/composite/clip operation group.
- KFONT-108: Add COLRv1 recursion, cycle and bounds fixtures.
- KFONT-102: Promote PNG bitmap glyph artifacts.
- KFONT-103: Implement bounded SVG glyph renderer primitives.
- KFONT-109: Implement SVG glyph refusal classes and bounds fixtures.
- KFONT-104: Implement emoji sequence planner.
- KFONT-105: Add color/emoji fixture manifest.

## M11 - GPU handoff typé

Objectif: permettre au renderer GPU de consommer le texte sans parser les
fontes, shaper, fallback, décoder PNG/SVG ou construire les atlases.

Livrables:

- `TextLayoutResult` et `GlyphRunDescriptor` consommables par GPU route planning.
- `GPUTextSubRunPlan` splitting by representation, atlas, pipeline and
  dependency gate.
- `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, `ColorGlyphPlan`,
  `BitmapGlyphPlan`, `SVGGlyphPlan`.
- Resource plans, upload plans, instance plans and binding layout plans.
- Ordering tokens proving upload-before-sample and stable draw order.
- `TextGPUArtifactBundle` sans `Sk*`, font bytes, live handles GPU ou full text
  CPU texture.
- `DrawTextRun` ou équivalent dans la normalisation de draw commands.
- Route mapping: atlas A8, atlas SDF, outline, color composite, bitmap texture,
  SVG vector, dependency gated, refused.
- Diagnostics `text.gpu.*` for handoff and `unsupported.text.*` for renderer
  route refusals.
- WGSL parser/reflection validation for text shaders.
- `MaterialKey` leakage tests: no atlas coordinates, glyph IDs, live handles or
  upload tokens in material identity.

Acceptance criteria:

- Le GPU renderer ne dépend pas de `SkFont`, `SkTypeface`, `SkShaper` ou parser
  font.
- Une route CPU-rendered full text texture est rejetée par validation.
- Le support simple Latin atlas reste borné tant que broad shaping/fallback ne
  sont pas promus.

Tickets de départ:

- KFONT-110: Align `font:gpu-api` with target artifact registry.
- KFONT-111: Add no-`Sk*` leakage validation.
- KFONT-112: Add normalized `DrawTextRun` contract.
- KFONT-113: Wire atlas A8 artifact route.
- KFONT-114: Wire dependency-gated diagnostics for unsupported routes.
- KFONT-115: Add `GPUTextSubRunPlan` splitting tests.
- KFONT-116: Add resource/upload/instance/binding plan contracts.
- KFONT-117: Add upload-before-sample ordering validation.
- KFONT-118: Add WGSL parser/reflection validation for text routes.
- KFONT-119: Add `MaterialKey` leakage tests.

## M12 - Performance et telemetry

Objectif: rendre les coûts du système font visibles avant les release gates.

Livrables:

- Metrics parser: bytes read, tables parsed, malformed tables, parse time.
- Metrics scaler: glyphs scaled, cache hits/misses, outline command count.
- Metrics shaping: codepoints, clusters, lookups applied, fallback probes.
- Metrics paragraph: lines, break attempts, hit-test map cost.
- Metrics glyph: artifact count, atlas occupancy, eviction, generation time.
- Metrics GPU handoff: upload bytes, artifact reuse, refused routes.
- Baselines p50/p90/max by fixture.

Acceptance criteria:

- Aucune optimisation ne masque un support gap.
- Les budgets sont indicatifs avant stabilisation hardware.
- Les regressions de telemetry sont visibles en dashboard.

Tickets de départ:

- KFONT-120: Define font telemetry schema.
- KFONT-121: Add parser and scaler metrics.
- KFONT-122: Add shaping and paragraph metrics.
- KFONT-123: Add glyph artifact and cache metrics.
- KFONT-124: Add GPU handoff metrics.

## M13 - Migration de la façade Skia-like

Objectif: faire de `kanvas-skia` un consommateur du cœur pure Kotlin, pas un
deuxième système font divergent.

Livrables:

- Adapter `SkFont`, `SkTypeface`, `SkFontMgr`, `SkShaper`, `SkTextBlob`,
  `SkCanvas.drawString` vers les contrats pure Kotlin là où le périmètre est
  supporté.
- Garder `drawString` simple et déterministe.
- Routes explicites pour complex shaping et paragraph.
- Retirer les stubs qui cachent des refus.
- Garder les non-claims pour LCD, unsupported native parity et SkSL.

Acceptance criteria:

- Les tests Skia-like passent par le même cœur que les tests `font:*`.
- Les anciens chemins outline/path restent seulement comme adaptateurs ou
  fallbacks documentés.
- Les refus historiques sont retirés uniquement avec preuve complète.

Tickets de départ:

- KFONT-130: Add facade adapter inventory.
- KFONT-131: Route `SkTypeface` OpenType facts through pure Kotlin core.
- KFONT-132: Route explicit `SkShaper` APIs through pure Kotlin shaping.
- KFONT-133: Route `SkTextBlob` glyph runs through typed descriptors.
- KFONT-134: Retire stale font docs and stubs after evidence promotion.

## Validation continue

Commande minimale attendue pour une tranche pure Kotlin avec les modules
actuels/candidats du repo:

```bash
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test :font:gpu-api:test
```

Commande d'intégration façade:

```bash
rtk ./gradlew --no-daemon :kanvas-skia:test --tests 'org.skia.foundation.opentype.*' --tests 'org.skia.foundation.SkFont*' --tests 'org.skia.foundation.SkTypeface*' --tests 'org.skia.foundation.SkTextBlob*' --tests 'org.skia.foundation.SkShaper*'
```

Commandes GPU/evidence lorsque le milestone revendique une route GPU. Chaque
route doit garder une commande focused; ne pas utiliser une passe WebGPU globale
comme preuve d'une route A8/SDF/color/bitmap/SVG non exercée.

```bash
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest :gpu-raster:gpuSmokeTest
rtk ./gradlew --no-daemon validateKan054WebGpuGlyphAtlasSamplingRoute
rtk ./gradlew --no-daemon validateKan055TextGlyphAtlasVisualDelta
rtk ./gradlew --no-daemon validateKan056GlyphAtlasRouteHardening
```

Commande PM/dashboard avant promotion de claim:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePerformanceTrendWarnings pipelinePmBundle
```

## Jalons de release recommandés

### R1 - Foundation auditable

Inclut M0, M1, M2 partiel.

Résultat: les claims sont propres, les IDs sont stables, les sources et tables
OpenType ont des dumps. Aucun nouveau support large n'est revendiqué.

### R2 - Outline text fiable

Inclut M2, M3, M5 partiel, M9 outline/A8 partiel.

Résultat: texte simple et glyph outlines TrueType sont prouvés avec IDs,
metrics, outlines, artifacts et diagnostics.

### R3 - Complex shaping MVP

Inclut M5, M6 pour Latin/Hebrew/Arabic/Devanagari bornés, M7 fallback borné.

Résultat: l'API de shaping explicite produit des runs multi-script traçables.
`drawString` reste simple.

### R4 - Paragraph MVP

Inclut M8 sur rich text, wrapping, bidi, ellipsis, selection et hit testing
bornés.

Résultat: paragraph layout pure Kotlin utilisable avec fixtures.

### R5 - Broad font format support

Inclut M4 CFF/CFF2 et variations plus complètes.

Résultat: TTF, TTC, OTF/CFF et CFF2 ont paths/metrics/bounds et diagnostics.

### R6 - Color and emoji MVP

Inclut M10 pour COLRv0, subset COLRv1, PNG bitmap glyphs, SVG subset et emoji
routes bornées.

Résultat: color/emoji GMs peuvent être reclassées avec preuves ou refus fins.

### R7 - GPU text handoff

Inclut M11 et M9/M10 selon routes revendiquées.

Résultat: le renderer GPU consomme des artifacts typés. Le cœur pure Kotlin
reste propriétaire du parsing, shaping, fallback, glyph artifact planning et
atlas preparation.

### R8 - Release candidate font system

Inclut M12 et M13.

Résultat: la façade Skia-like utilise le cœur pure Kotlin, les claims sont
alignés avec les specs, la performance est mesurée, et les gates historiques
sont retirées uniquement lorsque la preuve complète existe.

## Workstreams parallélisables

- Validation/CI peut avancer en continu et doit précéder toute promotion.
- SFNT parser et Unicode data peuvent avancer en parallèle après M0.
- Après M2 et M5, figer la version Unicode, le fixture manifest et les formats
  de dumps avant de lancer massivement M6, M8 ou M10.
- CFF/CFF2 peut avancer en parallèle du shaping une fois M2 stabilisé.
- Paragraph peut démarrer après les premiers contrats shaping, mais ne doit pas
  inventer sa propre résolution font.
- Glyph artifacts peut avancer après TrueType outline stable.
- Color/emoji peut avancer après SFNT color tables et shaping cluster-safe.
- GPU handoff doit attendre des artifacts typés, mais ses tests de fuite `Sk*`
  peuvent être préparés plus tôt.

## Risques principaux

- Sous-estimer GSUB/GPOS: ce bloc doit rester découpé par lookup type et script.
- Déclarer trop tôt le support emoji/color: metadata-only n'est pas un support.
- Laisser `kanvas-skia` diverger du cœur pure Kotlin: chaque nouveau support doit
  avoir un adapter plan.
- Confondre preuve WebGPU simple Latin avec broad text support.
- Utiliser des fontes système comme oracle normatif sans capturer les bytes.
- Ajouter des tolerances larges au lieu de dumps déterministes.

## Première tranche conseillée

La première tranche doit couvrir uniquement M0 et M1, plus les premiers dumps de
M2. Elle est petite mais critique: elle rend les prochaines étapes mesurables.

Résultat attendu:

- CI pure Kotlin font active.
- Taxonomie de diagnostics adoptée.
- Dumps source/typeface/SFNT disponibles.
- Fixtures minimales listées.
- Dashboard empêchant les claims implicites.
- Gates legacy critiques conservées dans ce pack, sans dépendre d'une ancienne
  spec font.

Cette tranche ne doit pas essayer de résoudre CFF, GSUB/GPOS, paragraph, color
ou GPU handoff. Elle rend ces travaux exécutables sans ambiguïté.
