# Opérations Vertices

Les opérations vertices (`DrawVertices`) permettent de dessiner des
maillages de triangles avec des coordonnées, couleurs et textures
spécifiées par l'utilisateur.

## Flux

```mermaid
flowchart TD
    CMD["NormalizedDrawCommand\nDrawVertices"] --> VERTEX["GPUVertexLayoutPlan"]
    VERTEX --> BUFFERS["Vertex + Index buffers"]
    BUFFERS --> UPLOAD["Upload buffers CPU → GPU"]
    UPLOAD --> BIND["Binding buffers au pipeline"]
    BIND --> DRAW["Draw dans la render pass"]

    style VERTEX fill:#2a3a6b,color:#aac4ff
```

## Autorité de blend

Les vertices utilisent la même autorité de blend que les autres
opérations (`GPUBlendPlan`). Les buffers de vertices et d'index sont
préparés et uploadés avant consommation, comme les textures.

> Voir [Blend & Couleur](blend-couleur.md) pour l'autorité de blend.
