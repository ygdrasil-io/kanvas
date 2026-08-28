# W21 — Path topology

## Portée prouvée

La route publique `kanvas.surface.render` rend, sur une cible WebGPU offscreen
64×64, les topologies de remplissage suivantes sans anti-aliasing :

| Cas | Sémantique | Résultat CPU/GPU |
| --- | --- | --- |
| `even-odd-path-hole` | Deux contours, `EvenOdd` | 0 pixel différent, 100 % |
| `winding-path-hole` | Deux contours d'orientations opposées, winding | 0 pixel différent, 100 % |
| `inverse-winding-triangle-path` | `InverseWinding` | 0 pixel différent, 100 % |
| `inverse-even-odd-path-hole` | `InverseEvenOdd` | 0 pixel différent, 100 % |
| `even-odd-bow-tie-path` | Une auto-intersection bornée, `EvenOdd` | 0 pixel différent, 100 % |

Chaque scène rendue contient une capture GPU native, un oracle CPU indépendant
au centre des pixels, un diff, les statistiques et les diagnostics de route.
Le bundle `reflected-path-topology-refusal` est un cas de refus : il contient
les diagnostics, les statistiques de route et la preuve `submissionDelta=0`,
sans capture de rendu. La provenance des six bundles est le commit
`5b57fd11d897a932130323c73749d142979bb9b9`.

## Refus explicite

`reflected-path-topology-refusal` exerce un path winding à contours multiples
sous réflexion X. La route publique le refuse avant toute submission avec le
diagnostic stable `unsupported.transform.class_downgrade` (`submissionDelta=0`).
La réflexion de topologie n'est donc pas annoncée comme supportée.

## Bornes conservées

Les cas passent par le même contrat de tessellation de path que W20 : limites
de vertices, edge-fan et mémoire avant allocation/submission. Les dépassements
restent couverts par les contrats de refus de `PathTessellator` et
`GPUFramePathApiInventoryTest` introduits précédemment.
