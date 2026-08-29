# W132 — SweepGradient clamp + stroke square/miter sous clip EvenOdd inverse avec `DIFFERENCE`

## Résultat

La combinaison est validée nativement sur le lane `StencilCoverage` borné. Elle
réutilise l’admission `INVERSE_EVEN_ODD + DIFFERENCE` corrigée en W131.

## Fixture et oracle CPU

- Cible offscreen : `32x32`.
- Stroke device : `(5.25,8.25) -> (21.25,20.25)`, largeur `4`, cap `SQUARE`,
  join `MITER`, anti-aliasing désactivé.
- Clip `INVERSE_EVEN_ODD` : rectangle extérieur
  `(3.25,3.25)-(28.75,28.75)` et trou intérieur
  `(10.25,10.25)-(21.75,21.75)`, `ClipOp.DIFFERENCE`,
  anti-aliasing désactivé.
- SweepGradient centré en `(16,16)`, angles `0..360`, deux stops rouge →
  bleu, interpolation linear-light puis encodage sRGB, mode `clamp`.

L’oracle CPU indépendant conserve la coque EvenOdd (`inOuter XOR inInner`),
car le clip inverse couvre le complément et `DIFFERENCE` retire ce
complément. Il applique ensuite l’extension square de `2 px` aux extrémités,
le test de distance au segment et l’évaluation `atan2` du SweepGradient avec
interpolation linear-light avant encodage sRGB. La comparaison porte sur le
buffer RGBA complet avec une tolérance maximale de 1 LSB par canal pour la
différence CPU/GPU en `f32`.

## Preuve native

Le test
`clamp sweep gradient square miter stroke under inverse even odd difference hole clip renders natively`
vérifie :

- route `native.path_stroke.stencil_cover` ;
- plan `StencilCoverage` ;
- fill rule `EvenOdd`, `inverseFill=true` ;
- opérations producteur `Invert` / `Invert` ;
- comparaison consommateur `NotEqual` (`inverseFill XOR Difference`) ;
- préparation `Recorded` ;
- résultat `Succeeded` ;
- exactement un submit et une copie de readback ;
- résultat RGBA conforme à l’oracle CPU à 1 LSB près.

## Vérification

Commande ciblée exécutée :

```text
./gradlew --no-daemon :kanvas:test --no-parallel --rerun-tasks \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp sweep gradient square miter stroke under inverse even odd difference hole clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test passé sans mismatch pixel.

## Limites

La preuve couvre uniquement le lane exact : deux stops, `clamp`, interpolation
linear-light puis encodage sRGB, matrice locale identité, transformation de
dessin identité, stroke mono-segment direct `square/miter`, anti-aliasing
désactivé, clip rectangulaire avec trou `INVERSE_EVEN_ODD`, clip `DIFFERENCE`
et stencil 1x. Les caps `round`, chemins
multi-segments, matrices locales non identités et gradients à plus de deux
stops restent hors contrat et doivent conserver une politique de refus
explicite.
