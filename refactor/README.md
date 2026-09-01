# Refactor du renderer Skia

Ce dossier centralise les documents humains de pilotage de la remédiation
architecturale du renderer. Les artefacts techniques générés — captures PNG,
diffs, métriques, manifests et résultats JSON — restent dans leurs répertoires
de preuve existants.

## Objectif

Atteindre une compatibilité Skia quasi isopixel hors `font` et `codec`, avec :

- 100 % des GMs éligibles exécutées ;
- au moins 95 % des GMs éligibles conformes à leur politique pixel ;
- zéro refus terminal non classifié ;
- zéro fallback CPU silencieux ;
- une liste fermée et documentée des écarts Skia acceptés.

## Documents autoritaires

### Spécifications

- [Spec architecturale](specs/2026-08-29-skia-renderer-remediation-design.md)
- [Moteur topologique robuste des paths dans `:math`](specs/2026-08-30-math-path-topology-engine-design.md)
- [Arrangement hybride F64/F32 pour les opérations de paths](specs/2026-08-31-hybrid-f64-f32-path-topology-design.md)
- [Admission conservative de la topologie hybride](specs/2026-09-01-conservative-hybrid-topology-admission-design.md)

### Plans

- [Plan d'implémentation W0–W2](plans/2026-08-29-w00-w02-foundation-implementation-plan.md)
- [Plan du moteur topologique robuste](plans/2026-08-30-math-path-topology-engine-implementation-plan.md)
- [Plan de topologie hybride F64/F32](plans/2026-08-31-hybrid-f64-f32-path-topology-implementation-plan.md)
- [Plan d'admission conservative](plans/2026-09-01-conservative-hybrid-topology-admission-implementation-plan.md)

### État et rapports finaux

- [Baseline de vérité W00](waves/W00-truth-baseline/status.md) — gate stricte
  non atteinte en raison de la quarantaine temporaire `jpg-color-cube`.
- [État W01 — géométrie immuable et format `Picture` v8](waves/W01-immutable-geometry/status.md)
  — preuve d'immuabilité, compatibilité de lecture v7 et writer v8 stable.
- [État consolidé de la topologie hybride](progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md)
- [Rapport d'implémentation de l'admission conservative](progress/2026-09-01-conservative-hybrid-topology-admission/implementation-report.md)
- [Revue de spécification de l'admission conservative](progress/2026-09-01-conservative-hybrid-topology-admission/spec-review.md)
- [Revue qualité de l'admission conservative](progress/2026-09-01-conservative-hybrid-topology-admission/quality-review.md)

## État des vagues

| Vague | Sujet | État |
| --- | --- | --- |
| W0 | Vérité de référence | Baseline publiée ; gate stricte non atteinte |
| W1 | Géométrie immuable dans `:math` | Périmètre fonctionnel implémenté et prouvé ciblé pour les frontières d'enregistrement/Picture : snapshots immuables et writer `Picture` v8 stable. Gate stricte **NON ATTEINTE / bloquée** par la baseline globale de 51 échecs GPU/Image ; topologie source, topologie hybride F64/F32 et admission conservative restent documentées séparément |
| W2 | `Scene IR` et frontières de modules | Non démarrée |
| W3 | `gpu-plan` et premier `RenderGraph` | Non démarrée |
| W4 | Geometry/coverage | Non démarrée |
| W5 | Material graph | Non démarrée |
| W6 | Layers et effets | Non démarrée |
| W7 | Convergence GM | Non démarrée |
| W8 | Retrait legacy et runtime | Non démarrée |

## Organisation

```text
refactor/
├── README.md
├── specs/       # designs et décisions architecturales approuvées
├── plans/       # plans d'implémentation exécutables
├── progress/    # états consolidés et rapports finaux
└── waves/       # états, décisions et écarts par vague active
```
