# W5 — Material graph commun et exécution native

Date : 2026-09-09  
Branche de design : `codex/w5-material-graph`  
Base stackée : `codex/w4e-complex-clips` / PR #2393

## 1. Objectif

W5 rend le matériau indépendant de la famille géométrique. Une même scène
`MaterialNode` doit produire une autorité de programme, de bindings et de
ressources unique, consommable par les Rect, RRect, Path, points, images,
vertices et textes déjà admis par les routes préparées.

La vague couvre séquentiellement :

1. solid et opacity ;
2. blends fixed-function et destination-read ;
3. gradients linéaire, radial, sweep et conical ;
4. matrices locales, `CoordClamp` et tile modes ;
5. images déjà décodées RGBA/A8 et sampling ;
6. color filters et espaces de travail ;
7. graphes composés et matériaux procéduraux ;
8. runtime effects enregistrés.

W5 ne change pas la géométrie W4. Les combinaisons qui dépendent encore de la
topologie AA4 native conservent un refus de capability exact ; aucune
capability ne sera inventée pour élargir artificiellement la matrice.

## 2. État de départ

L'IR sémantique existe déjà dans `:render-ir`. `MaterialNode` représente les
sources solides, quatre gradients, images, blend children, runtime effects,
matrices locales, color filters, opacity, working color spaces, bruit et
`CoordClamp`. Les snapshots, identités canoniques et limites de graphe sont
déjà en place.

Le renderer possède également un `GPUPreparedMaterialProgramCompiler` et des
lowerers spécialisés. Ils ne forment toutefois pas encore l'autorité commune
du rendu :

- les compilers W4 reconnaissent directement `MaterialNode.Solid` ;
- text et vertices appellent le compilateur préparé par leurs propres routes ;
- gradients et images utilisent encore plusieurs descripteurs, clés et ABI ;
- des branches de `GPUMaterialMapper` remplacent un matériau non supporté par
  son child, une couleur transparente ou un autre fallback sémantique ;
- l'ABI gradient borne fonctionnellement les stops à 16 ;
- les programmes destination-read sont spécialisés par plusieurs lanes de
  géométrie ;
- le `RenderGraph` ne porte pas une autorité material complète et uniforme
  avant `Ready`.

W5 consolide ces éléments ; il ne crée pas une seconde hiérarchie de matériaux.

## 3. Approches considérées

### 3.1 Étendre chaque lane existante

Chaque route Rect/RRect/Path/text/vertices apprendrait progressivement les
gradients, images et blends. Cette approche livre vite un cas isolé, mais
multiplie les matrices de support, les clés pipeline et les divergences de
couleur. Elle est rejetée.

### 3.2 Remplacement big-bang du renderer material

Tous les chemins existants seraient remplacés en une seule branche. Cela
réduit théoriquement la période de coexistence, mais rend le diagnostic des
régressions et la revue presque impossibles. Cette approche est rejetée.

### 3.3 Compilateur commun, migration verticale séquentielle

Une autorité material plan-first est introduite, puis chaque famille est
activée par une tranche verticale publique. Les anciens chemins ne sont
retirés qu'après preuve du remplacement. C'est l'approche retenue.

## 4. Frontières de modules

### 4.1 `:render-ir`

`:render-ir` reste l'autorité backend-neutral :

- `MaterialNode`, ses children et leurs identités canoniques ;
- valeurs sémantiques immuables : couleurs, stops, tile modes, sampling,
  interpolation, matrices et ressources image ;
- `GraphLimits` communs, avec validation itérative et bornée ;
- compatibilité `Picture`/`SceneArchive` seulement lorsqu'un changement de
  schéma sémantique est réellement nécessaire.

Il ne contient ni WGSL, ni layout WebGPU, ni pipeline key native.

### 4.2 `:math`

Les nouvelles valeurs géométriques ou de transformation restent dans
`:math:geometry` et `:math:matrix`, avec suffixes I32/I64/F32/F64. En
particulier, la composition et l'inversion des matrices locales de sampling
sont préparées en F64 dans `:math:matrix`, puis projetées explicitement vers
les données F32 du backend.

W5 n'introduit aucun Rect, Point, Size ou Matrix privé dans `:kanvas` ou
`:gpu-renderer`.

### 4.3 `:color-management`

`:color-management` porte les conversions de gamut, transfer function et
working color space. Le renderer ne duplique pas les formules de conversion
dans plusieurs materializers.

### 4.4 `:gpu-plan`

`:gpu-plan` compile `MaterialNode` en deux produits immuables :

- `MaterialProgramPlan` : structure du DAG, ordre d'évaluation, opcodes,
  contrats de couleur, besoins destination-read et identité structurelle ;
- `MaterialBindingPlan` : blocs uniformes, buffers de stops, textures,
  samplers, children, usages, tailles, alignements et lifetimes.

