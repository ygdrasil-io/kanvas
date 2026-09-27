# 2A0b.Ia — Empty W6a

## Status

Implémentation verticale partielle : recette planner-owned `W6FullscreenEmptyRecipeV1`, catalogue canonique, construction finale depuis `resources + source.resources`, scellement `PlanPhysicalLayoutV1`, préflight renderer avant la première allocation, et traduction fullscreen avec group 0 réellement vide.

## Commit

`feat(gpu-plan): freeze W6 empty native recipes` (commit local HEAD)

## Tests

- `rtk ./gradlew :gpu-plan:compileKotlin` — PASS.
- `rtk ./gradlew :gpu-renderer:compileKotlin` — PASS.
- `rtk ./gradlew :kanvas:compileTestKotlin` — PASS (dans l’invocation combinée).
- `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest --tests org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest` — 32 PASS, 4 FAIL, native exit 133.

Les quatre échecs sont `emptyLayerWithoutAnySourceRemainsTransparent`, `restore capability rejects active oversized composed filter before mutation and skips elided scope after recovery`, `sourceUniformBudgetRefusesPreciselyAndRecovers`, et `siblingTargetsExceedBudgetAtomicallyAndRecoverWithOneLayer`, tous avec `w6a.layer.unsupported_child: Required value was null`.

## Limites

Les variantes Ib/Ic, leases/budget B, W4e/W5a, et l’authentification globale 2B restent hors scope. Native 133/134 est UNKNOWN pour cette tranche car l’exécuteur a terminé en 133 après les assertions ci-dessus.
