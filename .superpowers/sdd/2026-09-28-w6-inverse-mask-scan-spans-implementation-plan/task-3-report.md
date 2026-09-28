# Task 3 — W6 inverse scan spans

## Résultat

`DONE_WITH_CONCERNS` avant revue Sol : les cinq témoins Surface publics sont verts dans
leur XML JUnit. Le runner natif macOS termine ensuite chaque invocation `:kanvas:test`
avec l'exit Gradle 133 ; ce résultat post-JUnit est conservé séparément et ne masque aucun
échec XML.

## Causalité et implémentation

- Le rebase transmet le même `PathFillScanSpansI32` device de W4e vers le proxy W6 ; les
  seuls scissors de cible sont recalculés par `localScissorsI32OrNull(origin, extent)`.
  `W4eNativePayloadPlan`, le layout physique, le proxy W6, l'autorité préparée et le
  préflight W6 comparent origine, domaine et ordre de scissors avant allocation native.
  Le triangle `F32` reste seulement la preuve de sélection ; aucun calcul de géométrie
  n'a été déplacé hors de `:math`.
- Le producer NonEmpty utilise un pipeline fullscreen sans bind group ni V/I : D24S8 est
  clear à zéro, puis chaque span scellé émet `SetScissor(left, top, width, 1)` suivi de
  `Draw(3)`. Aucun chemin scan-span n'émet `DrawIndexed` ou une slice V/I.
- Empty conserve un packet et un semantic `PathStencilProducer`, mais le render pass ne
  contient aucune commande de draw ni pipeline : clear/store D24S8 seulement. Les operand
  keys n'annoncent alors ni pipeline, ni V/I.
- Un seul semantic est retenu par `GPUDrawPacket`. L'evidence associe les N draws du
  producer NonEmpty au même commandId ; Empty a zéro evidence/draw. Le preflight refuse
  tout semantic de producer qui ne correspondrait pas à un packet W4e scan-span exact.
- Le cover reste distinct : après le producer, il réémet son `coverScissor` avant son
  fullscreen draw ; il conserve son binding mask/uniform et le test stencil zéro.

## Correction causale B_frame

Le premier positif `B_frame` refusait à `15_715_792` octets. L'inventaire exact a montré
une lease supplémentaire de 16 octets : `PlanResourceRole.SourceUniformData`, créée par
`FrameSourceLayoutV4.actualLegacy` pour la couleur solide legacy. Elle est distincte de
l'uniform de restore W6 (16 octets). Les deux fixtures publiques ont donc été corrigées :

- `B_frame = 15_715_808` accepte le rendu et ses pixels ;
- `B_frame - 1` refuse encore avec `w6a.layer.frame_budget_exceeded` avant readback.

`B_lane` n'a pas été contourné : il demeure le témoin d'admission W4e et peut être refusé
ultérieurement par l'agrégat W6a.

## Vérification

Compiles, tous `BUILD SUCCESSFUL` / exit 0 :

- `:math:geometry:compileKotlinJvm`
- `:gpu-plan:compileKotlin`
- `:gpu-renderer:compileKotlin`
- `:kanvas:compileTestKotlin`

Sélecteurs exécutés séparément, chacun avec XML `tests=1, failures=0, errors=0, skipped=0` :

- `W6aLayerW4W5SurfacePixelTest.inverse winding direct fill remains clipped by a hard concave path in a W6 layer`
- `W6InverseScanSpanSurfacePixelTest.inverse scan spans rebase once in a translated W6 layer`
- `W6InverseScanSpanSurfacePixelTest.subpixel inverse triangle keeps a clear only W6 producer`
- `W6InverseScanSpanSurfacePixelTest.inverse scan span fullscreen producer covers 700 corners`
- `W6InverseScanSpanSurfacePixelTest.inverse scan span frame budget at B keeps its W6 pixels`

Vérification complémentaire : `inverse scan span aggregate budget refuses B minus one before
readback` est XML vert avec le diagnostic W6a attendu.

Les six invocations `:kanvas:test` ci-dessus finissent toutefois en `BUILD FAILED` après
l'écriture XML, car `Gradle Test Executor 1` termine avec l'exit natif 133. C'est un risque
d'intégration existant du runner macOS, à conserver séparé des résultats JUnit.

`git diff --check` est propre. L'auto-revue authority → seal → evidence n'a relevé ni
géométrie nouvelle hors `:math`, ni V/I ou `DrawIndexed` pour le producer, ni paquet
supplémentaire, ni perte du scissor du cover.

## Fichiers

Le commit Task 3 inclut les propagations W4e/W6, les validations et le renderer natif,
ainsi que les deux témoins Surface. `W6aLayerW4W5SurfacePixelTest.kt`, qui était déjà RED
dans le worktree, est volontairement inclus comme demandé. Aucun fichier Task4, spec, plan
ou status n'est modifié.
