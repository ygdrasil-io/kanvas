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
