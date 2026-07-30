# Guide du code

Ce guide aide un contributeur à naviguer dans le code source du pipeline
GPU de Kanvas.

## Organisation des modules Gradle

```
kanvas/
├── :gpu-renderer           Cœur du pipeline GPU (analyse, passes, ressources, exécution)
├── :gpu-renderer-scenes     Scènes de test offscreen et windowed (Kadre)
├── :kanvas                  API publique Surface, Canvas, Picture (compatible Skia)
├── :font:scaler             Rastérisation de polices (glyphes A8)
├── :codec:jpeg2000          Décodeur JPEG 2000
├── :codec:jpegxl            Décodeur JPEG XL
├── integration-tests/skia   Tests GM (Golden Master) portés depuis Skia
└── integration-tests/svg    Tests d'intégration SVG
```

## Où trouver chaque composant

| Composant documenté | Package Kotlin |
|---------------------|---------------|
| `NormalizedDrawCommand` | `:gpu-renderer` → `commands` |
| `GPUOpMapper` | `:kanvas` → `surface.gpu` |
| `GPURecorder`, `GPUDrawAnalysis`, `GPURecording` | `:gpu-renderer` → `recording` |
| `GPUTaskList` | `:gpu-renderer` → `recording` |
| `GPUBlendPlan`, `GPUBlendMode` | `:gpu-renderer` → `passes` |
| `GPUDestinationReadPlan`, `SnapshotGrouping` | `:gpu-renderer` → `destination` |
| `GPUGeometryPlan`, `GPUClipPlan` | `:gpu-renderer` → `geometry`, `clips` |
| `GPUFramePlanner`, `GPUFramePlan` | `:gpu-renderer` → `recording` |
| `GPUFrameCoordinator` | `:gpu-renderer` → `execution` |
| `GPUFramePreflighter` | `:gpu-renderer` → `execution` |
| `GPUFrameExecutor` | `:gpu-renderer` → `execution` |
| `PreparedGPUFrame` | `:gpu-renderer` → `execution` |
| `GPUResourceProvider`, `GPUScratchTexturePool` | `:gpu-renderer` → `resources` |
| `GPUSolidPayloadGatherer`, `GPUDrawSemanticPayload` | `:gpu-renderer` → `payloads` |
| `WGSLModule`, `GPURenderPipelineKey` | `:gpu-renderer` → `wgsl` |
| `GPUQueueCompletionAdapter` | `:gpu-renderer` → `execution` |
| `GPUPreparedSurfaceSession` | `:gpu-renderer` → `execution` |
| `GPUMsaa`, `GPUSampleContinuationKey` | `:gpu-renderer` → `passes` |

## Conventions

- **Package racine :** `org.graphiks.kanvas.gpu.renderer`
- **Nommage :** les acronymes GPU/CPU/WGSL sont en majuscules
  (`GPUBlendPlan`, `WGSLFragment`, `CPUReference`)
- **Contrats :** chaque package expose un fichier `*Contracts.kt` qui
  définit les interfaces et types publics du package
- **Tests :** les tests sont dans `src/test/kotlin/`, miroir de
  `src/main/kotlin/`

## Exécuter les tests

```bash
# Tous les tests du renderer GPU
./gradlew :gpu-renderer:test

# Tests des scènes offscreen
./gradlew :gpu-renderer-scenes:test

# Tests de l'API Surface
./gradlew :kanvas:test

# Un test spécifique
./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanTest"

# Sur Windows (désactiver la vérification de dépendances)
.\gradlew.bat :gpu-renderer:test --dependency-verification=off --no-daemon
```

## Documentation

- **Specs d'architecture :** `docs/superpowers/specs/`
- **Plans d'implémentation :** `docs/superpowers/plans/`
- **Cibles upstream :** `.upstream/target/` et `.upstream/specs/gpu-renderer/`
- **Rapports d'évidence :** `reports/upstream-rebaseline/`

## Ajouter une page à cette documentation

1. Créer un fichier `.md` dans `docs/architecture/docs/` (pipeline ou concepts)
2. L'ajouter à la navigation dans `docs/architecture/mkdocs.yml`
3. Builder : `mkdocs build --config-file docs/architecture/mkdocs.yml --strict`
4. Servir : `mkdocs serve --config-file docs/architecture/mkdocs.yml`
