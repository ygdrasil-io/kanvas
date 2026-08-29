# W138 — Radial clamp 2-stop, square + miter stroke, right-angle winding clip

## Périmètre

- preuve planner et preuve native pour un `DrawPath` stroke mono-segment `square/miter`,
  anti-aliased désactivé,
  `clamp` radial à 2 stops,
  `Clip` `Winding` dur,
  et `CTM` rotation exacte 90°;
- refus général de rotation maintenu (pipeline planner).

## Résultat des preuves

- route planner: `native.path_stroke.stencil_cover` pour
  `exact right angle radial draw with square miter stroke under winding clip reaches the hard path clip route`;
- route native + exécution: `native.path_stroke.stencil_cover` pour
  `clamp radial gradient right angle square miter stroke under winding clip renders natively`;
- préparation `Recorded` ; exécution `Succeeded`;
- `readback` conforme au CPU oracle via `assertRgbaWithinOneLsb` (tolérance 1 LSB par canal).

## Vérification ciblée exécutée

Commande exécutée:

```text
rtk ./gradlew :kanvas:test --no-daemon --no-parallel \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest.exact right angle radial draw with square miter stroke under winding clip reaches the hard path clip route' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp radial gradient right angle square miter stroke under winding clip renders natively'
```

Résultat: `BUILD SUCCESSFUL`.

## Notes

- le refus non-cœur `general radial draw rotation remains refused before native preparation` reste inchangé.
- aucune production non testée n’a été modifiée.
