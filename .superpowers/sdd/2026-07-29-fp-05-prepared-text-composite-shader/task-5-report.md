# FP-05 Task 5 — Prepared A8 Text Native Materialization

Date : 2026-07-29

Base : `b064e0fa03fe96390f1801b1406def3446c6bbda`

## Résultat

Task 5 matérialise et encode désormais les runs TextA8 préparés dans la route
native Wgpu4k, sans étendre le scope à Task 6.

- Le cache de session crée et réutilise le shader module, le bind-group layout,
  le pipeline layout et le render pipeline par identité composite exacte.
- La création du pipeline conserve le target format, le blend identity et le
  fixed-function blend state authentifiés par la Task 4.
- Chaque draw exige une acquisition typée, scellée, émise uniquement par le
  cache Text ; un pipeline générique ou une acquisition forgée ne peut pas
  satisfaire le contrat.
- Les atlas A8, instance buffers, draw-uniforms et material uniforms sont
  uploadés selon leur scope key exact. L’ordre de la liste de plans ne constitue
  aucune seconde autorité.
- Les commandes natives suivent l’ordre sémantique des source steps et
  conservent pipeline, bind groups 0/1/2, vertex stride 64, instance ranges et
  scissor exacts.
- Les frames mixtes Core → Image → Text gardent l’ordre des operands, des
  uploads et des draws, puis utilisent un seul `encoder.finish`, un seul submit
  et un seul readback.
- Les ressources frame-local Text sont distinctes de la target, fermées
  exactement une fois après completion, et participent au même protocole de
  quarantine/retry en cas d’échec de fermeture.
- Le handoff `recording` → `execution` reste un DTO passif ; le cache natif
  n’importe ni ne référence directement les contrats `materials`.

ColorGlyph reste explicitement hors scope. Aucun changement n’ajoute Task 6,
une nouvelle route produit, un gate, une animation, Ganesh, Graphite ou un
compilateur SkSL.

## Architecture livrée

### Cache de session

`GPUWgpu4kPreparedTextSessionCache` possède les objets natifs réutilisables et
sépare les autorités :

```text
validated composite program
→ exact module/layout/pipeline cache key
→ private typed acquisition
→ draw-time native handoff
```

Les collisions, y compris entre un objet déjà caché et un nouveau programme
portant la même clé, sont refusées si les champs immuables du programme
divergent. Les formats `rgba8unorm` et `rgba8unorm-srgb`, ainsi que `SRC` et
`SRC_OVER`, sont matérialisés avec leurs états natifs exacts.

### Uploads frame-local

La topology d’upload est exprimée par des plans scellés :

- `Atlas` associe une atlas scope key à son resource plan exact ;
- `Material` associe une material scope key à son resource plan exact ;
- vertex et draw-uniform gardent leurs rôles et ownership propres.

Une permutation de la liste d’uploads conserve donc la même association
resource → binding. Les ressources atlas utilisent une identité native stricte,
sans alias avec la target de scène.

### Encodage et ordre

Chaque run Text publie :

```text
Acquire typed pipeline
→ upload exact frame-local resources
→ set pipeline
→ set scissor
→ bind groups 0 / 1 / 2
→ bind 64-byte instance vertex buffer
→ draw exact instance range
```

Le surface materializer fusionne Core, Image et Text selon les source steps
préflightés. Le dispatcher accepte la nouvelle payload préparée sans modifier
les routes historiques.

### Lifecycle

Le runtime cache reçoit les ressources Text frame-local dans son ownership
scope. Les tests couvrent :

- succès et fermeture exacte ;
- échec après `markSubmitted`, puis quarantine/retry avec comptes exacts ;
- échec de fermeture du readback sans double fermeture Text ;
- absence d’alias target/texture/view ;
- conservation de la génération et des ressources de session.

## Surface de refus

La validation pure reste avant tout travail natif.

| Code | Autorité vérifiée |
| --- | --- |
| `invalid.preflight.text.composite_abi` | target format, blend identity et fixed-function blend state exacts |
| `invalid.preflight.prepared_surface_payload` | acquisition Text typée et topology payload/operand exacte |
| refus Text Task 3/4 existants | programme, binding layout, vertex ABI, draw-uniform et ownership avant matérialisation |

Trois mutations indépendantes du programme final — target format, blend
identity et fixed-function state — refusent avec `COMPOSITE_ABI` et zéro
création, upload, encoder ou submit natif.

