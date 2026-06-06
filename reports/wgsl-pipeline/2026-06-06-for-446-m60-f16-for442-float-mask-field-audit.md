# FOR-446: M60 F16 audit du champ float FOR-442

## Résultat

Classification: `for442-float-mask-field-retired-as-unreliable`.

La sonde FOR-446 est strictement opt-in via
`kanvas.webgpu.m60F16For442FloatMaskFieldAuditFor446.enabled`. Elle ne change
pas le rendu par défaut, les seuils, le scoring, la politique de fallback,
`PipelineKey`, les ressources WGSL de production, ni `wgsl4k`.

Source brouillon mémoire:
`global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-auditer-le-champ-float-for-442-refute-par-for-445`

Source finding mémoire:
`global/kanvas/findings/for-445-runtime-integer-lane-mask-refutes-for-442-float-masks`

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-for442-float-mask-field-audit-for446/m60-f16-for442-float-mask-field-audit-for446.json`

## Preuves

Les six pixels CPU-exclus M60 F16 sont audités:

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

Résumé attendu:

- masque vert CPU nul: 6 / 6;
- champ float FOR-442 disponible: 2 / 6;
- masques float FOR-442 non nuls: `(92,75)=0x005C`, `(89,78)=0x0058`;
- valeur brute `f32` FOR-442 disponible: 2 / 6;
- désaccord arrondi `f32 -> Int`: 0;
- masque float FOR-442 non nul alors que le compteur voisin vaut 0: 2;
- masque entier FOR-445 disponible: 6 / 6, toujours `0x0000`;
- compteur FOR-445 disponible: 6 / 6, toujours `0`;
- masque bas niveau FOR-443 disponible: 6 / 6, toujours `0x0000`.

## Interprétation

FOR-446 écarte l’hypothèse d’un simple artefact de conversion `f32 -> Int`:
les valeurs brutes flottantes arrondissent vers les mêmes entiers que le champ
exporté par FOR-442.

Le champ `wgslSubsampleMask4x4` FOR-442 reste pourtant non nul sur deux pixels
alors que le `vec4f` voisin de couverture indique `0`, que FOR-445 écrit le
masque entier `0x0000` avec compteur `0`, et que FOR-443 calcule aussi
`0x0000`. Ce champ doit donc être retiré des preuves de couverture M60 F16.

La prochaine correction ne doit pas utiliser
`M60F16AaStencilCoverShaderReturnDiagnosticSample.wgslSubsampleMask4x4` comme
source fiable. Les tickets suivants doivent s’appuyer sur FOR-445/FOR-443 pour
le constat de masque nul.

## Validation

Commandes:

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16For442FloatMaskFieldAuditFor446.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py`
- `rtk python3 scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for446-pycache python3 -m py_compile scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py`
- `rtk git diff --check`
