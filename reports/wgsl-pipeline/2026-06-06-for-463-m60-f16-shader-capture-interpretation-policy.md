# FOR-463 - M60 F16 shader capture interpretation policy

## Résultat

Classification : `shader-capture-before-reject-confirmed-by-color-attachment`

La preuve FOR-463 consolide FOR-455, FOR-458 et FOR-459. Elle formalise la règle
suivante : une émission shader `inside` observée sur un pixel où le stencil vaut
zéro n'est pas, seule, une preuve d'acceptation couleur. Pour affirmer une
acceptation couleur après rejet stencil attendu, un ticket futur devra montrer
une cible couleur modifiée.

## Artefact

`reports/wgsl-pipeline/scenes/artifacts/m60-f16-shader-capture-interpretation-policy-for463/m60-f16-shader-capture-interpretation-policy-for463.json`

## Preuves consolidées

- FOR-455 observe l'émission shader `inside` sur les pixels stencil zéro.
- FOR-458 confirme `insideShaderEmissionOnDiagnosticZeroStencilCount=6` avec un
  état `fixed-function` (fonction fixe) qui devrait rejeter ces pixels.
- FOR-459 confirme `colorAttachmentChangedTargetCount=0` et
  `colorAttachmentUnchangedTargetCount=6` pour le cover `inside` seul.
- `for442DecisionSourceUsedCount=0`.

## Interprétation

L'émission shader est interprétée comme une capture avant rejet, pas comme une
écriture effective dans la cible couleur. FOR-459 est la preuve décisive pour
cette règle, car la cible couleur diagnostique reste inchangée sur les six
pixels concernés.

FOR-463 ne corrige pas M60 F16, ne modifie pas le rendu par défaut, les seuils,
le score, la politique de fallback (repli), `PipelineKey`, le WGSL de production
ni `wgsl4k`. FOR-442 reste exclu comme source de décision et FOR-447 n'est pas promu.

## Suite unique

Appliquer cette politique aux prochains tickets M60 F16 : ne plus ouvrir de
ticket de correction sur la seule base d'une émission shader sans preuve couleur
contradictoire.

## Validation attendue

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ShaderCaptureInterpretationPolicyFor463.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for463_m60_f16_shader_capture_interpretation_policy.py
rtk python3 scripts/validate_for459_m60_f16_production_cover_color_attachment_acceptance.py
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for463-pycache python3 -m py_compile scripts/validate_for463_m60_f16_shader_capture_interpretation_policy.py scripts/validate_for459_m60_f16_production_cover_color_attachment_acceptance.py
rtk git diff --check
```
