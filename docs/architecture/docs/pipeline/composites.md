# Opérations Composites

Les opérations composites regroupent les **calques** (`saveLayer`),
les **filtres d'image** (flou, matrice de couleur), les **masques de
flou**, les **pictures**, et les **backdrops**.

## Flux

```mermaid
flowchart TD
    CMD["NormalizedDrawCommand\nDrawLayer"] --> LAYER["GPUDrawLayerPlanner"]
    LAYER --> CHILD["Rendu du contenu enfant\n(dans une cible intermédiaire)"]
    CHILD --> COMPOSITE["Passe composite\n(sample la cible enfant)"]
    COMPOSITE --> FILTER["Filtre optionnel\n(blur, color matrix)"]
    FILTER --> BLEND["Blend du résultat\nsur la cible parent"]

    style LAYER fill:#613783,color:#d4bfff
    style COMPOSITE fill:#2a5a6b,color:#b3e8ff
```

## Types de composites

| Type | Description |
|------|-------------|
| `saveLayer` | Groupe de dessin isolé dans une cible intermédiaire |
| `ImageFilter` | Filtre appliqué au contenu du calque (flou, matrice) |
| `MaskFilter` | Masque de flou appliqué aux bords |
| `Picture` | Sous-arbre de dessin pré-enregistré |
| `Backdrop` | Accès au contenu sous le calque avant dessin |

## Cibles intermédiaires

Chaque composite utilise une cible intermédiaire (`GPUIntermediateTarget`).
Le contenu enfant est d'abord rendu dans cette cible, puis la passe
composite la sample pour l'appliquer sur la cible parent. Les transitions
de cible sont planifiées par le `GPUFramePlanner`.
