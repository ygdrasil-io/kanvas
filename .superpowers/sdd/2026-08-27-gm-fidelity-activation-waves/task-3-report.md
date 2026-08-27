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

Le test CPU calcule le point transformé, le clamp et le mélange premultiplied;
les tests WGSL vérifient le packing v2, la réflexion/parser et l'absence de
conversion sRGB supplémentaire; les refus ont des codes fermés.

Les artifacts sont dans
`reports/gpu-renderer/evidence/linear-gradient-2stop-2026-08-27/`.

## GMs et non-claims

Aucun GM n'est régénéré : `linear_gradient` emploie six arrêts,
`fillrect_gradient` mélange des comptes d'arrêts et des gradients radiaux, et
`gradient_matrix` comporte une moitié radiale. Les trois exécutions ciblées
`SkiaGmRunner` terminent au product-surface existant avec
`unsupported.material.source_unimplemented`; elles ne produisent donc pas de
pixels WebGPU, de référence/diff ou de statistiques de similarité honnêtes.
Les fichiers `route.json`, `diagnostics.json`, `stats.json` et `diff.json`
conservent explicitement ce ruling.

Pas de Ganesh, Graphite, SkSL dynamique, `gpu-renderer-scenes`, fenêtre native,
ou baisse de seuil.
