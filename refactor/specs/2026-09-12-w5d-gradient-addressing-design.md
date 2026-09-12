# Design W5d — adressage des gradients, matrices locales et tile modes

## 1. But et position dans la stack

W5d étend la tranche W5c sans changer son modèle de ressources : les gradients
`Linear`, `Radial`, `Sweep` et `Conical` déjà promus acquièrent les tile modes
`REPEAT`, `MIRROR` et `DECAL`, les wrappers `WithLocalMatrix` et `CoordClamp`,
sur les mêmes lanes Rect, RRect analytique, Path fill et Path stroke/hairline.

La stack est :

```text
codex/w5b-blends
  └── codex/w5c-gradients
        └── codex/w5d-gradient-addressing
```

W5d ne traite ni images, ni autre espace d'interpolation couleur, ni
`WithColorFilter`, ni `Blend` comme nœud source, ni runtime effects, ni nouvelles
familles géométriques. W5e, W5f, W5g et W5h conservent ces responsabilités.
Fonts et codecs restent hors périmètre.

## 2. Autorités normatives

Le contrat principal reste
`refactor/specs/2026-09-09-w5-material-graph-design.md`, sections 7 et 8. W5d
ne modifie pas la normalisation, l'interpolation straight sRGB, le stop buffer
ou les règles numériques de famille fermés en W5c.

Les sources Skia primaires épinglées servent de contrôle sémantique :

- la documentation des espaces de coordonnées définit la local matrix comme la
  transformation des coordonnées shader vers l'espace local de la géométrie :
  <https://skia.googlesource.com/skia/+/17d00f9241b3/site/docs/user/coordinates.md> ;
- `SkLocalMatrixShader` concatène la matrice du wrapper à la chaîne existante :
  <https://skia.googlesource.com/skia/+/7a2127711a40/src/shaders/SkLocalMatrixShader.cpp> ;
- `SkCoordClampShader` applique le transform courant, clamp X/Y, puis invoque
  son child ; un subset non trié est refusé :
  <https://skia.googlesource.com/skia/+/7a2127711a40/src/shaders/SkCoordClampShader.cpp> ;
- les tile modes et les bords transparents de `DECAL` suivent les gradients
  Skia : <https://skia.googlesource.com/skia/+/chrome/m89/src/gpu/gradients/GrGradientShader.cpp>.

En cas de divergence entre une optimisation historique et le design W5, le
design W5 est l'autorité du renderer Kanvas. Aucun test GM n'est requis pour
établir ce contrat.

## 3. Périmètre exact

| Axe | Admis en W5d | Reporté |
| --- | --- | --- |
| Sources | Linear, Radial, Sweep, Conical | Image W5e, autres sources W5g/W5h |
| Tile | CLAMP, REPEAT, MIRROR, DECAL | aucun mode inventé |
| Interpolation | SRGB | LINEAR/OKLAB/HSL/OKLCH W5f |
| Coordonnées | CTM, `WithLocalMatrix`, `CoordClamp`, homographie bornée | graph source général W5h |
| Lanes | Rect, RRect analytique, Path fill, Path stroke/hairline | Point(s), Text, Vertices, Mesh et cellules H W5h |
| Wrappers couleur | Opacity déjà promu | color filters, working color space, source Blend |

La grammaire admise est un gradient W5c entouré d'un nombre borné de nœuds
`Opacity`, `WithLocalMatrix` et `CoordClamp`, dans n'importe quel ordre. Les
Opacity conservent leur produit existant. Les nœuds de coordonnées conservent
leur ordre exact ; leur traversée ne dépend pas de la lane géométrique.
Les limites existantes restent `GraphLimits(maxDepth=64, maxNodes=4096)` et
sont préflightées par la capture avant toute copie ; W5d n'ajoute pas un second
compteur de profondeur concurrent.

Un gradient à un seul stop continue de suivre W5c : Solid pour
Linear/Radial/Sweep, indépendamment du tile ou des wrappers de coordonnées ;
Conical conserve deux stops identiques afin de préserver son masque de racine.

