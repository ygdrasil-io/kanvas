# Planification — GPUFramePlanner, GPUFramePlan

Le **GPUFramePlanner** consomme le `GPUTaskList` et produit un
**GPUFramePlan** — un plan d'exécution linéaire, ordonné, immuable, et
**sans aucune ressource GPU native**.

## Contenu du plan

```mermaid
flowchart TD
    TL["GPUTaskList"] --> FP["GPUFramePlanner"]
    FP --> PLAN["GPUFramePlan"]

    PLAN --> PASSES["Segments de render pass\n(groupes de draws compatibles)"]
    PLAN --> SNAPS["Snapshots bornés\n(copies ciblées de la destination)"]
    PLAN --> TRANS["Transitions\n(calque, filtre, cible)"]
    PLAN --> BUDGET["Budget mémoire\n(pic transitoire, résident)"]
    PLAN --> OUTPUT["Sortie\n(Readback / Present / CompletionOnly)"]

    style FP fill:#4a4a4a,color:#ccc
    style PLAN fill:#4a4a4a,color:#ccc
```

## GPUFrameCoordinator

Le **GPUFrameCoordinator** est le point d'entrée unique vers la
planification, le pré-vol et l'exécution. Aucun code externe ne peut
court-circuiter ce chemin. Il préserve les refus comme des résultats
terminaux.

> Voir [Concepts — Frame Plan](../concepts/frame-plan.md) pour la
> comparaison plan linéaire vs DAG.
