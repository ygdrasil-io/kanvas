# FOR-443 - M60 F16 sonde bas niveau du masque WebGPU exact

Date: 2026-06-06

## Verdict

FOR-443 ajoute une sonde WebGPU bas niveau strictement opt-in via
`kanvas.webgpu.m60F16LowLevelExactMaskProbeFor443.enabled`.

La sonde utilise un `compute shader` (shader de calcul) diagnostique genere en
memoire par `SkWebGpuDevice`. Elle lit le meme uniform d'aretes que
`StencilCoverAaPolygonDraw`, evalue `winding_at`, `sample_covered` et le masque
4x4 pour les six coordonnees M60 F16, puis ecrit un tampon de stockage relu par
le test. Elle ne modifie pas `aa_stencil_cover.wgsl`, le rendu par defaut, les
seuils, le scoring, la fallback policy, `PipelineKey`, FOR-431 ou `wgsl4k`.

Classification obtenue: `webgpu-low-level-mask-unresolved`.

## Resultat

Les six masques bas niveau sont disponibles et valent tous `0x0000`:

| Pixel | CPU vert | Masque WebGPU bas niveau | FOR-442 runtime |
|---|---:|---:|---:|
| `(92,75)` | `0x0000` | `0x0000` | `0x005C` |
| `(91,76)` | `0x0000` | `0x0000` | indisponible |
| `(90,77)` | `0x0000` | `0x0000` | indisponible |
| `(89,78)` | `0x0000` | `0x0000` | `0x0058` |
| `(88,79)` | `0x0000` | `0x0000` | indisponible |
| `(87,80)` | `0x0000` | `0x0000` | indisponible |

Conclusion simple: le predicat bas niveau `sample_covered` ne surcouvre pas ces
six pixels. Les deux valeurs partielles observees par FOR-442 ne sont donc pas
reproduites par l'evaluation directe du masque 4x4.

## Artefacts

- Artefact JSON:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-low-level-exact-mask-probe-for443/m60-f16-webgpu-low-level-exact-mask-probe-for443.json`
- Validateur:
  `scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py`
- Source FOR-442:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-runtime-exact-mask-probe-for442/m60-f16-webgpu-runtime-exact-mask-probe-for442.json`
- Finding source:
  `global/kanvas/findings/for-442-web-gpu-runtime-exact-mask-probe-unavailable-for-complete-m60-f16-six-pixel-set`

## Risque restant

La divergence utile se deplace: elle n'est plus localisee dans le predicat bas
niveau `sample_covered`. La prochaine piste est d'auditer le stockage
shader-return FOR-442 et le chemin de couverture effectif du fragment, car le
champ runtime partiel ne correspond pas au masque exact calcule par la sonde
bas niveau.

## Validation

Commandes attendues:

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16LowLevelExactMaskProbeFor443.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py
rtk python3 scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py
rtk python3 scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for443-pycache python3 -m py_compile scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py
rtk git diff --check
```
