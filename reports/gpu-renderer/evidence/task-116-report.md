# W140 — Subdivision adaptative des courbes cubiques

## Périmètre

La vague traite le refus réel observé sur les GM Skia `clipcubic` et
`clippedcubic`. La cause était la subdivision uniforme des cubics dans
`gpu-renderer` : avec `curveTolerance = 0.25`, un seul cubic produisait plus de
1 024 arêtes et dépassait le contrat du stencil edge-fan.

Le correctif reste dans `PathTessellator` et ne change ni le contrat WebGPU ni
les budgets publics. Les cubics sont maintenant subdivisés par De Casteljau,
selon leur flatness (écart des points de contrôle à la corde), avec une
profondeur maximale de 16. Les budgets de vertices, triangles et octets restent
les refus de dernier ressort.

## Résultats des preuves

- `PathTessellatorTest` : 29 tests, 0 échec. Le cubic vertical de `clipcubic`
  reste sous les 1 024 triangles du payload et le test de budget excessif
  conserve un refus stable.
- `GPUClipCoverageContractsTest` : 24 tests, 0 échec. Les clips cubiques
  conservent leurs fill rules, opérations et limites explicites.
- `GPUFramePathApiInventoryTest` : 144 tests, 0 échec. Le trou cubique garde
  le même état stencil pour `Winding`/`EvenOdd` et `Intersect`/`Difference`;
  seule la densité de flattening n’est plus figée à une valeur historique.
- `ClippedCubicGmTest` : rendu WebGPU headless/offscreen, 10 opérations
  dispatchées, 0 refus, similarité `99.52191894127378 %` contre
  `reference/clippedcubic.png` (seuil GM `94.3 %`), erreur moyenne
  `0.0016432698389529509`.
  Les images GPU, diff, référence et les statistiques sont conservées dans
  `clipped-cubic-gm-2026-08-29/`. Le CPU oracle complet de ce GM 1240×390 est
  explicitement refusé (`unsupported.cpu.oracle_not_available`) : la PNG Skia
  reste une référence externe et n’est pas présentée comme oracle CPU.
- `CurvedClipGmSurfaceRefusalEvidenceTest` : `clippedcubic` est promu sur la
  route cubic bornée; `clipcubic` reste un refus explicite
  `unsupported.stroke.width_invalid` (17 opérations), car son second dessin
  est un hairline stroke anti-aliasé hors du contrat de stroke actuel.

## Politique de refus conservée

Un cubic réellement trop grand pour les budgets reste refusé par
`geometry.path.vertex_budget_exceeded` ou
`geometry.path.fan_budget_exceeded`; aucun budget ABI n’a été augmenté et
aucun fallback CPU caché n’a été introduit. La prochaine extension séparée est
le hairline cubic de `clipcubic`.

## Commandes de vérification

```text
rtk ./gradlew :gpu-renderer:test
rtk ./gradlew :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageContractsTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest
rtk ./gradlew :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.gm.path.ClippedCubicGmTest \
  --tests org.graphiks.kanvas.skia.CurvedClipGmSurfaceRefusalEvidenceTest
```

Le run complet `:gpu-renderer:test` conserve deux échecs préexistants sans
rapport avec cette vague : le test de package-boundary (cycles déjà présents)
et un ordre de refus de gradient dans `FirstRoutePlannerTest`. Les suites
directement touchées par W140 passent intégralement.
