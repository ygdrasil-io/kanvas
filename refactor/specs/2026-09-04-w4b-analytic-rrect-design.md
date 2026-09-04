# W4b — tranche verticale RRect analytique normalisée

**Statut :** validé
**Date :** 2026-09-04
**Branche de base :** `codex/w4b-analytic-rrect`, empilée sur W4a
**Référence amont :** `refactor/specs/2026-09-03-w4-geometry-coverage-stack-design.md` et `refactor/plans/2026-09-03-w4a-scalar-aa-rect-implementation-plan.md`

## 1. Décision

W4b ajoute une capacité GPU préparée verticale pour dessiner, dans une même
frame, des `Rect` et des `RRect` à partir du chemin analytique natif déjà
présent dans le shader `AnalyticShape`. La capacité exacte est :

`solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1`.

Cette tranche reste volontairement étroite :

- une peinture `SolidColor` prémultipliée ;
- un `SrcOver` normal ;
- une surface sRGB à un seul échantillon ;
- un transform axis-aligned admissible ;
- un scissor rectangulaire simple, entier et déjà déterminé par le plan ;
- un remplissage direct de `Rect` ou de `RRect`, y compris les rectangles
  de rayon nul dans une frame où au moins un `RRect` a été admis ;
- AA analytique scalaire de la géométrie, sans MSAA.

Le shader existant encode exactement l’aire de chevauchement pour un
`Rect` dont les rayons sont nuls. Pour un `RRect` à rayon non nul, son
chemin natif est une SDF (signed distance field, champ de distance signé)
avec rampe d’AA scalaire. W4b le réutilise tel quel : il ne prétend pas
introduire une nouvelle aire analytique exacte pour les coins arrondis.

## 2. Position dans la pile W3 → W4a → W4b

| Niveau | Unité planifiée | Géométrie | Rôle après W4b |
|---|---|---|---|
| W3 | `CorePrimitiveDraw` | forme rectangulaire déjà autorisée par le chemin direct | conserve la voie générique et les capacités antérieures |
| W4a | `AnalyticRectDraw` | `Rect` AA scalaire à bords fractionnaires | reste inchangé et demeure le lot rectangulaire spécialisé |
| W4b | `AnalyticRRectDraw` | une primitive `Rect` ou `RRect` normalisée; la frame a au moins une origine RRECT | ajoute la seconde voie préparée, native et scellée |

W4b ne généralise pas W4a en une abstraction polymorphe commune. Les deux
familles restent des branches sœurs : leurs garanties de sélection, de données préparées
et de matériel sont semblables, mais W4b transporte une forme à quatre coins
et doit préserver une provenance `RRECT`. Cela évite de modifier la
classification W4a ou de faire passer discrètement des RRect par une voie
rectangulaire.

La chaîne de compilation de capacités garde l’ordre explicite suivant :

1. W3 reste disponible pour les entrées qui ne satisfont pas les invariants
   W4a ou W4b.
2. W4a reconnaît son sous-ensemble `Rect` AA existant.
3. W4b reconnaît son sous-ensemble mixte propre et produit uniquement
   `AnalyticRRectDraw`.
4. Une sélection W4b réussie ne peut être reclassifiée ultérieurement.

Le sens du fallback est donc uniquement celui de la sélection initiale de
capacité. Après émission d’un plan W4b, un échec de lowering, de preflight ou
de matérialisation est une violation d’invariant et doit échouer
explicitement, jamais basculer vers W3, W4a ou la route directe.

## 3. Admission, provenance et modèle de draw

### 3.1 Unité de plan

`AnalyticRRectDraw` est un draw planifié immuable. Une instance représente
exactement une primitive, donc une commande de scène planifiée. Elle porte :

- son ordinal dans l’ordre de peinture de la frame ;
- la géométrie device-space normalisée et déjà transformée ;
- la couleur solide prémultipliée ;
- le scissor simple déjà résolu ;
- les faits de pipeline, de bind group et de format déjà verrouillés ;
- la provenance `RECT` ou `RRECT` de cette primitive ;
- son slot et offset Uniform80, déjà compatibles avec les données du plan.

