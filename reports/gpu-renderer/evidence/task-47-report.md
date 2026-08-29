# W71 — Cache / determinism (task 47)

## Verdict

**PASS — contrat de cache central durci ; preuve GPU/pixel non revendiquée.**

La route réellement utilisée est `GPUExecutionObjectCache`, consommée par les
caches module, bind-group-layout, pipeline-layout et render-pipeline de
`WgpuExecutionCaches`. La clé interne est maintenant la combinaison complète
`domain + keyHash + subjectHash + deviceGeneration + capabilityFingerprint`.
Une entrée issue d’une autre génération de device ou d’une autre capability
identity ne peut donc pas être réutilisée.

## Changements

- Ajout de `capabilityFingerprint` dans `GPUExecutionCacheRequest` et dans la
  clé privée ; le runtime WGPU utilise `wgpu-execution-capabilities-v1`.
- Ajout d’un budget borné (`maxEntries = 256` par défaut) et d’une éviction
  LRU déterministe (`LinkedHashMap`), avec disposal avant remplacement et
  événement `Evict` stable.
- Les hits déplacent l’entrée en fin d’ordre LRU ; les dumps et handles restent
  exempts de toute identité backend.
- Tests de contrat pour l’isolation capability et l’éviction LRU ; test ciblé
  total : 15 tests.
- La nouvelle dimension reste optionnelle en fin de constructeur pour préserver
  la compatibilité source des appels historiques ; les producteurs WGPU actifs
  la renseignent explicitement.
- Si le disposal LRU réussit mais que `create()` échoue, les événements sont
  `Miss, Evict, Failure`. Si le disposal échoue, l’entrée reste conservée et
  aucun `Evict` n’est annoncé.

## Limites de preuve

Cette wave ne lance pas `gpu-renderer-scenes`, ne crée pas de device WGPU et ne
produit aucune comparaison pixel. Le verdict porte uniquement sur le contrat
Kotlin, la séparation de clés, le budget et l’ordre déterministe observables.

## Vérification

Commande exécutée :

```text
./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionCacheContractsTest
```

Résultat observé : **15 tests, 0 échec** (`BUILD SUCCESSFUL`).