## 4. Modèle de coordonnées ordonné

### 4.1 Plan et binding

`MaterialCoordinatePlanV1` reste l'autorité W5c historique. W5d introduit
`MaterialCoordinatePlanV2`, capable de décrire une liste immuable
d'opérations :

```kotlin
public sealed interface MaterialCoordinateOperationV2 {
    public data class InverseMatrixF32(
        public val inverseF32: Matrix3x3F32,
    ) : MaterialCoordinateOperationV2

    public data class ClampRectF32(
        public val subsetF32: RectF32,
    ) : MaterialCoordinateOperationV2
}

public class MaterialCoordinatePlanV2 {
    public fun copyOperations(): List<MaterialCoordinateOperationV2>
    public val uniformByteSizeI64: Long
    public val canonicalIdentity: String
}
```

Ces types vivent dans `:gpu-plan`. Ils référencent les valeurs géométriques de
`:math:geometry` et les matrices de `:math:matrix` ; aucune copie de `Rect` ou
de `Matrix` n'est créée dans `:kanvas` ou le renderer. Les noms publics
numériques utilisent les suffixes `I32`, `I64`, `U32`, `F32` ou `F64`.

La program key contient la version et la séquence des tags
`InverseMatrix`/`ClampRect`, jamais les coefficients ou les bornes. Le binding
contient les valeurs copiées, leur byte layout et le sceau canonique. Deux
draws de valeurs différentes peuvent partager un programme ; deux topologies
différentes ne peuvent pas partager une pipeline incompatible.

### 4.2 Ordre d'évaluation

Pour un point device `pD`, un CTM `C` et des wrappers de l'extérieur vers
l'intérieur `Louter ... Linner`, la relation reste :

```text
MshaderToDevice = C * Louter * ... * Linner
pShader = inverse(Linner) * ... * inverse(Louter) * inverse(C) * pD
```

L'évaluation séquentielle part donc de `pD`, applique `inverse(C)`, puis les
inverses des wrappers de l'extérieur vers l'intérieur. Lorsqu'un `CoordClamp`
est rencontré, le point courant est clampé avant de poursuivre vers son child.

Exemple :

```text
WithLocalMatrix(T,
  CoordClamp(A,
    WithLocalMatrix(R, gradient)))

pD → inverse(C) → inverse(T) → clamp(A) → inverse(R) → gradient
```

Les matrices adjacentes peuvent être composées en F64 en conservant leur ordre,
puis inversées comme un segment. Elles ne sont jamais composées à travers un
`CoordClamp`. Deux clamps adjacents restent ordonnés : les fusionner en une
intersection serait faux lorsque leurs rectangles sont disjoints.

### 4.3 Validation et homographies

La composition et l'inversion utilisent `Matrix3x3F64` dans `:math:matrix`.
Chaque source `Matrix3x3F32` est copiée avant calcul. Sont refusées avant
`Ready` :

- matrice non finie ;
- segment singulier en F64 ;
- inverse dont une composante projetée en F32 est non finie ;
- taille uniforme, profondeur ou arithmétique de layout hors bornes.

Le subset `CoordClamp` doit être fini et trié (`left <= right`,
`top <= bottom`). Un axe vide, avec bornes égales, est valide et clampé sur
cette coordonnée ; un rectangle inversé est refusé.

Le planner prouve d'abord avec `WgslFloatEnvelopeV1` que les trois produits
scalaires homogènes ne débordent pas sur les bounds device propriétaires. Le
WGSL vérifie ensuite `w` et une borne conservatrice du quotient **avant** toute
division. Avec `QMAX = 2^126`, une composante `n / w` est admissible si `w` et
`n` sont finis, `w != 0`, et soit `abs(w) >= 1 && abs(n) <= QMAX`, soit
`abs(w) < 1 && abs(n) <= abs(w) * QMAX`. Cette multiplication ne peut pas
déborder et garantit un quotient fini avec marge. La division vit dans une
branche `if` exécutée uniquement lorsque X et Y satisfont cette borne ; elle ne
doit pas être placée dans les arguments évalués d'un `select`.

