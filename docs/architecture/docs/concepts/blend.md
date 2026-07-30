# Concept : Blend

Le **blend** (mélange) combine la couleur d'un nouveau pixel (la **source**)
avec la couleur déjà présente (la **destination**). C'est ce qui permet la
transparence, les ombres, et les effets de calque.

## Les 29 modes

Kanvas supporte les 29 modes de blend Skia. Voici les principaux :

| Mode | Formule | Fixed-function ? |
|------|---------|-----------------|
| `SrcOver` | src + dst × (1 − src.a) | ✅ Oui |
| `SrcIn` | src × dst.a | ✅ Oui |
| `SrcOut` | src × (1 − dst.a) | ✅ Oui |
| `DstOver` | dst + src × (1 − dst.a) | ✅ Oui |
| `DstIn` | dst × src.a | ✅ Oui |
| `SrcATop` | src × dst.a + dst × (1 − src.a) | ✅ Oui |
| `DstATop` | dst × src.a + src × (1 − dst.a) | ✅ Oui |
| `Xor` | src × (1 − dst.a) + dst × (1 − src.a) | ✅ Oui |
| `Plus` | src + dst | ✅ Oui (sauf coverage partielle) |
| `Multiply` | src × dst | ❌ Shader |
| `Screen` | src + dst − src × dst | ❌ Shader |
| `Overlay` | hard-light(dst, src) | ❌ Shader |

## Fixed-function vs Shader

```mermaid
flowchart TD
    MODE["Mode de blend"] --> CAN{"Le hardware peut-il\nle faire exactement ?"}
    CAN -->|Oui| FF["FixedFunctionBlend\n→ GPUBlendState natif\n→ Zéro coût shader"]
    CAN -->|Non| SHADER["ShaderBlend\n→ WGSL fragment shader\n→ Calcule la formule"]

    style FF fill:#2d6a4f,color:#fff
    style SHADER fill:#6b5a2a,color:#ffe0a0
```

## Lecture de la destination

Certains modes (comme `Plus`) ont besoin de la couleur destination pour
calculer le résultat. WebGPU interdit de lire et écrire la même texture
dans une render pass. Solution : **snapshot destination** (copie bornée).

```mermaid
flowchart TD
    NEED["Blend nécessite dst"] --> SNAP["Snapshot borné\nde la zone affectée"]
    SNAP --> COPY["Copie GPU → texture temporaire"]
    COPY --> SHADER["Shader lit depuis la copie"]
    SHADER --> WRITE["Écrit dans la cible"]

    style SNAP fill:#3a6b5a,color:#b3ffe0
```

## Le cas Plus + couverture partielle

`Plus` avec couverture partielle est le cas le plus exigeant :
- Ne peut jamais utiliser le fixed-function
- Nécessite toujours un shader exact (`plus_exact@v1`)
- Requiert la destination quand la couverture est < 1
