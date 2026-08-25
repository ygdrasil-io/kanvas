# WIP 11 — Preuves RRect/DRRect avancées

> Document temporaire d'exécution. Le code, les tests exécutés et les
> artefacts générés restent les seules sources de vérité du support rendu.

## But

Fermer le sous-ensemble analytique `RRect`/`DRRect` déjà ouvert par les scènes
`scaled-solid-rrect` et `solid-drrect-hole`, sans élargir la vague aux paths,
à l'anti-aliasing, aux strokes ou aux clips complexes.

La vague doit déterminer, à travers la route publique Kanvas `Surface`, si le
renderer WebGPU produit exactement les pixels attendus pour des rayons
elliptiques indépendants par coin. Un cas qui fonctionne est promu avec ses
preuves. Un cas qui échoue est corrigé dans la route de production réellement
utilisée par Kanvas avant toute promotion ; aucun seuil n'est relâché.

## Sources de vérité à vérifier

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RRectF32.kt` pour les
  quatre paires de rayons publiques ;
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` pour
  l'abaissement public `DisplayOp.DrawRRect` et `DisplayOp.DrawDRRect` ;
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
  et `GPURRectNormalizer.kt` pour les géométries normalisées ;
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitiveAnalyticShapeUniformAbi.kt`
  et `execution/GPUCorePrimitiveNativeShader.kt` pour le packing et le test de
  membership WGSL ;
- `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt`
  et les résultats du vérificateur pour le verdict final.

Les documents Markdown ne doivent jamais être cités comme preuve de support.

## Scènes bornées

Les trois scènes utilisent un fond opaque, un `Paint.fill` opaque,
`antiAlias = false`, `BlendMode.SRC_OVER`, aucune paint effect, aucun clip et
une matrice identité.

| ID | Géométrie locale | Attente |
| --- | --- | --- |
| `asymmetric-solid-rrect` | bounds `[8,8,56,56]`, rayons TL `(4,8)`, TR `(10,4)`, BR `(8,12)`, BL `(6,3)` | Rendu analytique exact, sans fallback. |
| `ellipse-solid-rrect` | bounds `[12,20,52,44]`, quatre rayons `(20,12)` | Cas limite où le RRect est une ellipse complète, rendu exact. |
| `asymmetric-solid-drrect-hole` | outer `[6,8,58,56]` avec TL `(4,8)`, TR `(10,4)`, BR `(8,12)`, BL `(6,3)` ; inner `[20,20,44,44]` avec TL `(2,4)`, TR `(6,2)`, BR `(4,6)`, BL `(3,2)` | Outer moins inner exact, sans remplissage du trou. |

Ces IDs sont littéraux dans le catalogue et les scènes utilisent uniquement
le Canvas public via `KanvasSurfaceProgram`. Aucun programme de
`gpu-renderer-scenes` et aucun nouvel adaptateur de test n'est autorisé.

## Oracle indépendant

`SurfaceSrgbRRectCpuOracle` est étendu pour représenter quatre paires de rayons
par RRect tout en conservant les appels uniformes existants. Pour chaque centre
de pixel `(x + 0.5, y + 0.5)`, l'oracle :

1. rejette les points hors bounds ;
2. sélectionne indépendamment le quadrant de coin concerné ;
3. applique l'équation d'ellipse normalisée de ce coin ;
4. inclut le point dans l'outer et l'exclut s'il appartient à l'inner ;
5. écrit les bytes RGBA8 opaques littéraux attendus.

Le calcul de l'oracle ne réutilise ni le normalizer, ni le packing, ni le WGSL
de production. Les tests utilisent des pixels littéraux choisis à l'intérieur,
à l'extérieur et près de chacun des quatre coins afin qu'une permutation ou
une uniformisation accidentelle des rayons fasse échouer le test.

## Politique de verdict

- `maxChannelDelta = 0` ;
- similarité requise `100.0` ;
- `fallbackReason = none` ;
- route native WebGPU non-fallback ;
- diagnostic de géométrie et compteurs de draw/pipeline présents ;
- aucune promotion sur un backend synthétique ou une capture estimée.

Avant toute correction de production, les nouvelles scènes et l'oracle sont
exécutés contre le code de `origin/master`. Une divergence doit être classée
entre oracle, normalisation, packing ABI, WGSL, bounds ou sélection de route.
La correction minimale est alors écrite en TDD dans Kanvas ou `gpu-renderer`,
selon la route réellement fautive. Les preuves finales restent sous
`reports/gpu-renderer/evidence/` ; aucune donnée n'est ajoutée à l'ancien
report fourre-tout.

## Tests et promotion

La vague doit couvrir :

- les pixels indépendants de l'oracle pour les quatre coins et le trou ;
- l'enregistrement exact des trois scènes par le Canvas public ;
- le catalogue, les IDs, les policies et la route attendue ;
- les garde-fous existants de normalisation et de transform, sans élargir leur
  périmètre ;
- la capture native des trois scènes ;
- `verifyPromotedGpuEvidence` sur tout le catalogue promu ;
- les suites ciblées touchées puis la gate GPU evidence complète, en séparant
  toute défaillance déjà reproductible sur `origin/master`.

La promotion est atomique : chaque scène apporte son CPU PNG, GPU PNG, diff,
stats, route, diagnostics, environnement, manifeste et verdict. Si l'oracle
est faux ou si une correction sortirait du périmètre analytique borné, la scène
reste non promue et la vague s'arrête avec le diagnostic précis.

## Hors périmètre

- `drawOval`, `drawCircle`, `drawPath` et la route stencil-cover ;
- anti-aliasing et MSAA ;
- stroke, path effects, mask/image/color filters et blenders ;
- clip RRect/path et transforms non axis-aligned ;
- nouveaux codes de support larges ou changements de seuil globaux ;
- modifications de `gpu-renderer-scenes`.
