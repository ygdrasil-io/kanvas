# Pré-vol — GPUFramePreflighter, PreparedGPUFrame

Le **GPUFramePreflighter** est la **seule frontière de matérialisation**
du pipeline. C'est le premier (et unique) composant qui crée des
ressources GPU concrètes.

## Flux

```mermaid
flowchart TD
    PLAN["GPUFramePlan\n(sans handles)"] --> PRE["GPUFramePreflighter"]
    PRE --> VAL["Valide les payloads\nsémantiques"]
    PRE --> ALLOC["Alloue les ressources\n(GPUResourceProvider)"]
    VAL & ALLOC --> PGF["PreparedGPUFrame\n(scellé, sans handles exposés)"]

    PGF --> TOKEN["Token de scellement"]
    PGF --> REG["GPURuntimeResourceAdapter\n(registre privé)"]
    PGF --> CMDS["GPUPassCommandStream\nGPUDrawPacketStream"]

    style PRE fill:#3a6b5a,color:#b3ffe0
    style PGF fill:#3a6b5a,color:#b3ffe0
```

## Pourquoi cette séparation ?

- **Correction :** le plan est figé avant toute allocation GPU.
- **Diagnostic :** le plan peut être inspecté sans toucher au GPU.
- **Sécurité :** pas de fuite de handles natifs hors du PreparedGPUFrame.
- **Testabilité :** plan (sans GPU) et pré-vol (avec GPU) testés séparément.

## Gestion des erreurs

- **Échec au pré-vol :** rollback atomique de toutes les ressources.
- **Succès :** PreparedGPUFrame scellé, prêt pour l'exécution.

> Voir [Concepts — Pré-vol & Exécution](../concepts/pre-vol-execution.md)
> pour le détail du flux complet.