Il n’accepte pas de géométrie scene-space brute, de matrice utilisateur, de
clip arbitraire, de paint mutable, ni de décision de classification tardive.

Une frame W4b contient une liste ordonnée de `AnalyticRRectDraw`. Chaque
élément conserve sa provenance; un `Rect` est représenté par un
`RRectF32` canonique à quatre paires `(+0f, +0f)`, mais conserve
`DrawOrigin.RECT`. Une phase GPU ultérieure peut regrouper des éléments
compatibles, sans les réordonner. Ce regroupement est une optimisation de
soumission, pas l’unité `PlanDraw`; la correction `SrcOver` dépend de
l’ordre de la liste.

### 3.2 Règle d’admission mixte

W4b admet une frame candidate dont la liste de `AnalyticRRectDraw` contient
des `Rect` et des `RRect` seulement si toutes les conditions suivantes
sont vraies :

1. il contient au moins une primitive dont `DrawOrigin == RRECT` ;
2. chaque primitive est soit un `Rect`, soit un `RRect` de remplissage
   direct ;
3. chaque primitive produit, après transformation axis-aligned et
   normalisation W4b, une boîte finie de largeur et hauteur strictement
   positives ;
4. la peinture est une couleur solide prémultipliée et le blend est
   `SrcOver` ;
5. le clip effectif est un unique scissor rectangulaire entier, fini et non
   vide ;
6. la cible est sRGB, mono-échantillon, avec le format et le pipeline
   `AnalyticShape` déjà prévus ;
7. les calculs de budget physique ne débordent pas et respectent les limites
   de buffer et d’uniform offset du périphérique.

L’exigence « au moins une provenance RRECT » est volontaire. Une frame
candidate composée uniquement de `Rect`, même sérialisés avec quatre paires
de rayons nuls, est le domaine W4a ou W3 : W4b ne doit pas devenir une voie
de secours ni concurrencer la sélection rectangulaire existante.

Les `Rect` d’une frame W4b sont encodés comme un `RRectF32` canonique à
quatre paires `(+0f, +0f)`. Ils ne changent pas de provenance : leur
origine demeure `RECT`; seule la présence d’au moins un élément frère
`RRECT` autorise leur cohabitation matérielle.

### 3.3 Exclusions d’admission

W4b rejette, dès le compilateur de plan, les inverse fills, les clips RRect
ou path, les opérations booléennes de clip, les transforms rotation/skew/
perspective, les effets de paint, les gradients, images et shaders de paint,
les blend modes autres que `SrcOver`, les surfaces non-sRGB, les cibles
MSAA, les dessins non remplis, les formes dégénérées, les coordonnées non
finies et tout buffer excédant une limite device.

La tranche ne rend pas les clips plus permissifs et ne recalcule pas un
scissor. Un scissor déjà vide rend le draw non admissible; il ne devient pas
un packet « vide » à matérialiser.

## 4. Normalisation backend-neutral dans :math

### 4.1 Autorité

La normalisation de `RRectF32` est ajoutée dans `:math`, sur les
types géométriques publics communs, avec un résultat explicite
accepté/rejeté. Elle est backend-neutral : aucune référence à WebGPU, à un
pipeline, à une texture, à un bind group ou à une règle de shader n’y est
admise.

Cette fonction est l’unique autorité W4b pour :

- valider la finitude de la boîte et des huit composantes de rayon ;
- imposer des rayons non négatifs ;
- canoniser toute paire dont une composante vaut zéro en `(+0f, +0f)` ;
- réduire uniformément les rayons qui ne tiennent pas dans la boîte ;
- corriger les rares effets de rounding F32 qui réintroduiraient une somme de
  rayons supérieure à un côté ;
- produire quatre paires F32 ordonnées `TL, TR, BR, BL`.

Une implémentation de normalisation locale de renderer ou de surface ne peut
pas être appelée par W4b ni servir de seconde source de vérité. Les
normaliseurs historiques restent hors du périmètre de migration de cette
tranche.

### 4.2 Algorithme défini

Pour une boîte `[left, top, right, bottom]`, W4b procède dans cet ordre :

1. vérifier que les quatre bords sont finis et que
   `right > left`, `bottom > top`;
