# Hybrid F64/F32 path topology progress

## Task 1 — Preserve source spans

Completed 2026-08-31. Source segment/parameter provenance now traverses flattening and split edges; the transitional source-topology model and legacy adapter are present.  Projection no longer applies unsafe late compaction to synthetic F64 contours with multiple potential witnesses, and permitted collapse is represented by `Drop`.

Fix round 1: production now constructs the source topology before the legacy arrangement, retains coincident source locations/seams, and carries exact-cut boundaries through source spans.

Fix round 2: removed the legacy raw-split side channel; the transitional adapter emits all legacy edges from authoritative spans and flattened sections.

The Task 1 regression suite and focused JVM/complete JS verification passed before commit. See `task-1-report.md` for commands and evidence.

Fix round 5: le registre exact unique alimente désormais spans, sections et le pont de provenance minimal vers `PathArrangementF64`; le même budget débite cette transition avant toute sortie. La projection n'a plus de compactor, de chord ni de fallback permissif : chaque contour est `Keep`, `Drop` ou `Reject` atomique, et le ledger temporaire valide les claims exacts `(witness, span, intervalle)` avant émission. Les régressions publiques couvrent la subdivision collinéaire, `PointF64` → `OverlapF32`, claims disjoints sous permutation/relabeling et immutabilité, claims chevauchants, et le seuil de budget.

Ruling de transition Task 1 documenté : une preuve locale de claims disjoints conserve les trois régions; des claims chevauchants, ou un `PointF64` promu en `OverlapF32`, rejettent avec `path-f32-projection-collapse`. Les cinq tangences publiques concernées restent donc des rejets conservateurs jusqu'à Task 2. Dette de suivi : Task 2 pourra restaurer ces succès uniquement avec une `PathProjectedCoincidenceF32` locale, bornée et prouvée; aucun alias F32, matching de coordonnées source ni second DCEL hybride n'est introduit ici.
