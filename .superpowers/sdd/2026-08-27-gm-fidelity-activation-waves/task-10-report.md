# Task 10 — Texte/glyphes à police livrée (2026-08-27)

## Résultat

La police réellement livrée `LiberationSans-Regular.ttf` est exploitable :
`kanvas/src/test/resources/fonts/liberation/LiberationSans-Regular.ttf`
(410&nbsp;712 octets, SHA-256
`76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8`).
Le chemin GPU utilisé est intégral et ne comporte aucun substitut :
`Font -> TextBlob -> KanvasGlyphRun -> FontTypeface.preparedTextOutline ->
TextA8 -> WebGPU headless/offscreen ReadbackRgba`. `getGlyphPath` reste
strictement l’oracle CPU d’outline du test.

`GPUDeliveredFontGlyphRunEvidenceTest` borne trois rows à cette unique police :

- `gradtext.glyph-run.linear-clamp.v1` : `Skia`, 24 px, gradient 2 stops,
  `CLAMP` ; quatre glyphes A8.
- `text-scale-skew.glyph-run.affine.v1` : `Skia`, 24 px, affine
  `sx=1.15,kx=0.18` ; quatre glyphes A8 et oracle CPU opaque
  `[255,255,255,255]`.
- `fontscaler.glyph-run.size-18.v1` : `Aa`, 18 px ; deux glyphes A8 et le
  même oracle CPU.

Chaque row crée une soumission et une copie de readback, sans refus GPU. Les
preuves JSON sont dans
`reports/gpu-renderer/evidence/delivered-font-glyph-run-2026-08-27/`.

## GMs non promus

- `gradtext` reste `expected-unsupported` :
  `unsupported.material.mapping.linear_gradient_tile_mode` pour son tile mode
  `MIRROR`; le row actif est volontairement le sous-ensemble `CLAMP`.
- `text_scale_skew` reste `expected-nonpromotion` : 16 dispatches, zéro refus,
  mais 77,75 % sous le seuil existant de 80,0 %.
- `fontscaler` reste `expected-unsupported` :
  `invalid.surface.prepared.text-command`; le sweep large n’est pas admis par
  le prepared-text, et seul le run 18 px est prouvé.

Ces refus sont sérialisés dans `refusals.json`; aucun GM, seuil ou budget n’a
été modifié.

## Vérification reproductible

```sh
rtk ./gradlew --no-daemon :kanvas:test --rerun-tasks \
  --tests org.graphiks.kanvas.surface.gpu.GPUDeliveredFontGlyphRunEvidenceTest

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=gradtext -Dkanvas.render.debugLevel=PIXEL

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=text_scale_skew -Dkanvas.render.debugLevel=PIXEL

rtk ./gradlew --no-daemon :integration-tests:skia:test --rerun-tasks \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=fontscaler -Dkanvas.render.debugLevel=PIXEL
```

La première commande doit réussir. Les trois suivantes échouent
intentionnellement avec les états indiqués dans `refusals.json` : ce sont des
preuves de refus/non-promotion, pas des tests verts.

## Concerns

- Pas de revendication générale de police, shaping, fallback, RTL/BiDi,
  ligatures, emoji/couleur, SDF/LCD, hinting, ni de similarity GM globale.
- La voie est WebGPU headless/offscreen uniquement ; aucune dépendance de
  fenêtrage natif, Ganesh, Graphite ou SkSL dynamique n’est introduite.
