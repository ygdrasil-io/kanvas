# Blend & Couleur — GPUBlendPlan

Le **GPUBlendPlan** est l'autorité canonique de blend. Il couvre les **29
modes de blend** et produit une décision par mode.

## La matrice de décision

```mermaid
flowchart TD
    MODE["Mode de blend + Couverture"] --> FIXED{"Représentable en\nfixed-function ?"}
    FIXED -->|Oui| FF["FixedFunctionBlend\n(stateId)"]
    FIXED -->|Non| DST{"Nécessite de lire\nla destination ?"}
    DST -->|Non| SHADER["ShaderBlendNoDstRead\n(formulaId)"]
    DST -->|Oui| SNAP{"Snapshot borné\npossible ?"}
    SNAP -->|Oui| DSTREAD["ShaderBlendWithDstRead\n(formulaId, besoin déclaré)"]
    SNAP -->|Non| REFUSE["UnsupportedBlend\n(diagnostic)"]

    style FIXED fill:#4a4a4a,color:#ccc
    style DST fill:#4a4a4a,color:#ccc
    style FF fill:#2d6a4f,color:#fff
    style SHADER fill:#6b5a2a,color:#ffe0a0
    style DSTREAD fill:#6b3a3a,color:#ffb3b3
    style REFUSE fill:#8b0000,color:#fff
```

## Séparation des responsabilités

Quand le blend exige de lire la destination, le GPUBlendPlan émet un
`GPUBlendDestinationReadRequirement` — purement sémantique. C'est le
**GPUDestinationReadPlan** qui décide *comment* : snapshot borné, copie
native, ou refus.

> Voir [Concepts — Blend](../concepts/blend.md) pour le détail des 29 modes.

## GPUColorPlan et GPUTargetState

- **GPUColorPlan** : classification alpha source, compatibilité format cible,
  preuves d'opacité.
- **GPUTargetState** : format des attachments, load/store, sample count.
