# FP-05 Task 4 — Hardening des preuves de pureté

Date : 2026-07-29

Base : `ca6ff38bb0e0dca45d75170317c19d12518551e5`

## Résultat

Le Minor M1 de `task-4-controller-review.md` est fermé sans changement de
production ni extension vers Task 5.

Les deux tests de priorité à double corruption assertent désormais exactement
les mêmes quatre compteurs que les matrices simples :

```text
nativePreparationEvents = 0
materializerInvocations = 0
nativePayloadRegistrations = 0
totalCreations = 0
```

Les codes prioritaires restent inchangés :

- vertex avant source :
  `invalid.preflight.text.instance_vertex_abi` ;
- binding avant pipeline key :
  `invalid.preflight.text.composite_binding_layout`.

## Preuve RED causale

`totalCreations` additionne seulement `materializerInvocations` et
`nativePreparationEvents`. Il ne couvre pas
`nativePayloadRegistrations`.

Après ajout des assertions finales, une mutation temporaire test-only a forcé :

```kotlin
adapter.preparedNativeFramePayloadRegistrationCount + 1L
```

Le ciblé des deux tests de priorité a alors échoué 2/2 exactement sur
`nativePayloadRegistrations` :

```text
GPUPreparedTextCompositePreflightTest
  vertex refusal precedes source refusal FAILED
  binding refusal precedes pipeline key refusal FAILED

2 tests completed, 2 failed
BUILD FAILED
```

Cette mutation a ensuite été retirée intégralement. Le diff final ne conserve
que les six assertions manquantes dans
`GPUPreparedTextCompositePreflightTest.kt`.

## Validation GREEN

Ciblé frais :

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextCompositePreflightTest.vertex refusal precedes source refusal" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextCompositePreflightTest.binding refusal precedes pipeline key refusal"
```

Résultat : 2/2 tests passés, `BUILD SUCCESSFUL`.

Step 6 proportionné et entièrement frais :

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel --rerun-tasks \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextCompositePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest"
```

Résultat : 120/120 tests passés, `BUILD SUCCESSFUL` en 40 s.

`rtk git diff --check` réussit sans sortie.

## Scope

- production : aucun changement ;
- Task 5 : aucun changement ;
- probe final : aucune mutation ;
- hardening : six assertions test-only ;
- préoccupation restante : aucune.
