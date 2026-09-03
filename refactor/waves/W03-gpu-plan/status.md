# État W03 — `gpu-plan` et première tranche compositionnelle

Révision vérifiée : `d04a1515abb9b9b2022957b20ebf011ebf69f224` (`feat: route W3 frames through gpu plan`).

## Capability publiée

`solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1` est branchée de bout
en bout pour les rectangles solides pixel-aligned, `DeviceRect` simple,
`DrawColor(SRC_OVER)` et les couleurs opaques ou translucides en sRGB. Une
frame promue passe par `Surface -> SceneSnapshot -> RenderGraph ->
GpuRenderBackend -> readback`; le `RenderGraph` ferme les décisions de
coverage, sample, blend et load/store. Une frame non promue reste une frame
legacy entière; aucun fallback legacy n'est autorisé après `Ready`.

La cible physique W3 reste `rgba8unorm-srgb`. Le boundary Surface restitue
RGBA directement et applique le swizzle déterministe vers BGRA après
readback.

## Preuves W3

`GPUPlanSurfacePixelTest` compare exactement, par l'API publique `Surface`,
les bytes produits à `W3SolidRectCpuOracle`. L'oracle ne dépend ni de
`:gpu-plan` ni des payloads GPU : il décode `ColorARGB` sRGB, prémultiplie,
compose `SrcOver` en Float linear-premultiplied, puis modélise le store
`rgba8unorm-srgb` (encode, quantification 8 bits et decode) entre chaque
draw. Les sept preuves couvrent le recouvrement opaque, le blend translucide,
le clip `DeviceRect`, `DrawColor(SRC_OVER)`, les formats RGBA/BGRA, la
quantification d'attachement observable par le draw suivant et une frame
`SRC` manifestement hors W3 conservant son résultat legacy exact. Chaque
fixture W3 vérifie aussi, par `RenderResult` public, les scope kinds natifs
exactement `{Render, Readback}`; la fixture legacy ne leur impose aucune
forme.

| Commande | Résultat |
| --- | --- |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsTest :math:matrix:jvmTest :math:matrix:jsTest :math:color:jvmTest :math:color:jsTest` | Succès. |
| `rtk ./gradlew :render-ir:test :gpu-plan:test` | Succès. |
| `rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUCorePrimitivePreparedFrameTaskListBuilderTest*'` | Succès. |
| `rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*'` | Les preuves W3 passent; 45 échecs legacy connus, 0 erreur. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | Baseline préservée : 3 610 tests, 51 échecs, 0 erreur. |

## Baseline globale

La baseline W0–W2 est de 3 585 tests, 51 échecs et 0 erreur. Le run frais
ajoute 25 tests (dont les sept preuves W3) et les sept nouveaux tests W3 sont
verts. Les 51 noms rouges correspondent exactement à cette baseline :

- `ImageTest :: ColorType enum values`;
- les 45 cas `GPUAllApiBlendSurfaceTest :: DrawPoint` des 15 blends avancés,
  chacun sous `UNCLIPPED`, `SCISSOR` et `ALPHA_MASK`;
- `GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase`;
- les deux refus de `GPUPreparedSurfaceFrameBuilderTest`;
- `GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry and verb count seals every contour`;
- `GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non identity matrix facts before dispatch`.

Les deux smoke tests prepared qui avaient accidentellement formé des frames W3
admissibles sont désormais explicitement legacy : le test RGBA préfixe un
`Clear(Transparent)`, et le test BGRA un `Clear(Black)` opaque pixel-inert
avant son rectangle rouge couvrant toute la cible. Les routes prepared, les
pixels et l'evidence native sont réaffirmés sans modifier la production.

## Limites W4+

W4+ doit étendre la géométrie et coverage, puis les materials, layers et
effets, avant toute convergence GM. Les `font` et `codec` restent hors
périmètre. `jpg-color-cube` demeure en quarantaine et n'a pas été exécutée.
