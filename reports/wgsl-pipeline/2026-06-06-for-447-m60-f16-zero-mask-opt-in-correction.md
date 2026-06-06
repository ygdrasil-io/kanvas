# FOR-447 M60 F16 zero-mask opt-in correction

Date: 2026-06-06

## But

FOR-447 teste une correction WebGPU opt-in et bornee sur les six pixels M60 F16
ou FOR-445 et FOR-443 prouvent un masque 4x4 nul. La correction ne peut pas
utiliser le champ float FOR-442 comme source de decision, car FOR-446 l'a retire
des preuves de couverture.

## Source memoire

- Brouillon ticket:
  `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-appliquer-une-correction-opt-in-basee-sur-les-masques-zero-for-445-for-443`
- Finding source:
  `global/kanvas/findings/for-446-le-champ-float-for-442-est-retire-des-preuves-de-couverture`

## Implementation

- Drapeau opt-in:
  `kanvas.webgpu.m60F16ZeroMaskCorrectionFor447.enabled`
- Chemin runtime:
  `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
- Preuve:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/m60-f16-zero-mask-opt-in-correction-for447.json`

La variante est generee en memoire a partir du shader AA stencil-cover existant.
Le fichier WGSL de production `gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl`
reste inchange.

## Classification attendue

L'artefact classe le resultat dans l'une de ces categories:

- `zero-mask-opt-in-correction-improves-scene`
- `zero-mask-opt-in-correction-regresses-scene`
- `zero-mask-opt-in-correction-neutral`
- `zero-mask-opt-in-correction-inconclusive`

La classification est calculee depuis le residuel de la scene complete M60 F16,
le nombre de pixels changes, et le nombre de changements hors des six pixels
cibles.

## Resultat genere

- Classification: `zero-mask-opt-in-correction-neutral`
- Similarite actuelle: `95.914714`
- Similarite opt-in: `95.914714`
- Residuel actuel: `2014`
- Residuel opt-in: `2014`
- Delta residuel opt-in moins actuel: `0`
- Pixels changes: `0`
- Pixels cibles changes: `0`
- Pixels hors cible changes: `0`

Conclusion: l'essai ne degrade pas la scene, mais il ne l'ameliore pas non
plus. Le discard FOR-447 ne touche pas le chemin qui produit les six residuels
M60 F16 encore observes.

## Sources de preuve

- FOR-445: masque runtime integer `0x0000` et covered count `0`.
- FOR-443: masque low-level `0x0000`.
- FOR-442: exclu comme source de decision.

## Autres scenes

Les autres scenes ne sont pas mesurees dans ce ticket. FOR-447 compare seulement
la scene complete M60 F16 bornee et signale `changedOutsideTargetPixels` pour
indiquer si la correction modifie des pixels non cibles dans cette scene.

## Non-objectifs conserves

- Pas d'activation par defaut.
- Pas de changement de seuil ou de score.
- Pas de changement de politique de fallback.
- Pas de changement de `PipelineKey`.
- Pas de modification de `wgsl4k`.
- Pas de declaration de support complet.

## Validation

Commandes attendues:

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ZeroMaskCorrectionFor447.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py
rtk python3 scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py
rtk python3 scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for447-pycache python3 -m py_compile scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py
rtk git diff --check
```
