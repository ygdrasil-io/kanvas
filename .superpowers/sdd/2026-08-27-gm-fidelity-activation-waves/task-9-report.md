# Task 9 — Color filters et espace sRGB explicite (2026-08-27)

## Résultat

Le sous-ensemble activé est `srgb-colorfilter-matrix-v1`, pas un décodage
d'image ni une promotion GM large. `SrgbMatrixColorFilterDescriptor` fixe une
matrice 4×5 finie et le packing natif de 96 octets. Il est l'autorité unique
pour `ColorFilterParams`/`GPUFilterOracle` et le fixture natif borné via
`packNativeUniform`. L'oracle Kotlin applique explicitement `encoded sRGB ->
linear sRGB -> matrix -> encoded sRGB -> premul`. Le shader `color-matrix-v1`
suit la même séquence et est parser-validé par `wgsl4k` ; la réflexion fige
`ColorMatrixUniforms` à 96 octets (six `vec4f`, offsets 0..80).

La route WebGPU est `GPURegisteredUniformRectFrameRecorder` vers le
materializer `GPUWgpu4kRegisteredUniformRectFramePayloadMaterializer`, sur une
cible headless/offscreen 4×4 `rgba8unorm`, sans codec ni lecture de destination.
Pour la couleur droite encodée `(0.5, 0.25, 0.75, 0.5)` et une matrice qui
multiplie R par 0.5 en linéaire, l'oracle calculé et le readback GPU sont
`[46,32,96,128]` sur les 16 pixels : 64 canaux, `differentChannels=0`,
`maxDelta=0`, `meanDelta=0.0`, un submit et une copie de readback. Les deux
flux ont SHA-256
`67abf2afb66ca583f2f898cc06fa462823dec3b77486d06545a15b381372301a`.

## Refus GM conservés

- `colorfilterimagefilter` :
  `53:unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted`.
- `srgb_colorfilter` : `7:unsupported.image.native_binding`.

Ces GMs ne sont pas reclassifiés ; le premier reste bloqué avant le filter par
ScalarAA et le second par son binding image. Les commandes et messages exacts
sont sérialisés dans
`reports/gpu-renderer/evidence/srgb-colorfilter-matrix-2026-08-27/refusals.json`.

## Vérifications

```sh
rtk ./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSrgbMatrixColorFilterSmokeTest \
  --tests org.graphiks.kanvas.gpu.renderer.filters.SrgbMatrixColorFilterTest \
  --tests org.graphiks.kanvas.gpu.renderer.wgsl.ColorMatrixWgslTest \
  --tests org.graphiks.kanvas.gpu.renderer.filters.GPUFilterOracleTest

rtk ./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=colorfilterimagefilter -Dkanvas.render.debugLevel=PIXEL

rtk ./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=srgb_colorfilter -Dkanvas.render.debugLevel=PIXEL
```

Le premier groupe est PASS. Les deux commandes GM échouent intentionnellement
avec les refus exacts ci-dessus : elles sont des preuves de refus, pas des
tests verts.

## Concerns

- Ce slice ne prend pas en charge les images, les codecs, les chaînes de
  `ColorFilter`, les matrices non finies, les profils ICC/wide-gamut, HDR ou
  les transformations colorimétriques générales.
- Aucun seuil ou budget global n'a été modifié ; la tolérance locale ≤1 était
  autorisée pour la quantification mais le readback courant est byte-exact.
- Aucun Ganesh, Graphite, SkSL dynamique ni `gpu-renderer-scenes` n'est
  utilisé. La preuve headless ne promeut pas l'adaptateur de scène historique,
  qui est explicitement hors du slice Task 9 et sans revendication de support.
