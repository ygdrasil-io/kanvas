# W70 — audit du lifecycle des ressources GPU

## Verdict

W70 est couvert par les contrats et routes actuellement utilisés par
`gpu-renderer`. Aucun correctif de production n’est nécessaire dans cette
vague : les tests ciblés passent et ne révèlent ni réutilisation inter-device,
ni libération prématurée, ni ownership perdu. Aucun fichier
`gpu-renderer-scenes` n’a été utilisé ou modifié.

## Matrice de couverture

| Contrat | Preuve observée | Verdict |
| --- | --- | --- |
| Device generation | `GPUBackendRuntimeNativeFactoryLifetimeTest` vérifie qu’une création après `dispose` avance la génération exactement une fois ; `GPUQueueCompletionAdapterTest` refuse les tickets d’une autre génération. | `SUPPORTED` |
| Queue completion | `GPUQueueManagerTest` libère les ressources seulement après succès GPU, garde les ressources en vol, et transforme `DeviceLost` en quarantaine jusqu’au teardown. | `SUPPORTED` |
| Scratch/intermediate ownership | `GPUScratchTexturePoolTest` couvre les réservations, rollback LIFO, complétion, échec/device loss, invalidation de génération et absence de réutilisation avant complétion. | `SUPPORTED` |
| Upload/readback ownership | `GPUConcreteResourceProviderTest` couvre budget partagé scratch/readback, callbacks différés, map/unmap, refus et quarantaine ; `GPUQueueCompletionAdapterTest` couvre la terminalisation unique. | `SUPPORTED` |
| Dispose/close | `GPUBackendRuntimePreparedImageCacheLifecycleTest`, `GPUPreparedSurfaceCompositeLeaseLifecycleTest` et `GPURuntimeResourceAdapterTest` couvrent teardown ordonné, retry des close incomplets, ownership borrowed/owned et idempotence. | `SUPPORTED` |
| Use-after-free / partial creation | Les refus de réservation, completion inconnue/dupliquée et close partiellement échoué conservent les références nécessaires ou refusent avant utilisation ; aucun handle natif n’est exposé dans les contrats. | `SUPPORTED` |

## Limites explicites

La perte réelle d’un adapter WebGPU n’est pas simulée par une fenêtre native
dans cette preuve ; elle est représentée par le signal contractuel
`GPUQueueCompletionFailureKind.DeviceLost` et par l’invalidation des pools
avant une génération courante. La reprise/recréation matérielle dépend du
runtime `wgpu4k` livré et reste hors de cette exécution headless. Il n’y a pas
de fallback CPU caché, ni de support de windowing natif ajouté.

## Vérification

Commande exécutée avec succès :

```text
rtk ./gradlew --no-daemon :gpu-renderer:test \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueManagerTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUScratchTexturePoolTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactoryLifetimeTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimePreparedImageCacheLifecycleTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceCompositeLeaseLifecycleTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWindowFrameLifecycleTest' \
  --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionAdapterTest'
```

Résultat : 9 classes, 168 tests, 0 échec, 0 erreur, 0 test ignoré.

