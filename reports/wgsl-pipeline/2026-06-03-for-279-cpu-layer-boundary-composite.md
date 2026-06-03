# FOR-279 CPU Layer Boundary Composite

Linear: `FOR-279`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `REFUSE_CORRECTION_PENDING_DEEPER_LAYER_COMPOSITE_MODEL`

Support scene: `KEEP_EXPECTED_UNSUPPORTED`

FOR-279 audite le levier `TARGET_CPU_LAYER_BACKGROUND_COMPOSITE_AROUND_DIFFERENCE_CLIP_BOUNDARY`
apres FOR-278. Aucun renderer n'est modifie: la correction est refusee parce
que la scene source n'utilise pas `saveLayer`. Le chemin causal observe est le
draw CPU `clipRRect(kDifference)` + `drawRRect` avec `SkBlurMaskFilter` et
`Blend(RED, kSrcIn)`, pas `SkBitmapDevice.compositeFrom`.

## Compteurs FOR-278

| Mesure | Avant | Apres sans patch renderer | Delta |
|---|---:|---:|---:|
| Pixels fixture | 148 | 148 | 0 |
| CPU/reference >32 | 89 | 89 | 0 |
| CPU blanc/fond sur >32 | 78 | 78 | 0 |
| CPU blanc/fond share | 87.640449% | 87.640449% | 0 |
| CPU alpha >32 | 0 | 0 | 0 |
| GPU/reference >32 | 11 | 11 | 0 |

## Compteurs M60 Pleine Scene

| Mesure | Avant | Apres sans patch renderer | Delta |
|---|---:|---:|---:|
| CPU/reference similarity | 97.31% | 97.31% | 0 |
| CPU matching pixels | 908439 | 908439 | 0 |
| CPU max channel delta | 237 | 237 | 0 |
| CPU/reference >32 | 15726 | 15726 | 0 |
| GPU/reference similarity | 98.48% | 98.48% | 0 |
| GPU/reference >32 | 2869 | 2869 | 0 |

## Chemin CPU Identifie

1. `BlurredClippedCircleGM.onDraw`: `save()` puis `clipRRect(kDifference)` puis `drawRRect`.
2. `SkCanvas.clipPathDifference`: construit le `SkAAClip` difference.
3. `SkBitmapDevice.drawPathWithMaskFilter`: genere le masque de flou puis compose via `clipCoverage`.

Le chemin exclu est `SkBitmapDevice.compositeFrom`: il correspond au retour de
`saveLayer`, absent de cette GM. Corriger ce composite serait donc non causal
pour les pixels FOR-278.

## Refus

`REFUSE_CORRECTION_PENDING_DEEPER_LAYER_COMPOSITE_MODEL`.

Le refus est stable: FOR-275 montre `0` pixel different entre direct et
`saveLayer` dans la fixture minimale; FOR-276 recupere le halo dans une fixture
AA bornee mais laisse M60 inchange; FOR-278 reste a
`89` pixels CPU/reference
>32 et `78` pixels blanc/fond dans les
fenetres ciblees.

## Decision De Support

M60 reste `expected-unsupported` avec fallback `coverage.nested-clip-visual-parity-below-threshold`.
`image-filter.crop-input-nonnull-prepass-required` est preserve. Aucun seuil n'est affaibli,
aucun support large clip-stack/readback n'est ajoute, et aucun chemin
Ganesh/Graphite/SkSL n'est introduit.

## Prochaine Action

`TARGET_CPU_AA_DIFFERENCE_CLIP_COVERAGE_EDGE_MODEL`.

Le prochain ticket doit auditer le modele `SkAAClip` difference autour du bord
AA: coverage par pixel, convention de sample, et composition avec le masque
floute. C'est le point causal restant avant toute correction CPU pleine scene.

## Validation

```text
rtk python3 scripts/validate_for279_cpu_layer_boundary_composite_refusal.py
rtk python3 scripts/validate_for278_m60_boundary_layer_composite_fixture.py
rtk python3 scripts/validate_for277_m60_post_for276_cpu_residual_audit.py
rtk python3 scripts/validate_for276_cpu_mask_filter_clip_order.py
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-layer-boundary-composite-for279.json`
