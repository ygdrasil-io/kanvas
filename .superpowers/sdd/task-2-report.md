# Task 2 — Isolation native des `saveLayer` ordinaires

## RED confirmé avant production

```text
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon
```

Résultat initial : `2 tests completed, 2 failed`.

- La composition du rectangle `SRC` dans une couche remplaçait le parent au lieu de le composer.
- Le scénario imbriqué laissait un `DST_OUT` enfant modifier le contenu du parent.

```text
rtk ./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesRegressionTest --no-daemon
```

Résultat initial : 1 échec ; la sonde historique du GM observait le fond brut `210,184,135` au lieu de la composition attendue.

Un contrat complémentaire pour les `saveLayer` bornés a également été observé rouge : aucune fatalité n'était alors émise, car la variante était aplatie silencieusement.

## Implémentation

- `GPURenderer.renderViaGpu` conserve maintenant une pile de frames `label + hasContent`.
- `BeginLayer` sans bounds, backdrop ni paint non-trivial alloue une texture transparente distincte.
- `EndLayer` compose la texture enfant dans son parent par `COPY_WGSL` avec `SRC_OVER`, puis restaure le frame parent ; l'imbrication est couverte.
- Tous les helpers existants et `renderAdvancedBlend` continuent de router vers le label de scène actif ; la lecture finale s'effectue après retour à la racine.
- Les variants `backdrop`, `bounds` non nuls et `paint` non-trivial sont supprimés du rendu avec diagnostics stables. Les stacks mal équilibrées ont également un diagnostic explicite.
- Le blend shader produit déjà le résultat complet source/destination : sa target est donc clear transparent avant écriture, ce qui évite un second `SRC_OVER` implicite sur l'ancienne scène.

## GREEN

```text
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon
```

Résultat : 5/5 verts, couvrant composition `SRC`, couche imbriquée, `DrawColor(..., SRC)`, stabilité du fond hors source avancée et refus borné.

```text
rtk ./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesPortParityTest --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesRegressionTest --tests org.graphiks.kanvas.skia.GmCanvasCompatibilityTest --no-daemon
```

Résultat : 8/8 verts.

## Limites assumées

- Seuls `saveLayer(null, null)` et `saveLayer(null, Paint())` sont supportés.
- Backdrop, image filter, paint de composite non-trivial et bounds non nuls restent explicitement refusés ; aucun fallback CPU, référence, seuil ou score n'a été modifié.
- Les différences de rendu des paths et des blend modes avancés de `aaxfermodes` restent hors de cette tâche P0-A et seront traitées séparément.
