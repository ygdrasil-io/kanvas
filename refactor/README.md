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

## Documents

- [Spec architecturale](specs/2026-08-29-skia-renderer-remediation-design.md)
- Le premier plan d'implémentation W0–W2 sera ajouté dans `plans/` après
  validation de la spec.
- Le suivi détaillé de chaque vague sera créé dans `waves/` lors de son
  activation, afin d'éviter des documents vides ou prématurés.

## État des vagues

| Vague | Sujet | État |
| --- | --- | --- |
| W0 | Vérité de référence | Non démarrée |
| W1 | Géométrie immuable dans `:math` | Non démarrée |
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
└── waves/       # états, décisions et écarts par vague active
```

