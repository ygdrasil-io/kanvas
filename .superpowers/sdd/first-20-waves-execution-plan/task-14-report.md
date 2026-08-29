# W28 report — bounded bitmap sampling

## État autoritatif — 2026-08-28, après Round 4

Cette synthèse datée est l'état courant. Le journal append-only qui suit
conserve les findings et corrections dans leur ordre chronologique; un fait
historique ne doit pas être lu comme une extension du contrat actuel.

Le contrat W28 prouvé par l'évidence publique est : bitmap RGBA8 préparé en
mémoire, image entière, destination entière 1:1, nearest/clamp, `SrcOver`
opaque et sans codec. Son oracle CPU indépendant est
`SurfaceSrgbBitmapNearestCpuOracle`.

`BoundedNearest1To1` est une capability distincte de `GenericNative`. Pour
Bounded, crop, scaling, coordonnées fractionnaires, affine non admise,
Linear/cubic/mipmap/anisotrope et géométrie non canonique refusent avant
soumission. Les capacités historiques de scaling, image-shader avec translation
locale fractionnaire et lattice/grid restent exclusivement dans
`GenericNative`.

Les bundles promus actuels sont les deux scènes W28, avec :

- source de génération : `ea74ef949f49ec57ac995229a272b36677fa3afe`
- commit de promotion des artefacts : `f0b630520`
- positif : `encodedScopeKinds:["Upload"]` et
  `preparedImage.frameTextureCreations`, `frameSamplerCreations`,
  `frameBindGroupCreations`, `queueWriteTextureCalls`,
  `textureUploadScope` tous à `1`
- négatif : `bounded-bitmap-linear-refusal`,
  `unsupported.image.sampling_filter`, sans soumission ni texture créée.

Round 4 (`f7b852664`) ferme aussi les identités internes : cache pipeline,
canonical hash de frame, binding, sampler et géométrie bornée ne peuvent plus
faire collision entre `GenericNative` et `BoundedNearest1To1`.

## Journal append-only chronologique

### Initial — programme, oracle et première preuve

Commit code : `1d2c1d1d718c95e8f685d89d271c1ee23015caa6`.
Commit evidence : `fd8f3e610`.

- Ajout de `bounded-rgba8-nearest-bitmap`, de l'oracle CPU littéral et du
  refus public `bounded-bitmap-linear-refusal`.
- Première preuve native : similarité 100 %, zéro pixel différent, une
  soumission et une texture; pas de copie/readback destination.
- Finding conservé : la première formulation de support était trop large et
  ne séparait pas encore correctement la route bornée du chemin générique.

### Round 1 — contrat borné et telemetry image

Commits : `60bb9939a` (contrat), `f052fc351` (compteurs), `9da01e6e3`
(evidence refresh).

- Le contrat borné a imposé image entière, destination finie/entière 1:1 et
  le refus stable `unsupported.image.rect_geometry` avant soumission.
- La preuve a distingué l'upload image des compteurs Surface génériques avec
  texture, sampler, bind group, `queue.writeTexture` et scope d'upload.
- Finding conservé : la garde affine/geometry avait encore été appliquée au
  lowerer partagé et pouvait régressser des routes historiques.

### Round 2 — séparation de capability et propagation native

Commits : `7d6f62a98` (séparation), `0202fd525` (evidence), `33fe71793`
(propagation), `eb8e4246c` (evidence upload scope).

- `RenderConfig` sélectionne explicitement `BoundedNearest1To1` pour W28;
  `GenericNative` garde scaling, translation locale fractionnaire et
  lattice/grid sans modification de leurs tests historiques.
- La capability traverse lowerer, payload semantic, binding input/request,
  resource plan, preflight, factory et materializer. Linear appartient à la
  route Generic; Bounded impose nearest et sa géométrie 1:1.
- Le scope d'upload devient un événement structurel réel produit après
  l'encodage du `TextureUpload`, et non une déduction de `queue.writeTexture`.
- Finding conservé : les vérifications de sommets et toutes les identités de
  cache/frame n'étaient pas encore complètement fermées.

### Round 3 — géométrie complète, refus et identités de ressource

Commit code : `ea74ef949f49ec57ac995229a272b36677fa3afe`.
Commit evidence : `f0b630520`.

- Bounded valide les quatre vertices dans l'ordre canonique, les UV, la
  classe `Rect`, les coordonnées entières et l'extension exacte de l'image.
  Un `v1`/`v3` skewed refuse avant materialization.
- Tests natifs : nearest accepté; Linear et capability incohérente refusés;
  factory sans sampler natif et materializer sans handles lors du refus.
- Le payload, le sampler, le resource/binding dump et le pipeline key portent
  la capability. Les bundles actuels ont été régénérés avec le source commit
  ci-dessus et promus par `f0b630520`.
- Finding conservé : le session cache canonicalisait encore la clé sans
  recopier `routeCapability`, et le canonical hash de frame omettait des
  champs de binding/sampler/géométrie.

### Round 4 — fermeture des collisions cache et frame identity

Commit code : `f7b852664`.

- `GPUWgpu4kPreparedImageSessionCache.canonicalize` conserve
  `routeCapability`; `acquireBatch` construit donc deux pipelines distincts
  pour Generic et Bounded quand tous les autres axes sont égaux.
- Le canonical hash de `GPUFramePlan` inclut
  `GPUImageBindingRequest.routeCapability`, `boundedGeometry` intégrale
  (classe, vertices raw bits, indices) et
  `GPUSamplerDescriptor.preparedImageRouteCapability`.
- Les tests ont reproduit les collisions avant correction, puis vérifié la
  non-collision cache, la différence/stabilité des hash/dump de frame et les
  suites Surface historiques/W28, resource/preflight/factory/materializer et
  catalogue/oracle.
- Aucun bundle n'a été régénéré : Round 4 est une correction d'identité/cache
  interne. `verifyPromotedGpuEvidence` a validé les artefacts promus Round 3.

## Vérifications finales consignées

```text
./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUPreparedDrawImageLowererTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceImagePixelTest \
  :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSessionCacheTest \
  --tests org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanIntegrityTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageNativeResourcesTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageNativeHandleFactoryTest \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest \
  --tests org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadTest \
  :integration-tests:gpu-evidence:test \
  --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest \
  --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```

Résultat enregistré : `BUILD SUCCESSFUL`. Aucune PR n'a été créée.
