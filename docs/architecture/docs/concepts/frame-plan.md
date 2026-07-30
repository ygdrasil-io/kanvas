# Concept : Frame Plan (Planification linéaire)

Le Frame Plan est l'ordonnancement d'exécution d'une frame.

## Linéaire vs DAG

```mermaid
flowchart TD
    subgraph DAG["DAG de tâches (non utilisé)"]
        T1 --> T2
        T1 --> T3
        T2 --> T4
        T3 --> T4
    end

    subgraph LINEAR["Plan linéaire (utilisé)"]
        L1["Draw 1"] --> L2["Draw 2"] --> L3["Draw 3"] --> L4["Draw 4"]
    end

    style DAG fill:#6b3a3a,color:#ffb3b3
    style LINEAR fill:#2d6a4f,color:#fff
```

Un DAG permettrait le parallélisme, mais pour le rendu 2D, **l'ordre du
peintre est fondamental** — ce qui est dessiné après recouvre ce qui est
avant. Le Frame Plan est donc **linéaire** : il préserve strictement
l'ordre. Seule optimisation : le **groupement par passe** (draws
compatibles regroupés, jamais réordonnés).

## Structure d'un plan

```mermaid
flowchart TD
    PLAN["GPUFramePlan"] --> S1["RenderPass 1\n{ Draw1, Draw2, Draw3 }"]
    S1 --> SNAP["SnapshotGroup\ncopy → rect(x,y,w,h)"]
    SNAP --> S2["RenderPass 2\n{ Draw4, Draw5 }"]
    S2 --> TRANS["TargetTransition\n→ Layer2"]
    TRANS --> S3["RenderPass 3\n{ Draw6, Draw7 }"]
    S3 --> OUT["CopyToReadback\nou PresentBlit"]

    style PLAN fill:#4a4a4a,color:#ccc
```

## GPUFrameCoordinator

Point d'entrée unique. Enchaîne planification → pré-vol → exécution.
Préserve les refus comme résultats terminaux — un draw refusé n'est
jamais silencieusement ignoré.