2. vérifier que les huit valeurs de rayon sont finies et `>= 0`;
3. pour chaque coin, si `rx == 0f || ry == 0f`, remplacer la paire par
   `(+0f, +0f)`;
4. effectuer en F64 les quatre contraintes de côté :
   `TL.x + TR.x <= width`, `BL.x + BR.x <= width`,
   `TL.y + BL.y <= height`, `TR.y + BR.y <= height`;
5. prendre le minimum de `1` et des quotients positifs nécessaires aux
   quatre contraintes, puis multiplier les huit composantes par ce facteur
   commun en F64;
6. convertir les valeurs finales en F32;
7. après conversion, vérifier de nouveau les quatre contraintes en F64 à
   partir des F32 obtenus. Si une somme les dépasse uniquement à cause du
   rounding, réduire d’un ULP F32, de façon déterministe, la composante
   positive qui rétablit la contrainte; répéter seulement jusqu’au respect de
   toutes les contraintes;
8. recanoniser les paires devenues nulles et retourner les rayons F32
   canoniques avec la boîte validée.

Les quotients de l’étape 5 ne sont considérés que lorsqu’une somme de rayons
est strictement positive. Les contraintes déjà satisfaites contribuent
`1`; une contrainte qui n’est pas satisfiable à cause d’une entrée invalide
a déjà été rejetée aux étapes précédentes. Le facteur est commun à tous les
coins : il préserve la forme relative et satisfait simultanément les quatre
côtés.

La correction ULP est une correction de représentation, non un second
algorithme de clamp. Elle ne peut ni augmenter un rayon, ni créer une valeur
non finie, ni laisser un `-0f`. Les égalités de bords sont admises; les
rayons canoniques nuls sont toujours écrits `+0f`.

### 4.3 Transformation et parité JVM/JS

La source est normalisée avant sa projection device-space. Ensuite,
`RRectF32.mapAxisAligned` applique la matrice axis-aligned admissible :

- les tailles de rayon sont multipliées par les valeurs absolues des échelles;
- les quatre coins sont permutés quand une échelle est négative;
- la boîte et les rayons obtenus sont de nouveau soumis au même validateur
  F32 W4b avant leur scellement dans le plan.

Cette seconde validation n’est pas une reclassification et n’utilise pas de
normaliseur renderer : elle garantit seulement que les faits device-space
déjà sélectionnés restent représentables.

Les opérations de somme, quotient et facteur de réduction sont explicitement
F64 afin d’éviter qu’une différence de précision intermédiaire JVM/JS ne
change l’acceptation. Les valeurs d’interface, les rayons stockés et les
paquets sont F32. Les comparaisons de finitude, de signe, de zéro et les
corrections ULP doivent être définies sur les résultats F32 communs.

Les tests de `:math` portent les mêmes vecteurs sur JVM et JS : cas
admissibles, rayon à composante nulle, rayons excessifs, bord exactement
saturé, nécessaire correction ULP, `-0f`, NaN, infini, boîte dégénérée et
réflexions X/Y/XY. La parité attendue est l’égalité des bits F32 ou une
égalité explicitement définie pour NaN rejeté; aucun test ne dépend d’une
classe privée ou d’une méthode de backend.

## 5. Responsabilités par module

| Module | Responsabilité W4b | Interdit |
|---|---|---|
| `:math` | types F32, normalisation canonique, mapping axis-aligned et résultats de validation communs | API GPU, limites device, scissor, shader |
| `:render-ir` | conserver `GeometryNode.RRect`, `DrawOrigin.RRECT` et l’ordre sémantique de scène | normaliser des rayons, choisir une capacité GPU |
| `:gpu-plan` | reconnaître l’enveloppe W4b, normaliser via `:math`, fixer draw/budget/origines/faits device et émettre `AnalyticRRectDraw` | accès au device, buffer allocation, fallback tardif |
| `:gpu-renderer` | lowering frère, preflight, allocation, écritures Uniform80, soumission `AnalyticShape` et contrôles d’invariants | re-normaliser, retransformer, reclassifier, recalcule de scissor |
| `:kanvas` | capturer `DrawRRect` dans l’IR et ouvrir W4b seulement sur les surfaces compatibles | codec/font, adaptation Skia spécifique, route de fallback déguisée |

