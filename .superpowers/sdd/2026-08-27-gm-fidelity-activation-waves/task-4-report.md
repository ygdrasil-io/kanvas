# Task 4 — strokes simples (2026-08-27)

## Résultat

Le sous-ensemble natif est volontairement borné à un contour ouvert d’exactement
deux points finis, largeur finie `0.5..64`, sans dash ni autre `PathEffect`,
avec cap `butt` ou `square`, join `miter` et limite de miter finie `>= 1`.
Les path effects non-dash sont désormais conservés dans la commande normalisée
afin d’être refusés au lowering, au lieu d’être perdus entre le mapper et
CorePrimitive.

La fixture `simple-stroke-butt-miter-v1` rend nativement un segment butt/miter
rouge de 4 px dans une cible 32×32 WebGPU headless/offscreen. Son oracle CPU et
le readback GPU sont identiques: 4 096 canaux, delta maximal 0, une submission
et une copie de readback. La couverture vérifiée est `Stencil1x`; `ScalarAA`
ne peut pas être promu pour la géométrie stroke.

## Refus fermés

Les refus restent explicites et stables pour largeur non finie, hairline,
largeur hors `0.5..64`, dash, `PathEffect` non supporté, cap round, join
round/bevel, limite de miter invalide et contour qui n’est pas un seul segment.
Le budget maximal n’a pas changé.

Un `PathEffect.Dash` vide est maintenant conservé comme `pathEffectKind=Dash`
et refusé comme dash, y compris depuis l’API publique `Surface`. Le contrat de
limite de miter est uniforme: valeur finie `>= 1`; `0`, `NaN` et `Infinity`
sont refusés par la route, la validation de couverture et le plan de refus.
`GPUStrokeDescriptor` reçoit aussi l’identité de path effect afin qu’un Dash
vide n’apparaisse jamais comme candidat native solide.

## GMs

Aucun GM n’est promu et aucun GM n’a été modifié. Les routes terminales sont
couvertes par `SimpleStrokeGmSurfaceRefusalEvidenceTest`:

| GM | Opérations | diagnostic terminal stable |
| --- | ---: | --- |
| `strokedline_caps` | 13 | `unsupported.material.mapping.linear_gradient_stop_count` |
| `strokes_round` | 401 | `unsupported.stroke.expansion_budget_exceeded` |
| `dashcircle` | 25 | `unsupported.pipeline.capability_missing` |

`strokedline_caps` ne constitue donc pas un oracle exact du sous-ensemble: il
contient aussi un gradient à trois stops et un cap round. `strokes_round` et
`dashcircle` restent hors périmètre/bloqués avant toute promotion.

## Preuves et tests

Artifacts complets: `reports/gpu-renderer/evidence/simple-stroke-butt-miter-2026-08-27/`
(`route.json`, `cpu.json`, `gpu.json`, `diff.json`, `stats.json`,
`refusals.json`).

Tests ciblés passants:

```text
./gradlew --no-daemon :kanvas:test --tests GPUFramePathApiInventoryTest \\
  --tests GPUFramePathApiInventoryNativeSmokeTest :gpu-renderer:test \\
  --tests GPUCorePrimitiveCoverageSampleAuthorityTest \\
  --tests SimpleStrokePreparedRouteTest
```

Le test GM de refus est exécuté séparément; il exige WebGPU et stabilise les
trois diagnostics terminales ci-dessus. Aucun Ganesh, Graphite, SkSL dynamique
ni scène interactive n’est utilisé.

Commandes de reproduction:

```text
./gradlew --no-daemon :kanvas:test --tests GPUFramePathApiInventoryTest \\
  --tests GPUFramePathApiInventoryNativeSmokeTest :gpu-renderer:test \\
  --tests SimpleStrokePreparedRouteTest \\
  --tests GPUCorePrimitiveCoverageSampleAuthorityTest
./gradlew --no-daemon :integration-tests:skia:test \\
  --tests SimpleStrokeGmSurfaceRefusalEvidenceTest
./gradlew --no-daemon :gpu-renderer:check
```

La vérification élargie `:gpu-renderer:check` reste en échec sur sept tests
déjà présents dans le baseline de vague 3, hors des fichiers de cette tâche:
un guard de cycles de packages, deux attentes de gradient repeat restées sur
l’ancien diagnostic, un test de payload materializer et trois compteurs de
pipeline. Les tests stroke, native WebGPU et GM ciblés ci-dessus passent.

Les compteurs de performance sont descriptifs et non-gating; aucune politique
de seuil de similarité ou de performance n’a été ajoutée, abaissée ou relâchée.
