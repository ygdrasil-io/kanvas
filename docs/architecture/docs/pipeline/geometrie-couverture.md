# Géométrie & Couverture

## GPUGeometryPlan

Décide comment une forme sera dessinée sur le GPU. Il produit un
`GPUGeometryRoute` — le chemin de rendu pour cette géométrie.

## GPUClipPlan

Gère le clipping et produit un plan de couverture. Formes possibles :

| Forme | Description |
|-------|-------------|
| `FullOrScissor` | Tout ou rien via scissor rect natif |
| `ScalarCoverage` | Anti-aliasing par shader |
| `StencilCoverage1x` | Stencil buffer single-sample |
| `MultisampleAttachmentCoverage` | Couverture via MSAA |
| `LCDCoverage` | Couverture vectorielle RGB par canal |

## Lien avec le blend

La couverture est une entrée critique pour le plan de blend — la formule
fondamentale `result = dst + F * (Blend(src,dst) - dst)` dépend du facteur
de couverture **F**.

```mermaid
flowchart TD
    CMD["NormalizedDrawCommand"] --> GEO["GPUGeometryPlan"]
    CMD --> CLIP["GPUClipPlan"]
    GEO --> ROUTE["GPUGeometryRoute"]
    CLIP --> COV["Couverture\n(FullOrScissor / Scalar / Stencil / MSAA)"]
    ROUTE & COV --> BLEND["→ GPUBlendPlan"]

    style GEO fill:#6b5a2a,color:#ffe0a0
    style CLIP fill:#6b5a2a,color:#ffe0a0
    style BLEND fill:#6b3a3a,color:#ffb3b3
```

## Contexte de migration

`GPUGeometryPlan` et `GPUClipPlan` remplacent les anciens
`GeometryPlan`/`CoveragePlan` hérités de KanvasPipelineIR. Les anciens
existent encore comme entrée de migration et oracle CPU, mais sont
traduits une fois à la frontière.
