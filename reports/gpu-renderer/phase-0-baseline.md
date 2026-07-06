# GPU Phase 0 baseline

Statut: Phase 0 baseline locale close.

Cette baseline locale ferme le socle d'instrumentation GPU avant les phases de
ressources, lifetime et batching. Elle ne change pas le rendu et ne promet pas
de resultat dashboard par famille.

## Compteurs disponibles

- render passes
- offscreen/window passes
- submissions
- command buffers
- buffers/textures/samplers/bind groups
- queue writes
- uniform slab counters

## Limits GPU disponibles

- `maxTextureDimension2D`
- `copyBytesPerRowAlignment`
- `minUniformBufferOffsetAlignment`

Les limits peuvent etre conservatrices quand le runtime ne fournit pas une
source observee fiable. La source du fact doit rester explicite dans les dumps
de capabilities.

## Snapshot runtime

`GPURuntimeBaselineSnapshot` relie les runtime counters, cache counters et
capability facts dans des lignes courtes `gpu-phase0.baseline`,
`gpu-phase0.cache` et `gpu-phase0.capability`.

Le but est de detecter rapidement une derive entre ce rapport et la preuve
runtime dumpable, sans exposer de runtime handles.

## Preuve de validation

La validation ciblee couvre:

- tests de contrats runtime
- tests de capabilities
- smoke runtime GPU
- test du rapport Phase 0

Le test complet du module peut encore echouer sur le package-boundary connu.
Cet echec n'est pas un critere Phase 0b tant qu'aucun nouvel echec n'apparait.

## Hors Phase 0b

Ces points restent des follow-ups nommes:

- aggregation par GM
- integration dashboard GM
- rapport par famille

La Phase 0b ferme la baseline locale. Les follow-ups ci-dessus ne doivent pas
etre traites comme des criteres implicites de cette tranche.
