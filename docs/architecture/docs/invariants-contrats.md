# Invariants & Contrats

## Invariants de design

Ces règles structurent toute l'architecture :

1. **Pas de handles GPU avant le pré-vol.**
2. **Une soumission par frame.**
3. **Pas de fallback CPU.**
4. **Une texture canonique par scène.**
5. **Plan linéaire, pas un DAG de tâches.**
6. **Blend en fixed-function quand c'est exact.**
7. **Snapshots bornés.**
8. **Le présent n'est pas la complétion.**
9. **Une seule autorité par décision.**

## Contrats et autorités

| Décision | Autorité | Package |
|----------|----------|---------|
| Mode de blend | `GPUBlendPlan` | `passes` |
| Stratégie lecture destination | `GPUDestinationReadPlan` | `destination` |
| Dépendances | `GPUTaskList` | `recording` |
| Planification de frame | `GPUFramePlanner` → `GPUFramePlan` | `planning` |
| Point d'entrée produit | `GPUFrameCoordinator` | `planning` |
| Matérialisation GPU | `GPUFramePreflighter` | `preflight` |
| Géométrie | `GPUGeometryPlan` | `geometry` |
| Couverture | `GPUClipPlan` | `coverage` |
| Payloads de dessin | `GPUSolidPayloadGatherer` | `payloads` |
| Ressources natives | `GPURuntimeResourceAdapter` | `resources` |
| Complétion GPU | `GPUQueueCompletionAdapter` | `execution` |
| Télémétrie | Package `telemetry` | `telemetry` |

## Règles de dépendance entre packages

- `passes` n'importe jamais `destination`.
- `resources` n'importe jamais `recording` ni `execution`.
- `telemetry` n'aiguille jamais le travail.

## Limites explicites (non-goals)

- Pas de portage de moteurs de rendu externes.
- Pas de compilateur SkSL, IR, ou VM.
- Pas de multi-backend (WebGPU uniquement).
- Pas de KanvasPipelineIR comme centre sémantique.
- Pas de fallback CPU pour les lectures de destination.
