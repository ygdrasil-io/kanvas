# Architecture du Frame Plan WebGPU

Pipeline de rendu GPU pour Kanvas.

## Le pipeline en un schéma

```mermaid
flowchart TD
    subgraph Surface["Surface API"]
        DL["DisplayList\n(opérations de dessin ordonnées)"]
    end

    subgraph Mapping["Traduction"]
        OPM["GPUOpMapper\n(résout l'état mutable)"]
        NDC["NormalizedDrawCommand\n(immuable, sans handles GPU)"]
    end

    subgraph Analyse["Analyse & Recording"]
        REC["GPURecorder\n(validation, classification)"]
        DA["GPUDrawAnalysis\n(route, calque, matériau)"]
        GR["GPURecording\n(scellé, immuable)"]
    end

    subgraph Dependances["Dépendances"]
        TL["GPUTaskList\n(autorité de dépendance)"]
    end

    subgraph Specialisation["Blend & Couverture"]
        BL["GPUBlendPlan\n(29 modes)"]
        DR["GPUDestinationReadPlan\n(stratégie snapshot)"]
        COV["GPUGeometryPlan\nGPUClipPlan"]
    end

    subgraph Planification["Planification"]
        FP["GPUFramePlanner"]
        PLAN["GPUFramePlan\n(linéaire, sans handles)"]
        COORD["GPUFrameCoordinator\n(point d'entrée unique)"]
    end

    subgraph Prevol["Pré-vol"]
        PRE["GPUFramePreflighter\n(seule frontière de matérialisation)"]
        PGF["PreparedGPUFrame\n(scellé, sans handles exposés)"]
    end

    subgraph Execution["Exécution"]
        EXEC["GPUFrameExecutor"]
        CE["1 command encoder"]
        CB["1 command buffer"]
        SUBMIT["1 queue.submit()"]
    end

    subgraph Completion["Complétion"]
        QC["GPUQueueCompletion"]
        OUT["Readback | Present | CompletionOnly"]
    end

    DL --> OPM --> NDC
    NDC --> REC --> DA --> GR
    GR --> TL
    TL --> BL & DR & COV
    BL & DR & COV --> FP --> PLAN
    PLAN --> COORD --> PRE --> PGF
    PGF --> EXEC --> CE --> CB --> SUBMIT
    SUBMIT --> QC --> OUT

    style Surface fill:#2d6a4f,color:#fff
    style Mapping fill:#1b4332,color:#95d5b2
    style Analyse fill:#2a3a6b,color:#aac4ff
    style Dependances fill:#613783,color:#d4bfff
    style Specialisation fill:#6b3a3a,color:#ffb3b3
    style Planification fill:#4a4a4a,color:#ccc
    style Prevol fill:#3a6b5a,color:#b3ffe0
    style Execution fill:#2a5a6b,color:#b3e8ff
    style Completion fill:#5a2a6b,color:#e0b3ff
```

## Principes

Le pipeline est conçu autour de quelques principes simples :

- **Toute la frame est préparée avant la première allocation GPU.**
  L'analyse, le recording et le plan sont purement sémantiques — aucun
  handle natif ne circule avant le pré-vol.

- **Une frame = une soumission.** Un command encoder, un command buffer,
  un `queue.submit()`. Pas de soumissions intermédiaires.

- **Le blend est décidé une fois pour toutes.** Le `GPUBlendPlan` couvre
  les 29 modes et choisit la stratégie optimale : fixed-function quand
  c'est possible, shader WGSL sinon, snapshot borné de la destination si
  le shader doit la lire.

- **Tout passe par le GPU.** Pas de fallback CPU pour les lectures de
  destination. Pas de snapshots uploadés.

- **L'ordre du peintre est préservé.** Le plan est linéaire, pas un DAG
  de tâches. Seule optimisation : le groupement par render pass.

- **Une seule autorité par décision.** Chaque composant a un propriétaire
  unique. Pas de double énumération, pas de chemin parallèle.

## Backend

Kanvas a un seul backend — WebGPU via wgpu4k.