Ces plans ne contiennent aucun handle ni source WGSL. Ils sont attachés aux
draws et aux passes du `RenderGraph` avant sa publication `Ready`.

### 4.5 `:gpu-renderer`

`:gpu-renderer` consomme exclusivement le plan scellé pour :

- sélectionner ou assembler un module WGSL enregistré ;
- valider parser, reflection et ABI ;
- matérialiser exactement les bindings déclarés ;
- construire la pipeline key structurelle ;
- uploader les valeurs dynamiques sans recompiler le matériau ;
- gérer le cache et les ressources frame-locales.

Le `GPUPreparedMaterialProgramCompiler` existant est réutilisé comme point de
départ, puis séparé entre compilation sémantique et exécutable backend. Il ne
doit plus recevoir un descripteur reconstruit différemment par chaque famille
de draw.

### 4.6 `:kanvas`

`:kanvas` capture `Paint`/`Shader` vers `MaterialNode`, transmet les faits de
target et publie les résultats. Il ne choisit ni shader WGSL, ni ABI, ni
fallback de matériau.

## 5. Contrat du material graph

### 5.1 Séparation structure / valeurs

La clé de programme dépend uniquement de la structure qui modifie le code :

- famille de nœud et ordre des children ;
- mode de blend ;
- tile/sampling/interpolation lorsqu'ils changent la fonction exécutée ;
- contrats de couleur et topologie de bindings ;
- version des dictionnaires et runtime effects.

Elle ne dépend pas des couleurs, positions, matrices, alpha, pixels, IDs de
ressource ni du nombre concret de stops lorsque l'ABI reste identique. Ces
valeurs appartiennent aux bindings dynamiques.

Deux graphes structurellement identiques avec des valeurs différentes doivent
donc partager le programme sans partager leurs données.

### 5.2 Ordre et canonicalisation

L'ordre `dst`/`src`, l'ordre des children runtime et l'ordre d'évaluation sont
préservés. Aucune réécriture commutative n'est autorisée.

Les normalisations algébriques sont limitées aux équivalences exactes :

- multiplication de deux opacités finies ;
- suppression d'une matrice locale identité ;
- composition ordonnée de matrices locales adjacentes ;
- neutralisation d'un `Opacity(..., 1)` ;
- publication d'un matériau transparent pour `Opacity(..., 0)` seulement si
  le blend et les effets associés ne rendent pas la distinction observable.

La Scene IR d'origine reste inchangée ; seule la représentation compilée peut
être canonisée.

### 5.3 Limites et budgets

Les limites de graphe restent `GraphLimits(maxDepth=64, maxNodes=4096)`. Les
ressources sont ensuite bornées par des budgets calculés : octets uniformes,
octets de stops, textures, samplers, bindings, passes et mémoire temporaire.

Une limite device ou de frame est vérifiée avant toute allocation native. Un
dépassement produit un diagnostic typé et transactionnel, jamais une
substitution de matériau.

## 6. Couleur et alpha

Le contrat interne est RGBA linéaire prémultiplié. L'alpha reste linéaire et
n'est jamais soumis à la transfer function.

- les couleurs sRGB sont décodées une fois vers le linéaire ;
- les couleurs non prémultipliées sont prémultipliées après conversion ;
- l'interpolation de gradient suit le `ColorInterpolation` demandé ;
- les color filters déclarent explicitement leur espace d'entrée et de sortie ;
- le target sRGB 1× W4 reste le premier target physique ;
- Display P3, Linear sRGB et les gamuts/transfer functions déjà décrits par
  `ColorSpace` sont convertis vers ce target via `:color-management` ;
- un target HDR ou une combinaison non implémentée reste un refus typé, pas un
  calcul sRGB implicite.

Le rendu encode vers sRGB une seule fois à l'écriture finale. Les blends
fixed-function et shader utilisent le même contrat prémultiplié.

## 7. Gradients

Les quatre familles Linear/Radial/Sweep/Conical utilisent une même séquence de
stops immuable et un même contrat d'interpolation.

Le plafond sémantique de 16 stops est supprimé. Les stops sont matérialisés
dans un read-only storage buffer, partageable par identité canonique et borné
par les limites de buffer/frame. Le shader reçoit un offset et un count
dynamiques. Une optimisation inline pour les petits gradients est permise plus
tard, à condition qu'elle produise strictement les mêmes pixels et qu'elle ne
change pas la capability publique.

La préparation conserve les stops dupliqués et les discontinuités. Les entrées
non finies, les séquences invalides et les tailles hors budget sont refusées
avant création de ressource. Aucun LUT texture approximatif n'est utilisé pour
masquer une limite de stops.

## 8. Matrices locales, coordonnées et tile modes