Le `:render-ir` existant reste la source de la provenance. La capture
`:kanvas` doit préserver `RRECT` jusqu’au plan; aucun helper de surface ne
doit reconstruire un RRect en Rect ou dériver la provenance depuis les seuls
rayons.

## 6. Encodage GPU, ABI et ressources

### 6.1 ABI stable

W4b réutilise l’ABI native `Uniform80` de `AnalyticShape` sans la modifier.
Pour chaque instance, les 80 octets ont les champs et offsets suivants :

| Octets | Champ | Contenu W4b |
|---:|---|---|
| 0..7 | `target_size` | taille de cible F32 |
| 8..15 | `anti_alias_radius` / padding | rayon AA scalaire, padding zéro |
| 16..31 | `premul_color` | couleur solide prémultipliée |
| 32..47 | `bounds` | `left, top, right, bottom` device-space |
| 48..63 | `radii0` | `TL.x, TL.y, TR.x, TR.y` |
| 64..79 | `radii1` | `BR.x, BR.y, BL.x, BL.y` |

Les quatre paires sont déjà normalisées F32. Pour un `Rect` dans une frame
W4b, `radii0` et `radii1` sont tous nuls positifs; le shader suit alors
son chemin de couverture rectangulaire exacte. Pour un `RRect`, les coins
non nuls empruntent sa branche SDF existante.

W4b ne réutilise ni l’ancienne ABI de 64 octets du dispatch RRect, ni des
uniforms de rayon séparés, ni un shader legacy qui recalcule bounds/scissor.

### 6.2 Cinq ressources et comptabilité physique

Un graphe de frame W4b déclare exactement les cinq `PlanResource` logiques
de W4a. Le pipeline et le bind group sont des faits scellés du draw et de la
soumission; ils ne sont pas des `PlanResource` et ne remplacent jamais le
staging de readback.

| # | Rôle `PlanResource` | Kind et usages | Taille planifiée | Durée de vie |
|---:|---|---|---|---|
| 1 | `LogicalTarget` | texture 2D, render attachment, copy source | `4 × width × height` | `[0, 2)` |
| 2 | `ReadbackStaging` | buffer, copy destination, map read | `alignUp(4 × width, 256) × height` | `[1, 2)` |
| 3 | `VertexData` | buffer, vertex, copy destination | capacité de pool réservée | `[0, 2)` |
| 4 | `IndexData` | buffer, index, copy destination | capacité de pool réservée | `[0, 2)` |
| 5 | `UniformData` | buffer, uniform, copy destination | capacité de pool réservée | `[0, 2)` |

Pour `N` primitives :

```
vertexBytes  = 32 * N
indexBytes   = 24 * N
uniformStride = alignUp(80, minUniformBufferOffsetAlignment)
uniformBytes = uniformStride * N
```

La mémoire effectivement réservée est la capacité des trois buffers poolés,
pas seulement leurs tailles utiles. Les budgets W4b transportent donc les
besoins logiques et les capacités physiques arrêtées à la planification, avec
vérification `N > 0`, absence d’overflow, offsets alignés et capacité
suffisante. Avec `Render` à l’index 0 et `Readback` à l’index 1, le pic
à l’index readback est obligatoirement la somme checked des cinq ressources :

```
targetBytes + readbackBytes + vertexCapacityBytes +
    indexCapacityBytes + uniformCapacityBytes
```

Le target, VertexData, IndexData et UniformData vivent sur `[0, 2)`;
ReadbackStaging vit sur `[1, 2)`. Les cinq ressources restent vivantes
jusqu’à la dernière passe qui les consomme. Les buffers poolés sont retournés
seulement après completion/readback; aucun buffer scratch de normalisation ne
survit à `[0, 2)`.

Les sommets et indices peuvent rester la topologie quad existante. Leur
réutilisation ne dispense pas W4b de comptabiliser une instance par primitive
ni de conserver l’offset Uniform80 correspondant à l’ordre de dessin.

## 7. Lowering, preflight et matérialisation scellés

W4b ajoute des frères scellés des composants W4a :

1. un lowerer W4b transforme uniquement `AnalyticRRectDraw` en demande de
   tâche préparée W4b;
