# Session Surface réutilisable

La **GPUPreparedSurfaceSession** encapsule l'état réutilisable entre
frames successives d'une même Surface.

## État partagé

- Device WebGPU et ses capacités (`GPUCapabilities`)
- Cible canonique (`GPUSceneTarget`)
- Génération du device
- Caches réutilisables (pipelines invariants, pools)
- Compteurs de création/réutilisation

## Cycle de frame

```mermaid
flowchart TD
    SESSION["GPUPreparedSurfaceSession"] --> R1["renderFrame()"]
    R1 --> COORD["GPUFrameCoordinator"]
    COORD --> PLAN["Planification"]
    COORD --> PRE["Pré-vol"]
    COORD --> EXEC["Exécution"]
    EXEC --> OUT["Readback | Present | CompletionOnly"]
    OUT --> R2["renderFrame()"]
    R2 --> COORD

    style SESSION fill:#2a5a6b,color:#b3e8ff
```

Chaque `renderFrame()` crée un coordinateur frame-local frais. La session
retient le backend et les caches entre appels.

**État actuel :** non implémenté (FP-09). Un crash natif
`EXCEPTION_ACCESS_VIOLATION` dans `wgpu_native.dll` bloque la
réutilisation du backend entre frames.
