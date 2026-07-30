# Concept : Pré-vol & Exécution

La séparation planification/exécution est un choix architectural
fondamental.

## Pourquoi deux phases ?

```mermaid
flowchart TD
    PLAN["GPUFramePlan\n(sans ressources GPU)"] --> PRE["GPUFramePreflighter\n(matérialisation)"]
    PRE --> PGF["PreparedGPUFrame\n(scellé, ressources encapsulées)"]
    PGF --> EXEC["GPUFrameExecutor\n(exécution native)"]
    EXEC --> GPU["WebGPU"]

    subgraph "100% sémantique"
        PLAN
    end

    subgraph "Frontière GPU"
        PRE
        PGF
    end

    subgraph "Exécution native"
        EXEC
        GPU
    end

    style PLAN fill:#4a4a4a,color:#ccc
    style PRE fill:#3a6b5a,color:#b3ffe0
    style PGF fill:#3a6b5a,color:#b3ffe0
    style EXEC fill:#2a5a6b,color:#b3e8ff
```

1. **Correction :** plan figé et validé avant allocation GPU.
2. **Diagnostic :** plan inspectable sans GPU.
3. **Sécurité :** handles GPU natifs jamais exposés.
4. **Testabilité :** plan (sans GPU) et exécution (avec GPU) testés
   indépendamment.

## Gestion des erreurs

| Échec | Comportement |
|-------|-------------|
| Pré-vol | Rollback atomique de toutes les ressources |
| Pré-soumission (`FailedPreSubmit`) | Libération normale |
| Post-soumission (`FailedAfterSubmit`) | Quarantaine jusqu'au teardown |