Le sampling applique l'inverse de la chaîne de matrices locales au point device
déjà produit par la géométrie. La composition respecte l'ordre capturé et ne
modifie pas le CTM géométrique.

Les quatre tile modes sont pris en charge par une fonction commune :

- `CLAMP` ;
- `REPEAT` ;
- `MIRROR` ;
- `DECAL`, avec transparent hors domaine.

`CoordClamp` est appliqué dans l'espace local du child. Une matrice singulière,
non finie ou non représentable après projection F64→F32 produit un refus exact.

## 9. Images déjà décodées

W5 consomme uniquement `ImageResourceSnapshot` et les pixels déjà disponibles.
Il ne détecte, ne décode et n'encode aucun PNG/JPEG/WebP/GIF/BMP.

La tranche image couvre :

- RGBA_8888, BGRA_8888, SRGBA_8888 et ALPHA_8 ;
- `OPAQUE`, `PREMUL` et `UNPREMUL` lorsqu'ils sont cohérents avec le payload ;
- nearest, linear et cubic B/C ;
- les quatre tile modes et matrices locales ;
- conversion du `ColorSpace` source vers le contrat linéaire du matériau ;
- déduplication des uploads par snapshot immuable, sans dépendre de l'identité
  objet appelante.

Les autres `ColorType` restent hors de cette tranche tant qu'une conversion
exacte depuis leurs pixels déjà décodés n'est pas définie. Ce refus n'est pas
une exclusion codec.

## 10. Blends

`GPUBlendPlanner` reste l'autorité de classification :

- fixed-function lorsque WebGPU exprime exactement le mode et la couverture ;
- `NoOp` pour `DST` lorsque la géométrie et les effets n'imposent aucun travail ;
- destination texture + formule GPU pour les autres modes ;
- refus exact lorsque MSAA ou une autre topologie empêche la lecture destination.

Les copies destination sont groupées par dépendances et paint order, pas par
type de géométrie. Plusieurs draws destination-read dans une même frame doivent
donc être représentables sans invalider les seals V/I/U de W4.

W5b cible explicitement les 45 failures historiques
`GPUAllApiBlendSurfaceTest :: DrawPoint` : trois commandes successives, quinze
modes et trois contextes. Elles ne seront retirées du ledger qu'après preuve
publique de pixels et de route préparée.

`MaterialNode.Blend(dst, src)` est distinct du blend final du draw. Le premier
évalue deux child materials pour produire la source ; le second compose cette
source avec le target. Les deux étapes ont des plans et diagnostics séparés.

## 11. Color filters, graphes composés et procédural

Les color filters non spatiaux sont des étapes material : Matrix, Blend,
Compose, Table, Lighting, sRGB/Linear, HSLA, Lerp, HighContrast, Luma et
Overdraw. Leurs données dynamiques restent hors de la program key.

Les runtime color filters ne deviennent exécutables qu'avec l'autorité
enregistrée de W5h. Les image filters, mask filters, blur, crop, backdrop et
autres effets spatiaux restent W6.

`MaterialNode.Blend`, `WithColorFilter`, `WithWorkingColorSpace`, `Opacity`,
`WithLocalMatrix` et `CoordClamp` composent un DAG partagé. PerlinNoise et
FractalNoise sont livrés comme programmes built-in bornés ; leurs paramètres
ne créent pas de variantes de shader.

Aucun nœud inconnu ou non exécutable n'est remplacé par un child, un solid ou
transparent. Le résultat est un refus stable avant ownership natif.

## 12. Runtime effects enregistrés

W5 n'introduit pas de frontend SkSL arbitraire. Un runtime effect est admis
uniquement si son identité et sa version résolvent un programme enregistré qui
possède :

- une implémentation CPU indépendante utilisable par l'oracle ;
- un module WGSL parser-validé ;
- une reflection et une ABI exactes ;
- une liste ordonnée de uniforms, textures, samplers et child materials ;
- une identité canonique et une version de dictionnaire ;
- des budgets de graphe, bindings et ressources validés.

Un effet absent, une version différente, un child incompatible ou une ABI non
prouvée reste terminal. Aucun WGSL fourni par l'appelant n'est exécuté sans
enregistrement.

## 13. Data flow

```text
Paint / Shader public
        │ snapshot immuable
        ▼
MaterialNode (:render-ir)
        │ compilation sémantique + capabilities
        ▼
MaterialProgramPlan + MaterialBindingPlan (:gpu-plan)
        │ attachés aux draws/passes/resources avant Ready
        ▼
RenderGraph scellé
        │ lowering backend + validation WGSL/reflection/ABI
        ▼
PreparedMaterialExecutable (:gpu-renderer)
        │ location/upload/bind des seules ressources planifiées
        ▼
passes géométriques W4 + blend final + readback public
```

Une fois la frame promue à W5, tout échec est terminal pour cette frame. Aucun
fallback legacy ou CPU n'est possible après ownership.

