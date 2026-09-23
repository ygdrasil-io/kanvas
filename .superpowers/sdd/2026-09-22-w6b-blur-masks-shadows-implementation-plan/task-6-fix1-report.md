# Task 6 fix round 1 — R17 shadow sampling and topology

Base de correction : `0a7939ba4`  
Ruling : R17 du ledger W6b  
Statut : **DONE_WITH_CONCERNS** (JUnit vert ; fin GPU native 133 = UNKNOWN).

## RED

- L'oracle Surface public avec offset `(1.5,-0.5)`, translation et bord
  transparent a observé `alpha attendu 40`, `alpha rendu 160`. Il a isolé le
  nearest sampling `floor`/`textureLoad`, pas l'exit natif qui survient ensuite.
- La fixture budgétaire `SHADOW_ONLY` a été recalibrée publiquement après
  retrait du composite interne. Des tentatives sous la frontière (`320`, `332`)
  refusaient avant rendu ; `336` est la frontière B exacte et `335` est B−1.

## GREEN

- Le plan scelle `DropShadowLinearSamplingV1` : matrice/translation F64
  target-local et deux footprints target-local. `translatedBounds` garde
  required input, desired et produced du support F64 déplacé et arrondi vers
  l'extérieur. Le witness lie ces footprints aux targets gelés.
- Le renderer shadow n'a aucune soustraction d'origines. Il consomme les
  coordonnées et footprints publiés, fait quatre taps DECAL et une interpolation
  linéaire. Les offsets `COMPOSITE` sont également gelés plan-side.
- `SHADOW_ONLY` est le colorized terminal. Aucun pass/target
  `DROP_SHADOW_COMPOSITE` n'est publié ; le validator et le materializer
  n'acceptent ce pass qu'en `COMPOSITE` avec deux sources gelées.
- Les oracles publics prouvent Picture blur+offset `SHADOW_ONLY`, B/B−1,
  nested budget, refus de sibling tardif et recovery ; SrcOver avec source
  alpha 128/shadow alpha 96 ; siblings avant/layer/après ; replay mémoire et
  wire des deux modes avec bounds/origines non triviaux.

## Preuve du contrat DECAL

R17 et
[SkDropShadowImageFilter.cpp](https://skia.googlesource.com/skia/+/e5fda8472b21/src/effects/imagefilters/SkDropShadowImageFilter.cpp)
imposent un `MatrixTransform` en filtering linéaire pour l'offset public
fractionnaire. `DropShadow` n'ayant pas de TileMode public, le support hors
domaine est transparent : DECAL. Ce choix a été vérifié avant l'oracle et ne
reproduit pas l'ancienne implémentation. CLAMP aurait artificiellement étendu
le bord source et est donc refusé ici.

## Budget et custody

Fixture Surface 2×1 :

`B = root 8 + aggregate/source 2×4 + X/Y/color 3×4 + uniforme W5 52 + readback aligné 256 = 336`.

`SHADOW_ONLY` ne paie aucun composite interne. `B_nested = 344` (+ target layer
2×1). B est admis ; B−1 rend `w6b.filter.frame_budget_exceeded` avant
allocation. Le sentinel reste intact lors du refus tardif et la même Surface
réussit après discard/re-record. Les semantic/physical peaks, slots et
lifetimes restent plan-owned ; aucun fake device, reflection, compteur interne
ou infrastructure test n'est utilisé.

| Gate | Résultat |
| --- | --- |
| compilation gpu-plan/gpu-renderer/kanvas tests | GREEN, exit 0 |
| `RenderGraphContractTest` | GREEN, exit 0 |
| W6b public ciblé | 74/74 JUnit PASS, puis 133 / UNKNOWN |
| W6a budget + W4/W5 + nested | 39/39 JUnit PASS, puis 133 / UNKNOWN |

## Audit, exclusions, auto-review

Audit manuel seulement, sans test de forme/source : pas de référence W6b aux
anciens `GPUSeparableBlurRectFrameRecorder`,
`GPUTopLevelMaskBlurFrameRecording`, `GPUPreparedFilterDAGPlanner`,
`GPUPreparedMaskFilterLowerer` ou `GPUDropShadow`. Ces définitions legacy/W8
restent hors route. Il n'existe aucune création renderer-side de pass/source/
bound/budget ni `.toInt()` pour une coordonnée shadow ; les `ResourceSpec` sont
créés dans `:gpu-plan` avant freeze.

Exclusions inchangées : backdrop, filtered previous, F16/HDR, W6c/W6d, fonts,
codecs, GMs/dashboard/renders/baselines/scores, global Skia, `jpg-color-cube`
et device-loss/visibilité. Ce correctif ne revendique pas de convergence
ISO/globale. Auto-review : les trois findings Important sont couverts par des
valeurs plan-owned, une topologie distincte par mode et des preuves publiques
discriminantes ; le seul concern restant est le 133 post-JUnit non attribué.