Si la preuve des produits scalaires échoue, ou si la garde de quotient échoue
pour le fragment — notamment `w == 0` — le nœud produit le point sûr `(0,0)` et
un bit de validité faux. Les nœuds suivants s'évaluent uniquement sur les
valeurs sûres, et le matériau final est masqué transparent. Une valeur non
finie ou indéterminée ne peut pas entrer dans `floor`, `atan2`, la recherche de
stops ou le blend.

Le domaine numérique combine les bounds device déjà propriétaires des lanes W4
avec toutes les opérations de coordonnées. Une homographie courante et bornée
est admise ; une enveloppe que `WgslFloatEnvelopeV1` ne peut pas fermer reste un
refus typé, sans élargissement empirique.

## 5. Tile graph commun

Le paramètre brut `t` est calculé par le graph W5c de la famille après toutes
les opérations de coordonnées, puis un unique nœud structurel de tile applique :

```text
CLAMP:
  t < 0  → bord gauche
  t > 1  → bord droit
  sinon  → t inchangé

REPEAT:
  t' = t - floor(t)              // domaine [0,1), entiers → 0

MIRROR:
  q  = t - 2*floor(t*0.5)        // domaine [0,2)
  t' = 1 - abs(q - 1)            // ...0→1→0...

DECAL:
  valid = 0 <= t && t <= 1
  t' = clamp(t, 0, 1)
  couleur finale masquée transparent si !valid
```

Les opérations `MUL_F32`, `SUB_F32`, `FLOOR_F32`, `ABS_F32`, comparaisons et
sélections sont présentes dans le graph typé et dans l'oracle indépendant.
Leurs schedules WGSL permis sont couverts ; aucune égalité de frontière ne
repose sur une division supposée exacte.

La recherche de stops reste `upper_bound - 1`. Pour CLAMP, les comparaisons
strictes hors domaine précèdent la recherche : `t == 0` et `t == 1` conservent
les hard stops normatifs. REPEAT mappe un entier exact sur zéro. MIRROR conserve
un sommet exact à un entier impair. DECAL garde les endpoints et rend seulement
le domaine strictement extérieur transparent.

Pour Sweep, `sweepFullCoverage` force le tile effectif CLAMP, même si le leaf
demande REPEAT/MIRROR/DECAL. Pour Conical, un fragment sans racine valide est
transparent avant tile et ne peut pas être réintroduit par CLAMP ou REPEAT.

La famille, le tile mode demandé, la version du tile graph et la topologie des
coordonnées entrent dans la structural key. Les stops, matrices, subsets,
ranges, opacités et scalaires numériques restent dans les bindings scellés.

## 6. Dégénérescences REPEAT/MIRROR/DECAL

La règle commune du design W5 est appliquée avant le tile par fragment :

- CLAMP : dernière couleur ;
- DECAL : transparent ;
- REPEAT et MIRROR : moyenne exacte du gradient normalisé.

La moyenne est l'intégrale des couleurs straight sRGB sur `[0,1]` :

```text
average = Σ 0.5 * (color[i] + color[i+1])
                  * (position[i+1] - position[i])
```

Les endpoints implicites sont déjà présents. Un intervalle de hard stop de
largeur zéro ne contribue pas. Pour ne pas introduire un résultat dépendant de
l'ordre de sommation, le planner calcule cette intégrale uniquement lorsque la
branche dégénérée REPEAT/MIRROR est atteignable, avec un accumulateur rationnel
binaire exact construit depuis les bits F32, puis projette chaque canal une
seule fois en F32 roundTiesToEven. Le résultat est un
`GradientAverageSrgbaF32` immuable, authentifié avec le range/slab, sérialisé
dans le binding uniforme et converti une seule fois vers linear-premul.

Ce calcul ne modifie pas `GradientStopBufferV1`, n'ajoute aucun buffer et ne
boucle pas sur les stops dans le fragment shader. Son coût CPU est payé
seulement pour un gradient uniformément dégénéré concerné.

Les exceptions W5 restent prioritaires :

