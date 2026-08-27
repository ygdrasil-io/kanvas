# Task 6 — clip path cubique compositionnel

## Statut

Implémenté sur `codex/gm-activation-wave-6`, empilé sur
`codex/gm-activation-wave-5` (`5e121020f`).

Le subset natif est volontairement étroit : un seul clip path fermé, fini,
non-AA, dans `RenderConfig.maxPathVertices`, avec `Intersect` ou `Difference`
et `Winding` ou `EvenOdd`. Le chemin passe par le stencil-cover WebGPU
headless/offscreen existant, sans Ganesh, Graphite, SkSL dynamique ni
`gpu-renderer-scenes`.

## Changement

- Le lowering garde `hasCubicSegments` dans l’élément clip, son égalité et sa
  clé canonique.
- `Difference + EvenOdd` rejoint le stencil natif borné : producteur
  `Invert/Invert`, consommateur `Equal`.
- Les cubic inverses refusent de façon stable avec
  `unsupported.clip.inverse_cubic`; les chemins inverse historiques non-cubic
  ne sont pas reclassifiés.
- Perspective et dépassement du budget restent sur leurs refus existants,
  respectivement `unsupported_transform:Perspective` et
  `unsupported.clip.vertex_budget`.

## Preuves

- Fixture WebGPU : les quatre variantes cubic `Winding/EvenOdd ×
  Intersect/Difference` rendent un buffer 32×32 complet contre un oracle CPU
  réellement exécuté et indépendant (16 384 canaux RGBA, diff 0,
  `tolerance = 0`). Les pixels adjacents aux arêtes cubic et le centre du trou
  sont contrôlés explicitement.
- Deux contours cubic de même orientation distinguent les règles : `Winding`
  conserve le centre, `EvenOdd` le retire. Les quatre états stencil sont
  contrôlés directement dans l’inventaire de route.
- La fixture positive est à 190 vertices après flattening et utilise
  explicitement `RenderConfig(maxPathVertices = 256u)`; le refus de budget est
  maintenant lui aussi déclenché par un cubic surdimensionné.
- Fixture de refus WebGPU : cubic inverse →
  `unsupported.clip.inverse_cubic`.
- Artefacts complets :
  `reports/gpu-renderer/evidence/bounded-cubic-clip-2026-08-27.md` et JSON
  voisins (`cpu`, `gpu`, `diff`, `stats`, `route`, `refusals`).
- Aucun GM n’a été modifié ou promu. Le runner frais fige `clipcubic` à 17
  opérations / `unsupported.stroke.width_invalid` et `clippedcubic` à 19 /
  `unsupported.core_primitive.stencil_edge_fan_budget`.

## Vérification

```text
rtk ./gradlew --no-daemon --rerun-tasks :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageContractsTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest

rtk ./gradlew --no-daemon --rerun-tasks :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.CurvedClipGmSurfaceRefusalEvidenceTest
```

Résultat : succès (75 + 23 + 108 tests Kanvas, puis 1 test runner GM).

## Concerns

- L’oracle compare le buffer entier, y compris les pixels adjacents aux arêtes,
  mais ne revendique pas la fidélité AA : le contrat reste strictement non-AA.
- Les clips inverses cubic, perspective, multi-éléments, AA et hors budget
  restent hors périmètre. Aucune solution de contournement CPU/masque n’a été
  ajoutée pour les promouvoir.
