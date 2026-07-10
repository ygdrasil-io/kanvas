# Task 1 — Child layer scissor

## Correction

`LayerScissorOffscreenTarget` intercepte tout encodage vers le `sceneLabel`
actif. Pour une layer bornée, son `LayerScissorRenderRecorder` intersecte les
scissors de chaque draw backend avec `scenePlan.bounds`; les layers ordinaires
continuent de déléguer directement au recorder.

## TDD et vérification

- RED : le test `bounded saveLayer intersects every child raw draw scissor before encoding`
  a échoué avant l'implémentation sur `intersectLayerScissor` absent.
- GREEN : `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon`
  passe avec 15 tests.

## Auto-revue

- Le scissor est injecté à la frontière commune de l'offscreen target, sans
  condition ajoutée dans les dispatchers de primitives.
- Les bounds vides éliminent les draws par intersection vide, sans fallback CPU.

## Complément de revue P1

- Le test d'intégration `bounded child target forwards the intersected scissor
  to its backend recorder` encode une layer bornée dans un target espion et
  observe la `GPUBackendRawUniformDraw` transmise au recorder. Il attend le
  scissor intersecté `(2, 3, 3, 2)`, pas seulement le résultat du helper.
- `LayerBounds` et `LayerScissorOffscreenTarget` sont `internal` uniquement
  pour permettre cette preuve de frontière ; la logique de production est
  inchangée.
