# W134 — SweepGradient transformé sous clip inverse EvenOdd + Difference

## Périmètre

Cette vague ajoute une preuve native d’un stroke mono-segment `SQUARE + MITER`,
anti-aliasing désactivé, avec un `SweepGradient` clamp à deux stops. Le dessin
utilise `scale(1.5) + translate(2, 1)` et le clip est un rectangle EvenOdd avec
un trou, en `INVERSE_EVEN_ODD + DIFFERENCE`.

## Preuve

- route : `native.path_stroke.stencil_cover` ;
- transform : `uniform-positive-scale-translate` ;
- producteur EvenOdd : `Invert` / `Invert` ;
- consumer : `NotEqual` (`inverseFill xor Difference`) ;
- préparation : `Recorded` ;
- exécution native : `Succeeded` ;
- un submit et une readback copy ;
- oracle CPU indépendant en coordonnées device, avec coque `outer XOR hole`,
  extension square de `1,5 px`, sweep `atan2`, interpolation linear-light puis
  encodage sRGB, tolérance d’un LSB.

## Vérification

Commande ciblée exécutée :

```text
./gradlew :kanvas:test --no-daemon --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient square miter stroke under scaled translated inverse even odd difference hole clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La matrice complète des inventaires W131–W134 est relancée avant publication de
la PR.

## Limites

La preuve reste bornée à deux stops, `clamp`, sRGB, matrice locale identité,
stroke mono-segment `square/miter`, AA désactivé et stencil 1x. Les rotations,
perspectives, matrices locales, caps round, chemins multi-segments et gradients
à plus de deux stops restent refusés explicitement.