- Sweep CLAMP avec leading segment utilise la première/dernière couleur selon
  son angle scellé ; Sweep full coverage utilise CLAMP ;
- Conical fully-degenerate CLAMP avec rayon partagé valide conserve le hard
  stop circulaire ;
- Conical sans racine valide reste transparent quel que soit le tile.

## 7. ABI, ressources et ownership

`GradientStopBufferV1` reste strictement inchangé : un storage buffer de frame,
stride 32 octets little-endian, champs réservés nuls, binding group 1 index 1.

Le material uniform devient versionné par structural program :

- valeurs W5c de famille et tuple numérique scellé ;
- header de range et flags ;
- moyenne dégénérée seulement pour les programmes qui la consomment ;
- séquence compacte des valeurs de coordonnées : 48 octets par inverse 3×3,
  16 octets par `ClampRectF32`.

Les offsets et `minBindingSize` sont dérivés d'un unique
`RawMaterialRequirements` checked. Les multiplications/additions utilisent I64,
puis sont validées contre `Int`, `maxUniformBufferBindingSize`, le nombre de
bindings et `frameLocalBudgetBytes` avant `Ready`.

W5d n'ajoute aucun handle natif. Le stop buffer, le material uniform et les bind
groups conservent les owners W5c ; les nouveaux structural IDs empêchent de
réutiliser une pipeline ou un bind group ayant un layout antérieur. Rollback,
completion, fermeture et quarantaine restent ceux du payload propriétaire
existant.

## 8. Admission et frontière legacy

Le candidate gate descend uniquement dans la grammaire W5d et vérifie :

- leaf gradient parmi les quatre familles ;
- interpolation SRGB ;
- tile mode parmi les quatre valeurs connues ;
- wrapper parmi Opacity/WithLocalMatrix/CoordClamp ;
- lane parmi les quatre lanes W5c ;
- profondeur, valeurs et layout potentiellement représentables.

La classification détaillée et les refus numériques restent dans `:gpu-plan`.
Une frame n'est propriétaire W5d qu'après seal complet des coordonnées, du tile,
du material, du blend et des ressources. Après ce point, aucun mapper/provider
legacy n'est accessible. Avant admission, les familles W5e–W5h et les
combinaisons hors grammaire gardent leur continuation planifiée.

Un refus W5d n'est jamais remplacé par CLAMP, identité, transparent ou rendu
legacy. Les codes publics stables sont :

| Condition | Code |
| --- | --- |
| matrice locale non finie | `unsupported.material.gradient.local-matrix-non-finite` |
| segment local singulier en F64 | `unsupported.material.gradient.local-matrix-singular` |
| inverse non projetable en F32 | `unsupported.material.gradient.local-matrix-unrepresentable` |
| subset CoordClamp non fini | `unsupported.material.gradient.coord-clamp-non-finite` |
| subset CoordClamp non trié | `unsupported.material.gradient.coord-clamp-unsorted` |
| layout uniforme ou budget dépassé | `resource.material.gradient.coordinate-uniform-budget` |
| domaine projection/tile non borné | `unsupported.material.gradient.numeric-domain-unbounded` |
| sceau/version/topologie incompatible | `schema.material.gradient.coordinate-plan` |

Une erreur native après ownership conserve le diagnostic précis de son étape
allocation/upload/pipeline/submit/readback ; W5d n'ajoute pas de code générique
qui l'écraserait.

La récupération publique suit W5c : après un refus pré-Ready, un rendu valide
est exécuté immédiatement sur le même runtime/backend. Une Surface distincte
n'est utilisée que lorsque le recording append-only ou `RenderConfig` immuable
l'impose, et cette nécessité est documentée.

## 9. Preuves publiques

Les tests de rendu utilisent uniquement `Surface`, `Canvas`, `Picture`, pixels,
diagnostics publics et exceptions publiques typées. Les valeurs math publiques
de `:math:geometry` et `:math:matrix` peuvent être testées directement. Sont
interdits comme preuves : réflexion, source-shape, compteurs, scopes privés,
packets, allocator, bind groups, nombre de buffers, faux adapter/device,
failpoints et tests d'infrastructure.

