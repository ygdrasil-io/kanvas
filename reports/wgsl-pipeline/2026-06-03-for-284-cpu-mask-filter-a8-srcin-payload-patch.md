# FOR-284 CPU Mask Filter A8 SrcIn Payload Patch

Linear: `FOR-284`

Scene: `m60-bounded-nested-rrect-clip`

Patch decision: `CPU_MASK_FILTER_A8_SOLID_COLOR_FILTER_PAYLOAD_PATCH_APPLIED`

Support decision: `KEEP_EXPECTED_UNSUPPORTED_AFTER_TARGETED_CPU_PAYLOAD_PATCH`

## Resultat

Le chemin CPU `drawPathWithMaskFilter` garde l'ancien dispatch direct pour les
cas non concernes. Pour le cas solid A8 avec
`SkColorFilters.Blend(..., kSrcIn)`, il construit maintenant la source masquee,
applique ce color filter sur le payload, puis envoie le resultat a
`dispatchBlend`.

## Payload Cible

| Mesure FOR-283 | Valeur |
|---|---:|
| Pixels cibles | 89 |
| Source dispatch rouge | 89 |
| Resultat blend teinte rouge | 89 |
| Readback CPU rouge dominant | 9 |
| Readback CPU blanc/layer | 78 |

Test Kotlin cible:
`For275CpuSrcInBlurLayerFixtureTest.FOR-284 A8 solid SrcIn color filter composites the mask payload`.

## M60 Avant / Apres

| Mesure | Avant FOR-284 | Apres FOR-284 |
|---|---:|---:|
| CPU similarity | 97.31 | 97.31 |
| CPU matching pixels | 908439 | 908439 |
| CPU max channel delta | 237 | 237 |
| CPU/ref >32 | 15726 | 15726 |
| Integration test similarity | n/a | 97.31 |
| GPU similarity | 98.48 | 98.48 |
| GPU matching pixels | 919363 | 919363 |
| GPU max channel delta | 57 | 57 |
| GPU/ref >32 | 2869 | 2869 |

## Routes Et Diagnostics

| Champ | Valeur |
|---|---|
| CPU route | `cpu.coverage.nested-rrect-clip-oracle` |
| GPU route | `webgpu.coverage.nested-rrect-clip.expected-unsupported` |
| GPU status | `expected-unsupported` |
| Fallback visual parity | `coverage.nested-clip-visual-parity-below-threshold` |
| Fallback crop | `image-filter.crop-input-nonnull-prepass-required` |

## Decision

`KEEP_EXPECTED_UNSUPPORTED_AFTER_TARGETED_CPU_PAYLOAD_PATCH`.

La correction est limitee au payload CPU A8 solid + `Blend(..., kSrcIn)`. Les
metriques pleine scene M60 ne s'ameliorent pas assez pour promouvoir la scene;
les diagnostics `coverage.nested-clip-visual-parity-below-threshold` et
`image-filter.crop-input-nonnull-prepass-required` restent inchanges.

Artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-cpu-mask-filter-a8-srcin-payload-patch-for284.json`
