# Architecture du Frame Plan WebGPU

Inspiré de Graphite/Dawn — pipeline de rendu GPU pour Kanvas.

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

## Pourquoi cette architecture ?

Kanvas remplace le rendu GPU immédiat (une opération = un appel GPU)
par un **chemin de rendu mesuré et planifié**. L'idée centrale : au lieu
d'envoyer chaque opération de dessin au GPU une par une, on **prépare
toute la frame à l'avance** — on classe les opérations, on planifie les
passes, on alloue les ressources, puis on exécute en une seule soumission.

## Inspirations et limites

Ce design s'inspire de **Graphite** (le successeur de Ganesh dans Skia)
et de **Dawn** (l'implémentation native de WebGPU), mais ne les porte pas.
Kanvas a un seul backend GPU — WebGPU via wgpu4k — et adopte uniquement
les invariants de performance utiles sans la complexité multi-backend.

## État de la migration

| Opérations | Statut |
|-----------|--------|
| Images | ✅ Migré (FP-04) |
| Texte & glyphes | ⏳ En attente (FP-05) |
| Vertices & meshes | ⏳ En attente (FP-06) |
| Composites (calques, filtres, masques) | ⏳ En attente (FP-07) |
