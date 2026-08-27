# Pipeline-key fix — analytic shape AA (2026-08-27)

## Cause

La vague AA a changé les deux sources WGSL analytic-shape : le programme
direct `CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_WGSL` et le générateur
destination-read `corePrimitiveAnalyticShapeDstReadNativeWgsl`. Les deux
gardèrent pourtant leur `shaderIdentity` `v1`.

Cette identité est la clé structurale effective : elle alimente à la fois le
`sourceId` de validation/réflexion WGSL, le
`GPUWgpu4kCorePrimitiveComponentIdentity`, la clé du session cache et le label
du `GPUShaderModule`. Une source modifiée pouvait donc réutiliser une clé qui
nommait historiquement un autre programme.

## Correction

Les deux seules identités de source modifiées sont passées à `v2` :

- `core-primitive-analytic-shape-device-geometry-wgsl-v2` ;
- `core-primitive-analytic-shape-dst-read-device-geometry-wgsl-v2:<blend-mode>`.

La disposition des bindings, le layout uniforme80 et les programmes fermés
`AnalyticShapeSrcOver` / `AnalyticShapeDstRead` ne changent pas. Le test de clé
vérifie les deux identités, leur non-égalité, et leur association aux mêmes
programmes existants.

## Inventaire

L’inventaire observé est 46, non 42. Ce total était déjà atteint au commit
`75eee84d1`. Les quatre entrées au-delà de 42 ne proviennent pas de l’AA : les
deux consommateurs analytic RRect viennent de `0413ed435`, puis les deux
consommateurs analytic DRRect de `30d57c790`. Les variants Repeat
(`116ce6ec35`) leur sont antérieurs et n’expliquent pas cette variation. La
vague AA n’ajoute aucun `GPUWgpu4kCorePrimitivePipelineProgram`; les assertions
et le commentaire de capacité reflètent maintenant le total fermé réel de 46.

## Vérification

```text
rtk ./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitivePipelineDescriptorTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest

rtk ./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest
```

Les deux commandes passent. Aucun fichier de `gpu-renderer-scenes` n’est
modifié.