## Preuves TDD

RED initial :

- les tests ne compilaient pas avant l’existence du cache de session et du
  materializer Text ;
- un frame TextA8 préparé atteignait encore le guard
  `prepared_text_unmaterialized` ;
- la route de surface ne savait encoder que Core/Image.

GREEN initial :

- cache, acquisition, uploads, bind groups, vertex instances, draw calls et
  lifecycle ont été matérialisés ;
- le dispatcher et la surface route acceptent Text et les frames mixtes ;
- le guard Task 10 a été remplacé uniquement lorsque la payload Text complète
  et préflightée est disponible.

RED de review native :

- target/blend étaient reconstruits avec des constantes ;
- atlas/material uploads dépendaient de leur position ;
- le scissor sémantique n’était pas émis ;
- la frontière de package pouvait être contournée ;
- les tests d’aliasing et d’échec de fermeture ne prouvaient pas assez le
  lifecycle.

RED de review intégration :

- un contrat générique pouvait représenter l’acquisition de pipeline ;
- l’acquisition typée restait directement constructible ;
- aucune preuve Core + Image + Text ne fixait l’ordre global.

GREEN de review :

- matrice 2 formats × 2 blends et mutations négatives Task 4 ;
- plans d’upload scope-keyed avec test de permutation ;
- `SetScissor` exact pour chaque draw ;
- DTO passif et package-boundary gate lexical ;
- acquisition scellée avec unique implémentation top-level privée ;
- frame Core → Image → Text avec ordering et submit/readback uniques ;
- lifecycle testé sous succès, aliasing et deux modes d’échec de fermeture.

## Validation finale

Régression Step 9 :

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedTextRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextOwnershipTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFramePayloadMaterializerDispatcherTest"
```

Résultat : 50/50 tests passés, `BUILD SUCCESSFUL`.

Régression Step 10 :

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.*" \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.*" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedText*Test" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest"
```

Résultat : `BUILD SUCCESSFUL`.

Suites complètes :

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel
```

Résultat final :

- `gpu-renderer` : 2872/2872 tests passés ;
- `kanvas` : 2971/2971 tests passés ;
- Gradle : `BUILD SUCCESSFUL` en 24 s.

Une première relance complète a observé un unique échec de teardown
`failed.surface.prepared.session-close` dans
`GPUAllApiBlendSurfaceTest > DrawImage/SRC_OVER/SCISSOR`, hors fichiers Task 5.
La suite isolée a ensuite passé 1858/1858 tests, puis la suite complète ci-dessus
a confirmé zéro échec.

Le package-boundary gate passe. `rtk git diff --check` réussit sans sortie.

## Reviews indépendantes

Verdicts finaux :

- review native Graphite/Dawn/WebGPU : **READY**, C0/I0/M0 ;
- review intégration/scope : **PASS**, C0/I0/M0.

La review native a notamment relancé les 48 tests de son périmètre initial,
puis vérifié la matrice target/blend, l’acquisition privée, les uploads,
bindings/scissor/stride, le mixed ordering, les collisions de cache et le
lifecycle.

La review intégration finale a relancé 60/60 tests et confirmé la frontière de
package, l’autorité d’acquisition, le handoff passif, le scope Task 5 et
l’absence de dérive Task 6.

## Divergences justifiées

- `GPUPreparedImageFrameResourcePlan.kt` et `ResourceContracts.kt` portent les
  contrats partagés nécessaires à une association upload/resource explicite ;
  aucun comportement Image historique n’est changé.
- `GPUPreparedTextShaderComposer.kt` expose dans le programme immutable les
  champs target/blend nécessaires à la matérialisation exacte et à la
  ré-authentification Task 4.
- `GPUFramePreflighter.kt`, la native payload et les préflights de surface sont
  modifiés pour transporter et authentifier l’acquisition Text avant
  encodage ; ils ne déplacent pas l’autorité pure vers `execution`.
- Les tests Core/Image et runtime adapter reçoivent uniquement les adaptations
  de contrat requises pour prouver la non-régression.

## Handoff

- Task 5 livre la matérialisation Prepared TextA8 et son lifecycle natif.
- Task 6 reste non implémentée.
- ColorGlyph reste gouverné par son guard futur.
- Aucun push n’est effectué dans cette tâche.
