# Concept : Coverage (Couverture)

La **couverture** représente la proportion d'un pixel recouverte par
la forme dessinée. 1.0 = pixel entièrement couvert, 0.5 = à moitié
couvert (bord anti-aliasé).

## La formule fondamentale

```
result = dst + F × (Blend(src, dst) − dst)
```

où `F = geometryCoverage × clipCoverage`.

## Impact sur le blend

```mermaid
flowchart TD
    F1["F = 1\n(couverture pleine)"] --> OPT["Chemin optimal"]
    OPT --> FF["Fixed-function si possible"]
    OPT --> SHADER["Shader sans lecture dst"]

    F2["F < 1\n(couverture partielle)"] --> COST["Chemin plus coûteux"]
    COST --> CHECK{"Blend nécessite\ndestination ?"}
    CHECK -->|Non| SHADER2["Shader sans snapshot"]
    CHECK -->|Oui| SNAP["Shader + snapshot borné"]

    style F1 fill:#2d6a4f,color:#fff
    style F2 fill:#6b3a3a,color:#ffb3b3
```

## Formes de couverture

| Forme | Usage |
|-------|-------|
| `FullOrScissor` | Shapes pleines, clipping rectangulaire |
| `ScalarCoverage` | Anti-aliasing par shader |
| `StencilCoverage1x` | Stencil buffer, clipping complexe |
| `MultisampleAttachmentCoverage` | MSAA natif |
| `LCDCoverage` | Vectoriel RGB par sous-pixel (LCD) |
