# FOR-448 - M60 F16 zero-mask neutral path trace

Date: 2026-06-06

## Résumé

FOR-448 ajoute une instrumentation WebGPU opt-in pour comprendre pourquoi la
correction FOR-447, qui discard les six pixels zero-mask, ne modifie pas le
rendu M60 F16. La trace compare trois variantes expérimentales:

- `inside` via FOR-447 ;
- `outside` via `kanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.mode=outside` ;
- `both` via `kanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.mode=both`.

La propriété principale reste inactive par défaut:

`kanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.enabled`

## Résultat

Artefact:

`reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-neutral-path-trace-for448/m60-f16-zero-mask-neutral-path-trace-for448.json`

Classification:

`zero-mask-neutral-path-trace-inconclusive`

Métriques principales:

- similarité actuelle: `95.914714` ;
- similarité `inside`: `95.914714` ;
- similarité `outside`: `95.914714` ;
- similarité `both`: `95.914714` ;
- résiduel actuel: `2014` ;
- résiduel `inside`: `2014` ;
- résiduel `outside`: `2014` ;
- résiduel `both`: `2014` ;
- pixels changés `inside`: `0` ;
- pixels changés `outside`: `0` ;
- pixels changés `both`: `0` ;
- destination avant observée pour les six pixels: `6/6` ;
- destination après passage observée pour les six pixels: `6/6`.

## Interprétation

La neutralité de FOR-447 ne vient pas simplement du fait que la correction
ciblait le sous-passage `inside` au lieu de `outside`: les variantes `outside`
et `both` sont également neutres.

La trace montre aussi que les lectures destination avant et après passage sont
disponibles pour les six pixels. Malgré cela, aucun discard testé ne modifie la
sortie finale. FOR-448 refuse donc de nommer une correction candidate: il faut
élargir la prochaine trace vers la sélection exacte du fragment ou vers un
passage d’écriture non couvert par ces variantes.

Limite importante: la trace `inside/outside` de FOR-448 est une comparaison
différentielle d’images finales entre variantes, pas une observation directe de
chaque écriture fragmentaire. La trace `stencil-write` directe est également
indisponible dans cet artefact. Cette incomplétude est volontairement conservée
dans le JSON et force la classification inconclusive au lieu d’inférer une cause
non prouvée.

## Sources

- Brouillon mémoire: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-le-passage-reel-apres-correction-zero-mask-neutre-for-447`
- Finding mémoire: `global/kanvas/findings/for-447-zero-mask-opt-in-correction-is-neutral-on-m60-f16`
- FOR-447: correction opt-in zero-mask neutre.
- FOR-445 et FOR-443: sources zero-mask retenues.
- FOR-442: explicitement exclu comme source de décision de couverture.

## Garde-fous

Préservés:

- pas de changement du rendu par défaut ;
- pas de promotion de FOR-447 ;
- pas de changement des seuils ;
- pas de changement du scoring ;
- pas de changement de fallback policy ;
- pas de changement de `PipelineKey` ;
- pas de modification de `wgsl4k` ;
- pas de modification du WGSL de production ;
- pas de revendication de prise en charge complète stroke cap/join.

## Validation

Commandes exécutées:

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin
rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin
```

Commandes attendues pour le closeout:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for448_m60_f16_zero_mask_neutral_path_trace.py
rtk python3 scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for448-pycache python3 -m py_compile scripts/validate_for448_m60_f16_zero_mask_neutral_path_trace.py scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py
rtk git diff --check
```
