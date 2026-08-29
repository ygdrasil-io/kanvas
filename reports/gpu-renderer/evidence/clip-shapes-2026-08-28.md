# W23 — formes de clip transformées

## Route promue

`transformed-clip-rrect-solid` atteste le chemin public `Kanvas Surface` où
un `clipRRect` non-AA est capturé sous une scale non uniforme et une
translation, puis consommé après `resetMatrix()` par un `drawRect`. Le clip est
figé en coordonnées device (`(10,12)`–`(58,48)`, rayons `(6,3)`), et non
réinterprété sous la CTM ultérieure.

La classification est `analytic coverage` : ce cas ne réclame ni scissor, ni
stencil, ni texture intermédiaire. Le bundle promu
`correctness/promoted/transformed-clip-rrect-solid/` contient l'oracle CPU
indépendant, les captures CPU/GPU, le diff, les statistiques, les diagnostics,
la télémétrie de route et les hashes d'intégrité. Résultat : 64×64, 100 % de
similarité, 0 pixel différent, une soumission native `kanvas.surface.render`
(`render.draw=2`, `render.pipelineBind=2`).

## Contrats et refus

`ClipStackOp.RRectOp` conserve maintenant `transformClass` jusqu'au transport
GPU. Les diagnostics peuvent donc distinguer la géométrie effectivement
transformée (`scale-translate`) d'un RRect originellement en identité, sans
perdre la durée de vie des bounds capturés.

Le test d'inventaire public vérifie que ce même clip garde sa provenance et sa
classification analytique pour trois consommateurs : rect, RRect et path.
Les autres classifications restent explicites : un rect device entier peut
prendre le scissor ; un hard path clip prend le stencil-cover borné ; les piles
complexes nécessitant un masque passent par le budget intermédiaire ou refusent
avant allocation. Les rotations/skews de hard path clips et les CTM
singulières/non-finies conservent leurs refus stables avant soumission
(`unsupported.clip.path_transform`, `unsupported.transform.affine_singular`,
`unsupported.transform.non_finite`).

## Reproduction

```text
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit=f4ea1b51469063468cb46f57116039f38a02ade2 \
  -Pscene=transformed-clip-rrect-solid
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence \
  -PsourceCommit=f4ea1b51469063468cb46f57116039f38a02ade2 \
  -Pscene=transformed-clip-rrect-solid
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