2. un scratch W4b détient uniquement les faits device-space déjà scellés,
   les offsets, les tailles et les références autorisées;
3. un assembler W4b construit la tâche de frame `AnalyticShape`;
4. le preflight W4b valide exhaustivement les invariants du paquet, des cinq
   ressources, de l’alignement Uniform80, du scissor, des limites device et
   du format;
5. le materializer W4b écrit sommets, indices et Uniform80, puis crée la
   soumission avec le pipeline et bind group prévus.

Le type de demande, le scratch, le packet préparé et le payload W4b sont
scellés à la famille W4b. Une tâche W4a ne peut accepter un RRect; une tâche
W4b ne peut recevoir une primitive non normalisée ou une provenance absente.
Les adaptateurs de limite de couche doivent être explicites et à sens unique.

Il est permis d’introduire une autorité « planned RRect » qui signe
uniquement les faits déjà présents dans `AnalyticRRectDraw`. Cette autorité
ne fait ni transformation, ni normalisation, ni choix de shader; elle empêche
simplement une API renderer de recevoir une géométrie arbitraire.

Les règles suivantes sont non négociables après le plan :

- aucun appel au normaliseur historique du renderer;
- aucun appel à une route native directe générique;
- aucune reclassification `RRect → Rect` ou `W4b → W4a/W3`;
- aucun fallback si une ressource ou un invariant manque;
- aucun recalcul de scissor, de bounds, de rayons ou de transform;
- aucun élargissement silencieux d’une capacité par « best effort ».

Une incohérence doit être observable comme refus de compilation de capacité
avant création du plan, ou comme erreur d’invariant préparé après création du
plan. Les deux chemins gardent le diagnostic nécessaire, sans transformer une
erreur en image différente.

## 8. Sémantique de pixels et oracle

### 8.1 Contrat de rendu

Chaque primitive W4b est évaluée dans l’ordre planifié. Sa couverture est
multipliée à la couleur prémultipliée; le résultat est composé par
`SrcOver` dans l’espace linéaire prémultiplié. La target RGBA8 sRGB stockée
et quantifiée est relue comme destination par chaque draw suivant : la
conversion sRGB et la quantification interviennent donc entre les primitives
aux frontières normales de cette target. Une target intermédiaire de précision
différente exige une nouvelle capability et un oracle distincts ; W4b ne peut
pas la réutiliser silencieusement.

Pour les rayons tous nuls, l’oracle W4b est l’aire de chevauchement exacte
du pixel et de la boîte rectangulaire. Pour les RRect à au moins un rayon
non nul, l’oracle de W4b reproduit indépendamment l’équation SDF et la rampe
d’AA du shader natif; il ne doit pas appeler le shader, le materializer ni
les helpers privés de production.

Les scènes d’oracle couvrent au minimum :

- une primitive opaque et une primitive alpha partielle;
- superposition de deux primitives dans les deux ordres;
- un Rect et un RRect dans la même frame;
- quatre rayons asymétriques;
- rayons ramenés à la limite de la boîte;
- échelles axis-aligned positive et réflexions X/Y/XY;
- scissor qui coupe une primitive;
- pixels de bord, coins et intérieur.

### 8.2 Stratégie de test

Les tests W4b sont comportementaux :

- `:math` vérifie le contrat public de normalisation et la parité JVM/JS;
- `:gpu-plan` vérifie admission/rejet, préservation de l’ordre, provenance,
  faits scellés et budget public;
- `:gpu-renderer` vérifie les pixels de la capacité W4b contre l’oracle
  indépendant, y compris composition et scissor;
- `:kanvas` vérifie que `DrawRRect` est capturé et que la voie W4b est
  sélectionnée seulement lorsque l’enveloppe est satisfaite.

Les tests ne sont pas des tests d’infrastructure ni de forme du code. Sont
interdits : assertions de source-shape, reflection, méthodes privées,
compteurs d’appels, ordre d’appels interne, inspection d’implémentation ou
tests qui ne font que prouver l’existence d’une classe. Une assertion sur
l’erreur d’invariant est admise seulement si elle observe le contrat public
de rejet, pas un détail d’implémentation.

