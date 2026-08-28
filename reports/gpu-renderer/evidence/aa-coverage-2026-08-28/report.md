# W22 — couverture anti-aliasée bornée

## Route promue

`fractional-aa-rect-overlap` atteste la route publique `Kanvas Surface` pour
deux rectangles solides opaques à bords demi-pixel, composés dans l'ordre sous
un clip rect entier qui coupe réellement le bord droit du second rectangle
(pixel `(45,30)` bleu, `(46,30)` préservé au fond). La route est
`kanvas.surface.render` et effectue une seule
soumission GPU (`render.draw=3`, `render.pipelineBind=2`).

L'oracle CPU est indépendant de WGSL : il calcule l'aire d'intersection de
chaque pixel, décode les couleurs sRGB, applique la couverture à la composition
`LinearPremul` (prémultipliée en lumière linéaire), puis réencode le résultat.
La preuve GPU/CPU est exacte : `0` pixel différent, différence de canal maximale
`0`, similarité `100 %`.

Les artefacts vérifiés sont dans
`reports/gpu-renderer/evidence/correctness/promoted/fractional-aa-rect-overlap/`.

## Périmètre et refus

Cette vague ne promeut pas encore une généralisation AA pour les paths, les
RRects sous transformation, les petites primitives, ni les clips fractionnaires
combinés. Ces variantes restent hors de cette preuve ; les gardes existantes
refusent notamment les plans de sample incompatibles
(`unsupported.native-core-primitive.sample-plan`) et les formats non admis
avant tout travail natif.

## Reproduction

```text
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit=fe815489a4831079d08446c5e4a14c43c189ea16 \
  -Pscene=fractional-aa-rect-overlap
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence \
  -PsourceCommit=fe815489a4831079d08446c5e4a14c43c189ea16 \
  -Pscene=fractional-aa-rect-overlap
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
