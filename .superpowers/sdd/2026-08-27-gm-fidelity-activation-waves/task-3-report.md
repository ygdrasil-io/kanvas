# Task 3 — gradient linéaire borné à deux arrêts

## Résultat

La préparation de matériau accepte désormais uniquement un gradient linéaire
SRGB à deux arrêts, `CLAMP`, couleurs premultiplied, et matrice locale affine
finie dans les bornes 4096 (coefficients linéaires) / 16384 (translation).
La matrice est consommée dans l'ABI WGSL v2 (576 bytes), et non seulement
portée comme fait non consommé. L'interpolation WGSL mélange directement les
couleurs linéaires-premultiplied packées.

Les tile modes `repeat`, `mirror`, et `decal`, tout nombre d'arrêts autre que
deux, les valeurs non finies, la perspective, et les matrices hors budget sont
des refus stables. Les validations de matrice de la vague image sont réutilisées
par le helper commun, sans dupliquer les bornes.

## Tests et preuves

```sh
./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramTest \
  --tests org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProviderTest \
  --tests org.graphiks.kanvas.gpu.renderer.materials.LinearGradientMaterialLoweringTest \
  :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUMaterialMapperTest
```

Le test CPU reflète strictement le WGSL : point affine, branche dégénérée
`lenSq < 1e-12`, clamp, conversion sRGB→linéaire, premultiply puis mélange.
Ses couleurs intermédiaires empêchent un faux positif sur les seuls canaux 0/1.
Les tests WGSL vérifient le packing v2, la réflexion/parser et l'absence de
conversion sRGB supplémentaire; les refus ont des codes fermés.

```sh
./gradlew --no-daemon :gpu-renderer:test \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedVerticesNativeSmokeTest.two stop linear gradient v2 matches its CPU shader oracle'
```

Cette preuve native headless/offscreen passe par `GPUPreparedMaterialProgram`,
puis la task-list de prepared vertices et wgpu4k, avec un vrai pixel oracle
non trivial. Elle consomme l'ABI gradient v2 de 576 bytes (et pas l'ABI
CorePrimitive de 592 bytes) : 1 submit, 1 readback, 36 canaux comparés,
`maxChannelDelta=0`, 0 canal différent. Les artefacts route/diff/stats
enregistrent les deux buffers RGBA exacts.

```sh
./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.LinearGradientGmSurfaceRefusalEvidenceTest
```

Les artifacts sont dans
`reports/gpu-renderer/evidence/linear-gradient-2stop-2026-08-27/`.

## GMs et non-claims

Aucun GM n'est régénéré : `linear_gradient` emploie six arrêts,
`fillrect_gradient` mélange des comptes d'arrêts et des gradients radiaux, et
`gradient_matrix` comporte une moitié radiale. Un test de route Surface dédié
enregistre la commande, le diagnostic réel et le compte d'opérations :
`linear_gradient` (101 opérations) refuse par
`unsupported.material.mapping.linear_gradient_stop_count`; `fillrect_gradient`
(19) et `gradient_matrix` (18) refusent par
`unsupported.material.source_unimplemented`. Ils ne produisent donc pas de
pixels GM WebGPU, de référence/diff ou de statistiques de similarité honnêtes.
Les fichiers `route.json`, `diagnostics.json`, `stats.json` et `diff.json`
conservent explicitement ce ruling et la preuve native acceptée séparée.

Pas de Ganesh, Graphite, SkSL dynamique, `gpu-renderer-scenes`, fenêtre native,
ou baisse de seuil.