## 14. Gestion des erreurs et récupération

Les refus sont classifiés par frontière : capture, graph validation, material
program, binding, color conversion, capability, budget, preflight et native.
Le diagnostic conserve l'index du draw, l'identité material et la ressource
concernée sans exposer d'objet backend.

Les plans et snapshots restent publiés seulement après validation complète.
Un refus de budget ne consomme aucune allocation. Une panne native libère ou
met en quarantaine les ressources acquises selon les contrats de pool existants.

Conformément à la contrainte de tests, W5 ne crée ni API de contrôle de panne,
ni proxy interne, ni reflection ou compteur d'appels. La récupération est
prouvée par un refus public atteignable suivi d'un rendu exact sur la même
surface. Les sites natifs non injectables restent des gaps d'intégration
documentés.

## 15. Stratégie de tests

Le développement suit RED → GREEN → refactor pour chaque comportement.

Les preuves obligatoires sont publiques et mutation-sensitive :

- `Surface`, `Canvas`, `Picture` et `render()` ;
- pixels byte-exacts ou tolérance uniquement lorsqu'elle provient d'un calcul
  analytique explicite et non d'un seuil de similarité ;
- petit oracle CPU indépendant pour gradient, sampling, color et blend ;
- preuve `RenderResult` des scopes native Render/Readback afin qu'un fallback
  legacy ne puisse satisfaire les pixels ;
- mutation des stops, matrices, pixels, filters, uniforms et children après
  capture ;
- frames mixtes Rect/RRect/Path/points/text/vertices quand leur route W4 est
  déjà disponible ;
- refus puis récupération publique sur la même surface ;
- validation JVM/JS des nouvelles fonctions `:math`.

Sont interdits : tests de source shape, accès private/internal comme preuve,
reflection, call counts, assertion d'identité du cache, injection artificielle
de capability et tests d'infrastructure du code.

Les tests `font`, codecs, GM Skia, dashboard, renders/baselines,
`:integration-tests:skia` et `jpg-color-cube` restent hors des gates W5. La
convergence GM est réservée à W7.

## 16. Découpage des PR stackées

Chaque tranche part de la précédente et possède sa propre PR :

| Tranche | Contenu | Gate de sortie |
| --- | --- | --- |
| W5a | plans communs, solid, opacity, suppression des fallbacks silencieux concernés | pixels solid/opacity identiques sur les géométries W4 admises |
| W5b | fixed-function commun, destination-read multi-draw et snapshot grouping | fermeture prouvée des 45 `DrawPoint` historiques |
| W5c | quatre gradients et stop buffer sans plafond 16 | 1/2/16/>16 stops, doublons et quatre géométries |
| W5d | matrices locales, `CoordClamp`, quatre tile modes | transform classes et domaines exacts |
| W5e | images RGBA/A8 déjà décodées, nearest/linear/cubic | sampling, alpha, color space et mutation pixels |
| W5f | color filters et working spaces | chaîne colorimétrique exacte et graphes filter composés |
| W5g | blend children, DAG partagé, Perlin/Fractal | ordre child, partage, limites et récupération |
| W5h | runtime effects enregistrés, nettoyage et gates globales | ABI/children exacts, aucune route material parallèle restante |

Le document de suivi sera `refactor/waves/W05-material-graph/status.md`. Les
rapports temporaires d'agents restent ignorés ; aucun document de suivi n'est
ajouté hors de `refactor/`.

## 17. Critères de fermeture W5

W5 est fermée lorsque :

1. chaque famille `MaterialNode` de ce document est soit rendue par la route
   commune, soit refusée uniquement sur une capability physique explicitement
   hors W5 ;
2. aucune route migrée ne reconstruit son propre descripteur/programme material ;
3. program structure et valeurs dynamiques sont séparées ;
4. stops, uniforms, images et children sont snapshotés et budgétés avant
   allocation ;
5. geometry, coverage, material et blend restent des axes indépendants ;
6. les 45 failures `DrawPoint` sont fermées ou un diagnostic externe à W5 est
   démontré et documenté ;
7. les gates ciblées sont vertes et la suite globale ne contient aucun nouveau
   nom de failure/error ;
8. fonts, codecs et GMs n'ont pas été utilisés pour gonfler le résultat ;
9. une revue finale Sol de la stack complète ne contient aucun finding
   Critical ou Important.

## 18. Limites explicitement reportées

- topologie et resolve AA4 natifs de W4 ;
- targets HDR et formats physiques autres que les targets sRGB 1× déjà admis ;
- décodage/encodage d'images ;
- font et glyph generation ;
- image filters, mask filters, layers, backdrop et effets spatiaux W6 ;
- frontend SkSL arbitraire ;
- convergence, score et rebaseline GM W7 ;
- retrait final du renderer legacy W8.
