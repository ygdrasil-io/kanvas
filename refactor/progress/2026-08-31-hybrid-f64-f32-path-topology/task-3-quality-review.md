# Task 3 — review qualité

## Verdict

`Quality: FAIL`

## Critical

### Cuts intérieurs et `maxIntersections` absents

`projectedOverlapClaimF64F32` ne donne une identité qu'à `0/1`, puis
`validateProjectedClaimNoOpF64F32` rejette les paramètres intérieurs. Aucun cut,
sommet ou slot `maxIntersections` n'est créé. Le report renvoie cette charge à
la source topology, ce qui contredit le transfert explicite Task 2 vers Task 3.

### La disposition collapse valide après mutation topologique

`PathArrangementF64F32.build` unit immédiatement les deux extrémités de chaque
incidence collapsed, canonicalise ces unions, omet ensuite le carrier du
winding et construit les faces. Le gate `KEEP/DROP/REJECT` n'arrive que dans
`extractBoundaryF64F32`. La sélection travaille donc sur un DCEL déjà fusionné
et privé de l'incidence qui devait rester observable jusqu'à la décision.

## Important

- Dès que de vraies half-edges existent, tout contour entièrement collapsed
  provoque `REJECT` sans consulter l'opération, les fill rules ou les faces.
  Un sibling prouvablement non sélectionné ne peut pas être `KEEP`; un sibling
  sélectionné sous seuil ne peut pas être `DROP`.
- Les tests ajoutés restent publics mais ne prouvent aucune transaction
  projetée, claim chevauchant, cut intérieur, collapsed carrier, sibling ou
  seuil `2^-45`.
- `compareProjectedSourceSpansSemanticF64F32` emploie encore `operand`,
  `contourIndexI32` et indices de segments après égalité géométrique. Ces labels
  ne doivent pas résoudre une égalité géométrique.
- Le report ne peut pas annoncer `Implemented` tout en reconnaissant la voie
  intérieure absente et la disposition sibling non résolue.

## Minor

- `DROP` n'est jamais produit par la disposition selected non vide; cette
  branche de l'enum y est décorative.
- Les directions collapsed s'arrêtent aux frontières de span/seam même lorsque
  le contour possède une continuation réelle.

Vérification fraîche : full JVM+JS vert, 61 tâches; diff-check et worktree
propres. Le diff reste limité à `:math:geometry`, tests communs et report.

Les seules dettes hors Task 3 sont le writer legacy de Task 4 et le modèle
global de budget de Task 5.
