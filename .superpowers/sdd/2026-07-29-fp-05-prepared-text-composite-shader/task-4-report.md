# FP-05 Task 4 — Composite Program and Vertex ABI Preflight

Date : 2026-07-29

Base : `d1e1d866b95eb218da8dd7d3a4e5faace8ac4870`

## Résultat

Task 4 est implémentée et reste strictement en amont de la matérialisation
Task 10.

- Un validateur pur ré-authentifie le programme composite TextA8 final avec le
  composer Task 2, donc avec le parser, le lowering et la reflection `wgsl4k`,
  sans regex ni handle natif.
- Les source/ABI/pipeline hashes, entry points, binding groups 0/1/2, target,
  blend, seal et layout vertex instancié de 64 octets sont exacts.
- Le slab draw-uniform conserve l’ABI logique de 48 octets, les raw `Float`
  bits, l’alignment observé, le padding nul, le content hash, la slice et les
  limites du device.
- Le draw-uniform est un operand `UniformData/Uniform/FrameLocal` unique,
  inséré exactement entre la partition atlas/vertex et les operands material.
- Ownership, descriptor, préparation avant consommation, absence d’alias et
  de late upload, et allocation mémoire exacte sont vérifiés avant Task 10.
- Les refus Task 9 gardent leur autorité et leurs codes historiques. Task 4 ne
  reconstruit pas la partition non-draw : elle consomme la validation Task 9 et
  ne possède que l’insertion du nouvel operand draw-uniform.
- ColorGlyph reste hors de cette composition, Core/Image restent inchangés et
  aucune matérialisation, route produit, gate ou animation Task 5 n’est ajoutée.

Le fichier physique demandé
`execution/GPUPreparedTextCompositePreflight.kt` déclare le package logique
`recording`. Cette propriété est nécessaire pour respecter la frontière
architecturale qui interdit à `execution` d’importer directement les contrats
sémantiques `materials`; `execution` ne fait que consommer cette autorité pure.

## Ordre de validation

L’ordre stable est :

```text
Task 9 exact non-draw scope/resource authority
→ semantic + affine seal
→ instance vertex ABI/ranges
→ draw-uniform bytes/range/alignment
→ final source + entry points + reflected ABI
→ binding layout + draw-uniform insertion
→ target/blend pipeline key
→ ownership/prepare-before-consumer/allocation
→ Task 10 prepared-text-unmaterialized guard
→ native preparation/materializer
```

`GPUFramePreflighter` ne nécessitait aucun changement : il exécutait déjà la
validation pure du frame avant le guard Task 10, puis seulement la préparation
native. Le test de guard-order fixe explicitement ce contrat.

## Surface de refus

| Code | Autorité vérifiée |
| --- | --- |
| `invalid.preflight.text.composite_source` | WGSL final exact, source hash recomputé et seal |
| `invalid.preflight.text.composite_abi` | entry points, reflected ABI et pipeline key target/blend |
| `invalid.preflight.text.instance_vertex_abi` | stride 64, `Instance`, attributes, `firstInstance`, ranges et buffer vertex |
| `invalid.preflight.text.draw_uniform` | ABI 48 B, alignment, slices, padding, hashes, target, alpha et affine |
| `invalid.preflight.text.composite_binding_layout` | groupes/bindings, insertion ordonnée, ownership, aliasing, préparation et allocation |

Les tests de priorité prouvent notamment :

- vertex avant source pour une double corruption ;
- binding avant pipeline key pour une double corruption ;
- tous les refus Task 4 avant
  `unsupported.preflight.prepared_text_unmaterialized`.

## Preuves TDD

RED initial :

- le premier test de guard-order ne compilait pas, car la surface de refus
  Task 4 n’existait pas ;
- après le squelette minimal, le frame corrompu atteignait encore
  `unsupported.preflight.prepared_text_unmaterialized` ;
- la première matrice complète donnait 17/17 échecs causaux.

RED de review :

- le réordonnancement de l’operand draw-uniform était accepté ;
- une corruption simultanée binding + pipeline retournait
  `COMPOSITE_ABI` au lieu de `BINDING_LAYOUT` ;
- 2/29 tests composite échouaient avant les corrections.

GREEN final :

- 15 mutations composite individuelles ;
- 11 mutations de topology draw-uniform, dont missing/duplicate/wrong role,
  reorder, usage, ownership, descriptor, missing/duplicate preparation, late
  upload alias et allocation manquante ;
- 2 tests de priorité à double corruption ;
- 1 preuve d’operand canonique accepté.

Chaque mutation refuse avec son code de production et conserve :

```text
nativePreparationEvents = 0
materializerInvocations = 0
nativePayloadRegistrations = 0
totalCreations = 0
```

## Validation finale

Régression Step 6 :

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextCompositePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest"
```

Résultat : 120/120 tests passés, `BUILD SUCCESSFUL`.

Suites complètes :

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel
```

Résultat :

- `gpu-renderer` : 2728/2728 tests passés ;
- `kanvas` : 2919/2919 tests passés ;
- Gradle : `BUILD SUCCESSFUL` en 32 s.

Le package-boundary gate passe. `rtk git diff --check` réussit sans sortie.

## Review indépendante

Verdict final : **READY**.

- Critical : aucun.
- Important : aucun restant.

Trois findings Important ont été corrigés puis relus :

1. authentification de la position exacte du draw-uniform ;
2. ordre stable vertex/draw/source/binding/pipeline/ownership ;
3. suppression de la seconde autorité de partition au profit de Task 9.

La relecture finale ne détecte aucun autre problème correctness ou
Graphite/Dawn/WebGPU.

## Divergences justifiées

- `GPUPreparedSurfaceFrameTaskListBuilder.kt` est modifié au-delà de la liste
  indicative du brief parce que le handoff Task 3 exige explicitement de
  publier le draw-uniform dans les operands natifs.
- `GPUFramePreflighter.kt` et
  `GPUPreparedSurfaceNativePreflightTest.kt` ne sont pas modifiés : leurs
  contrats existants couvraient déjà l’ordre pur → Task 10 → natif et les
  régressions Core/Image ; les nouveaux tests ciblés suffisent sans changement
  artificiel.

## Handoff

- La Task 10 reste propriétaire de la matérialisation et de l’encodage natif.
- `firstInstance` est validé comme sélection logique de records 64 B ; aucun
  second offset natif n’est matérialisé ici.
- Aucun support GPU exécuté n’est revendiqué par Task 4 : le frame TextA8 valide
  atteint volontairement le guard Task 10 existant avec zéro effet de bord.
