# Exécution — GPUFrameExecutor

Le **GPUFrameExecutor** consomme un `PreparedGPUFrame`, vérifie son
scellement, et le traduit en appels WebGPU natifs.

## Une soumission par frame

```mermaid
flowchart LR
    PGF["PreparedGPUFrame"] --> EXEC["GPUFrameExecutor"]
    EXEC --> CHECK["Vérifie le scellement"]
    CHECK --> CE["Crée 1 command encoder"]
    CE --> PASSES["Enregistre toutes les passes"]
    PASSES --> CB["Termine 1 command buffer"]
    CB --> SUBMIT["queue.submit()"]

    style EXEC fill:#2a5a6b,color:#b3e8ff
    style SUBMIT fill:#2d6a4f,color:#fff
```

C'est un invariant de performance fondamental : **un frame = une
soumission**. Aucune soumission intermédiaire, aucun flush partiel.

Le GPUFrameExecutor arme également le ticket de complétion avant la
soumission, et invoque l'action de présent post-soumission si la frame
est destinée à une fenêtre.