La baseline de W4b est non-GM : vecteurs géométriques publics, oracle CPU
indépendant, lectures de pixels et scénarios de surface ciblés. Les tests
GM/Skia ne sont pas exécutés dans W4b.

## 9. Critères d’admission

Un changement relève de W4b seulement s’il satisfait tous les critères :

1. la scène contient au moins un RRect d’origine `RRECT`;
2. le sous-ensemble géométrique et de paint correspond exactement à
   `solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1`;
3. les RRect sont normalisés par l’API `:math` F32 définie ici, avec résultat
   JVM/JS conforme;
4. la transformation est axis-aligned et les données device-space sont
   revalidées avant scellement;
5. le plan contient tous les faits nécessaires au renderer, les cinq
   ressources et le budget physique;
6. Uniform80, son stride aligné et les offsets sont dans les limites device;
7. le lowerer, preflight et materializer W4b sont tous disponibles comme
   frères scellés;
8. l’oracle de pixels W4b couvre le scénario demandé sans GM.

Toute condition absente retire la scène de W4b avant production du plan. Elle
ne donne pas l’autorisation de rendre une approximation via un autre chemin
après qu’un plan W4b a été produit.

## 10. Critères de sortie

W4b est terminé lorsque :

1. `:math` expose la normalisation F32 backend-neutral, testée sur JVM et JS
   avec les mêmes vecteurs;
2. les scènes admissibles produisent un `AnalyticRRectDraw` ordonné et
   scellé, tandis que les scènes hors enveloppe sont rejetées par la
   sélection de capacité;
3. les listes de draws Rect+RRect ne sont admises que si elles comportent au
   moins une provenance `RRECT`;
4. le renderer matérialise exactement les cinq `PlanResource`, emploie les
   trois buffers de données avec ABI Uniform80 et stride aligné, et ne
   substitue jamais pipeline/bind group au staging;
5. preflight et matérialisation refusent toute divergence de fait planifié
   sans reclassification ni fallback;
6. les pixels W4b concordent avec l’oracle non-GM pour les scénarios de
   géométrie, AA, scissor et `SrcOver` définis;
7. aucun fichier de production ou de test hors du périmètre W4b ne sert à
   masquer une non-conformité, et aucun test GM/Skia n’est exécuté par cette
   tranche;
8. les vérifications de style, compilation et tests ciblés décidés dans le
   plan d’implémentation passent sans ajout de test d’infrastructure.

## 11. Dette suivie — SDF native contre aire analytique Skia

La SDF native utilisée pour les RRect non nuls n’est pas l’aire analytique
exacte utilisée par Skia. Cette différence est une dette explicitement suivie,
pas une ambiguïté masquée par W4b.

Un nouveau shader de couverture RRect ne sera envisagé que si de futurs tests
d’intégration Skia montrent une divergence matérielle. Il est interdit de
masquer cet écart par une tolérance plus large, une baisse de seuil de
similarité ou une modification opportuniste de baseline. La réévaluation
naturelle de cette dette est W7, lorsque les tests d’intégration Skia
appartenant à cette phase pourront fournir le signal de décision.

W4b n’exécute donc ni GM, ni suite Skia, ni génération de rendu de référence.
Cette absence est intentionnelle : elle isole le contrat natif et son oracle
non-GM avant toute décision de convergence avec Skia.

## 12. Hors périmètre ferme

W4b n’ajoute ni ne modifie :

- font, text, glyphes ou toute dépendance de font;
- codec, décodage/encodage d’image ou asset externe;
- GM, baseline GM, exécution Skia ou dashboard Skia;
- `jpg-color-cube`;
- MSAA, HDR, formats non-sRGB, blends étendus ou paint non solide;
- clip complexe, perspective, rotation, skew, path, inverse fill ou
  géométrie non rectangulaire;
- nouveau shader RRect, changement d’ABI de shader ou nouveau pipeline;
- tests d’infrastructure, source-shape, reflection, privé ou call-count.

La tranche suivante qui voudrait élargir l’un de ces points devra présenter
une nouvelle capacité nommée, ses critères d’admission, son oracle et ses
ressources; elle ne peut pas étendre implicitement W4b.