La gate W5d couvre au minimum :

1. les quatre tile modes sur chaque famille, avec intérieur, endpoints, entier
   positif et domaine supérieur à 1, plus les valeurs/entiers négatifs pour les
   familles dont le paramètre géométrique peut authentiquement les produire ;
2. hard stops aux frontières et à l'intérieur pour REPEAT/MIRROR/DECAL ;
3. la règle moyenne des dégénérescences Linear/Radial/Sweep/Conical, avec stops
   irréguliers et alpha non trivial ;
4. `DECAL` transparent hors domaine et aux fragments Conical sans racine ;
5. une local matrix non triviale sur les quatre lanes et quatre familles ;
6. deux matrices non commutatives, dans les deux ordres, avec pixels distincts ;
7. `WithLocalMatrix → CoordClamp → WithLocalMatrix` et l'ordre inverse, avec un
   témoin que le clamp n'a pas été déplacé ou fusionné ;
8. une homographie admise et un pixel `w == 0` transparent sans contamination
   des pixels voisins ;
9. matrices non finies, singulières, inverse non projetable, subset non trié,
   profondeur de graph existante et budget uniforme, suivis d'une récupération
   publique ;
10. mutation après capture des subsets/stops via `Picture`, et conservation de
    matrices immuables distinctes à travers l'enregistrement ;
11. une frame mixte combinant tile modes, wrappers, Opacity, blend W5b et les
    quatre lanes, avec ordre observable ;
12. authentic AA4/capability : pixels exacts si disponible, sinon skip/refus
    public précis ; aucune simulation.

Les attentes sont calculées par un oracle indépendant qui n'appelle pas les
factories du graph de production. Les fixtures ne deviennent gates que si
leurs enveloppes RGBA8 sont singleton ou deux codes adjacents. Une fixture
`Unbounded` est reformulée ou reste non-gate ; aucune tolérance empirique.

## 10. Vérification et exclusions

Les gates finales compilent `:math:matrix`, `:render-ir`, `:gpu-plan`,
`:gpu-renderer` et `:kanvas`, puis exécutent les tests publics W5d et les
régressions bornées W5c/W5b/W5a. Le target JS de `:math:matrix` est rejoué ; un
target JS `:kanvas` n'est exécuté que s'il existe authentiquement.

Aucun GM, dashboard, render regeneration, baseline Skia, intégration Skia,
font, codec ou `jpg-color-cube` n'est lancé. Les erreurs historiques des tests
d'infrastructure `gpu-plan`/`gpu-renderer` ne sont pas utilisées comme preuve et
ne sont pas corrigées dans W5d.

La clôture documente séparément : résultats XML, exit de commande Gradle,
skips AA4 authentiques, capability observée et crash natif post-assertions 133.
Elle conserve les gaps déjà suivis : collision de target IDs equal-extent
W5a/W5b, intervalle Conical B conservateur, ainsi que W5e/W5f/W5g/W5h.

## 11. Découpage d'implémentation proposé

1. Capture/admission immuable de la grammaire W5d et diagnostics de valeurs.
2. `MaterialCoordinatePlanV2`, composition/inversion F64 et local matrix Linear
   Rect avec preuve non commutative.
3. `CoordClamp` ordonné, homographie/validity mask et preuves de récupération.
4. Tile graph commun Linear pour CLAMP/REPEAT/MIRROR/DECAL et frontières.
5. Moyenne dégénérée exacte et règles spéciales Sweep/Conical.
6. Extension des quatre familles aux quatre lanes, sans nouveau chemin legacy.
7. Frame mixte, budgets/capabilities/ownership et documentation durable.
8. Deux reviews indépendantes, vérification controller et PR stackée vers
   `codex/w5c-gradients`.

Chaque tâche suit TDD avec un RED public réel avant production, un implementer
Astra et un reviewer Sol. Sol est réservé aux reviews. Les findings importants
sont corrigés par Astra puis re-reviewés de façon bornée par Sol.
