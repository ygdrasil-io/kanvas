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
  Intersect/Difference` rendent correctement les échantillons intérieur et
  extérieur contre un oracle CPU indépendant (32 canaux RGBA, diff 0).
- Les états stencil sont contrôlés directement dans l’inventaire de route.
- Fixture de refus WebGPU : cubic inverse →
  `unsupported.clip.inverse_cubic`.
- Artefacts complets :
  `reports/gpu-renderer/evidence/bounded-cubic-clip-2026-08-27.md` et JSON
  voisins (`cpu`, `gpu`, `diff`, `stats`, `route`, `refusals`).
- Aucun GM n’a été modifié ou promu; `clipcubic`/`clippedcubic` restent hors de
  ce contrat mono-clip non-AA et conservent leur statut de refus.

## Vérification

```text
rtk ./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageContractsTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest
```

Résultat : succès (les trois classes complètes ont passé).

## Concerns

- L’oracle de la fixture ne compare intentionnellement que des échantillons
  éloignés des arêtes ; il ne revendique pas la fidélité AA des bords cubiques.
- Les clips inverses cubic, perspective, multi-éléments, AA et hors budget
  restent hors périmètre. Aucune solution de contournement CPU/masque n’a été
  ajoutée pour les promouvoir.
