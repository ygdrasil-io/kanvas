# Inventaire GM non-font — replay `origin/master`

Cette vague établit une photographie reproductible des GMs Skia non-font
disponibles dans Kanvas. Elle n’ajoute aucune route de rendu et ne constitue
pas une promotion de support.

## Périmètre et preuve

- Source commit : `9d2194b403ff5e4fde8e07d13b7b3b154c592d96`.
- 493 GMs chargés, non-`TEXT`, `RenderCost` non bloquant.
- Replay par `RenderFamily` avec `generateSkiaRendersFor`, dans un répertoire
  temporaire, puis comparaison par `generateSkiaDashboard` sans relancer les
  renders.
- 102 renders GPU produits ; 86 ont une comparaison de référence ; 16 restent
  sans score (référence absente, non fiable ou dimensions incompatibles).
- 363 refus explicites, 28 échecs d’exécution, 0 timeout observé.
- Aucun mismatch sous le seuil déclaré par chaque GM parmi les 86 lignes
  scorées. Les scores faibles restent des observations et ne sont pas des
  claims de support.

Les données ligne par ligne sont dans
[`evidence.json`](./evidence.json) et [`classification.csv`](./classification.csv).

La source de vérité est le code et les tests suivants, pas ce rapport :

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistry.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGenerator.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaDashboardGenerator.kt`
- `integration-tests/skia/test-similarity-scores.properties`

## Ce que montre l’inventaire

| Route observée | Lignes | Lecture |
|---|---:|---|
| GPU rendu | 102 | La route existante produit un framebuffer ; cela ne suffit pas à déclarer le GM supporté. |
| Refus explicite | 363 | Refus diagnostiqués par code stable ; à regrouper avant toute nouvelle implémentation. |
| Échec d’exécution | 28 | Findings natifs/preflight à trier avant promotion d’une route concernée. |

Les principaux codes de refus sont `unsupported.geometry.path_key_nondeterministic`
(82), `unsupported.material.source_unimplemented` (52),
`unsupported.core_primitive.stencil_edge_fan_budget` (35),
`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted` (27) et
`unsupported.composite.paint` (21). Le détail complet est dans `evidence.json`.

## Ordre proposé pour les vagues suivantes

| Priorité | Cluster | Lignes de refus | Route à traiter | Sélection bornée |
|---:|---|---:|---|---|
| 1 | Path/clip | 161 | `PathClipCoverage` | courbes, contours multiples, `Intersect`/`Difference`, `Winding`/`EvenOdd`/inverse |
| 2 | Transformations | 2 | scale uniforme puis rotation contrôlée | affine limitée, sans perspective |
| 3 | Strokes | 16 | strokes simples | RRect/path, caps/joins bornés |
| 4 | Gradients | 25 | gradients sous clip/transform | seulement les variantes déjà déterministes |
| 5 | Compositing | 35 | `saveLayer`/blend | opacity, isolation, modes bornés |
| 6 | Images | 31 | image shader/sampling | uniquement après livraison des dépendances codec/image |
| 7 | Filtres | 2 | filtres simples | après stabilisation des routes précédentes |

Le tableau ci-dessus compte uniquement les refus (`outcome=refused`). Les
échecs natifs restent séparés dans `summary.byFailureCode` et dans les lignes
`outcome=failed` de `classification.csv` ; les agrégats complets et non-GPU
sont explicitement distingués dans `evidence.json` (`byCluster`,
`byNonGpuCluster`, `byRefusalCluster`).

Ces nombres sont des clusters d’inventaire, pas des objectifs de support. Une
vague d’implémentation devra sélectionner quelques GMs représentatifs et
fournir pour chacun une référence, un oracle CPU, une preuve GPU native (ou un
refus stable), les diff/statistiques, les diagnostics de route et la promotion
au dashboard uniquement après validation.

## Findings natifs à garder visibles

Les 28 échecs hors refus incluent notamment :

- `invalid.surface.prepared.frame-build-contract` (6) ;
- `failed.prepared-surface.materialization` (5) ;
- `invalid.preflight.core_primitive_direct_geometry_resources` (5) ;
- `stale.preflight.resource_generation` (2) ;
- des erreurs ponctuelles de clip/stencil, destination read et accounting.

Ils sont conservés comme findings dans les données et ne sont pas convertis
en refus attendus.

## Non-claims

- aucune modification de `gpu-renderer`, de Kanvas ou de `gpu-renderer-scenes` ;
- aucune modification de seuil, de fallback ou de `PipelineKey` ;
- aucune revendication de parité Skia, de couverture globale ou de support des
  fonts/codecs ;
- les GMs bloquants et `TEXT` restent hors périmètre de cette vague.
