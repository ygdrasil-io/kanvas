# Task 2 fix round 2 — Sol quality re-review

Base : `2eae107ba`  
Head : `edc761be3`

## Verdict

- `Quality: FAIL`
- `Gate qualité: NEEDS FIXES`

## Critical

1. **Cuts projetés intérieurs absents.** `limitsI32` est inutilisé, les identités n'existent que pour les bornes `0/1` et tout bound intérieur rejette (`PathHybridTopologyF64F32.kt:148`, `:594`, `:1452`). Implémenter une transaction de cuts projetés n-way avec provenance exacte et débit `maxIntersections` avant alias/DCEL, plus un test public réellement intérieur.

2. **Preuve d'ordre angulaire incorrecte.** Les directions utilisent les points canoniques, le DCEL trie par rays F32 et le validateur ne compare pas l'ordre mutuel des bundles source (`PathArrangementF64F32.kt:892`, `:1555`, `:1587`). Un ordre source 20°/10° peut passer sous embeddings 0°/30°. Utiliser les incidence points, trier/valider les bundles F64 adjacents et rejeter toute inversion avant l'ordre F32 ; couvrir high-valence/permutations.

3. **Collapse/zero-area non atomique.** Des retours vides/cancellations précèdent la disposition, les collapsed spans sans half-edge sont ignorés et `-d/+d` rend le test de dot tautologique (`PathArrangementF64F32.kt:93`, `:353`, `:1383`; `PathHybridTopologyF64F32.kt:320`). Décider chaque contour avant retours/cancellations, avec vrais rays voisins ; Drop contour complet sous/au seuil, sinon Reject, jamais sortie partielle. Tests publics sous/égal/au-dessus avec sibling significatif.

## Important

- Le nouveau travail reste sous-débité : validation des bundles, parcours de sections collapsed et sommes d'expansions (`PathArrangementF64F32.kt:1296`, `:1407`, `:1583`). Précompter en I64 et débiter avant scans/allocations/prédicats. La dette globale Task 5 reste distincte.

## État

- Fermés : chord/carriers, source sweep exact, tickets/overlaps source, point canonique vs incidence, limites finales, normalisation, fixtures internes, portée math.
- Ouverts : cuts projetés/maxIntersections hybride, ordre source des bundles, disposition atomique collapsed/zero-area, budget local complet.
