# Concept : Complétion & Cycle de vie

La soumission GPU est asynchrone.

## États d'exécution

```mermaid
stateDiagram-v2
    [*] --> Planned
    Planned --> Prepared : pré-vol réussi
    Prepared --> Encoded : encoder rempli
    Encoded --> Submitted : queue.submit()
    Submitted --> GPUCompleted : succès GPU
    Submitted --> FailedPreSubmit : échec avant soumission
    Submitted --> FailedAfterSubmit : échec après soumission
    FailedAfterSubmit --> Quarantined
    GPUCompleted --> [*]
    FailedPreSubmit --> [*]
```

## États de sortie

```mermaid
stateDiagram-v2
    [*] --> NotApplicable
    NotApplicable --> Acquired
    Acquired --> Presented
    Acquired --> PresentFailed
    Presented --> [*]
    PresentFailed --> [*]
```

## Règles

- **Présent ≠ Complétion.** Ressources libérées seulement après
  `GPUCompleted`.
- **Complétion armée avant présent.** Échec d'armement → quarantaine, mais
  ne bloque pas le présent.
- **Readback après complétion.** Attend `GPUCompleted`, mappe le staging
  buffer, dé-padde, unmappe.
