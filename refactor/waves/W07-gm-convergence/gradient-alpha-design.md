# W7 — politique alpha du gradient linéaire

Base `c80e5b56d1812e163f0cf2d0a13e3ae9bb660d14`, branche
`codex/w7-gradient-alpha-mode`, future draft empilée sur #2420.
Décision de pilotage dans la délégation utilisateur, après l'ordre recommandé
par Astra dans [l'audit](alphagradients-audit.md). Travail architectural ciblé,
pas une clôture W7 ni une modification implicite de la composition de Surface.

## Objectif et limites

Exposer la sémantique manquante de la seconde colonne `alphagradients` par une
capacité publique réelle, avec preuves et pixels natifs. Le lot ne corrige
pas le GM et ne prétend pas résoudre l'écart 191/205 de composition.

- `GradientAlphaMode { STRAIGHT, PREMULTIPLIED }` dans paint et dans l'IR neutre.
- `Shader.LinearGradient.alphaMode`, dernier paramètre, défaut `STRAIGHT`.
  Même champ dans `MaterialNode.LinearGradient.of` et le DSL linéaire.
- Nouveau mode admis pour LinearGradient, espace effectif SRGB, tile CLAMP.
  Pas d'extension publique aux autres familles ou aux espaces polaires.
- Utiliser les routes géométriques/wrappers déjà admis par le pipeline V4 ;
  ne pas ouvrir une nouvelle route de rendu pour contourner un refus.
  Tout chemin incapable de conserver la politique refuse, jamais de downgrade.
- Sémantique du mode par défaut, sources de composition, images et leurs conventions,
  filtres, snapshots, formats de sortie et domaine linéaire restent inchangés.
- Aucun GM/adaptateur, référence PNG, seuil, score historique, exclusion,
  plafond de budget, enveloppe numérique ou contrôle d'autorité modifié.
- Fonts, codecs/décodage externe et `jpg-color-cube` restent hors périmètre.
- Pas de tests d'infrastructure : Surface publique, pixels natifs, Picture
  publique, readPixels/sentinel/refus/récupération et deuxième rendu.
- Géométrie dans math ; nomenclature I/F32/64 pour ses valeurs et types.

## Architecture et fidélité

Le mode est capturé immuablement, restauré par les adaptateurs paint↔IR et
transporté jusqu'à `PreparedSourceDefinitionV4`. Identités canoniques,
programme, définition, authentification, rebasing et graph binding distinguent
STRAIGHT et PREMULTIPLIED. Les identités STRAIGHT historiques restent stables
quand le mode n'ajoute aucune sémantique.

Une seule autorité calcule le nouveau mode : le graphe de
`ColorOperationGraphV1.GradientStopSelection`, instancié par la preuve et
`W5fColorOperationEmitterV1`. PREMULTIPLIED sélectionne explicitement V4 ;
les raccourcis V1/V2 et les descripteurs legacy qui n'ont pas de politique
alpha doivent refuser. Exception sans interpolation : après validation des
gardes alpha/domaine/tile, la réduction existante d'un stop unique en Solid
est autorisée, car elle conserve exactement sa couleur et son alpha.
Les stops physiques gardent leurs valeurs straight
et leur format de 32 octets : pas de nouvelle représentation d'image ou slab.
Le changement de route V4 implique de comptabiliser ses vrais uniformes.
Deux modes peuvent partager un range physique dont les stops sont identiques ;
leur programme/définition/proof ne peut pas être confondu pour autant.
`GradientMetadata.recipeIdentity` reste l'identité de préparation des stops,
indépendante du mode ; ne pas l'utiliser pour distinguer les exécutions.
Un témoin public de budget mixte doit vérifier cette mutualisation.

Le mode n'est pas une variante d'espace colorimétrique. La sortie reste
linéaire prémultipliée, compatible avec la composition actuelle :

`P=(1−t)·C0·a0+t·C1·a1`, `a=(1−t)·a0+t·a1`,
`source.rgb=EOTF_sRGB(P/a)·a`, `source.a=a`, avec source nulle si a=0.

Ne pas calculer `EOTF(P)·a`, qui applique la mauvaise courbe et l'alpha deux fois.

### Recipe numérique retenue pour ce nouveau mode

Le cas général `/a` avec une simple garde a≠0 n'est pas suffisant pour la
preuve : près d'un stop transparent, a peut être subnormal. Le nouveau mode
définit donc explicitement un schedule F32 par classes exactes d'alphas :

1. Deux alphas nuls : quatre zéros.
2. Un seul alpha nul : RGB straight de l'extrémité non nulle et alpha
   interpolé ; zéro final lorsque cet alpha est nul. Il s'agit de la limite
   analytique premul, pas d'une promesse d'identité bit-à-bit avec une division
   exécutée par Skia.
3. Deux alphas positifs : P est la somme des deux produits pondérés premul.
   L'alpha utilise le calcul stable depuis le minimum : si a0≤a1,
   `a=a0+t·(a1−a0)`, sinon `a=a1+(1−t)·(a0−a1)` ; puis RGB=P/a.
   Les alphas publics sont issus de ColorARGB : tout alpha non nul est au
   moins 1/255 avant arrondi F32. Ce schedule permet une borne positive
   sans inventer d'epsilon et reste mathématiquement équivalent au mélange.
4. EOTF puis prémultiplication utilisent le graphe existant. Chaque garde
   est réellement lazy dans le graphe/WGSL. Aucun `select` exécutant une
   division interdite, clamp supplémentaire, élargissement d'enveloppe ou
   conversion d'un échec de preuve en pixels transparents.

Cette recipe est distincte dans les identités. STRAIGHT conserve son schedule.
Les bornes sont validées sur les opérations réellement émises, pas déduites
uniquement de l'équation réelle. Si le moteur de preuve existant ne certifie
pas ce schedule, l'implémenteur remonte le cas exact avant de changer la preuve
ou la recipe. Pas d'autorisation implicite d'ignorer le refus.

## Persistence et refus

L'archive IR actuelle est Picture 15 / schema 9. Écrire Picture 16 / schema 10
avec un identifiant stable explicite du mode, accepter les archives anciennes
comme STRAIGHT, rejeter les identifiants inconnus. La façade Picture doit
continuer à accepter la version 15 et reconnaître 16. Ce format interne
Picture n'est pas un chantier de décodage d'images externes.

Les combinaisons effectives PREMULTIPLIED × non-SRGB ou non-CLAMP échouent
avec `unsupported.material.gradient.alpha-mode`. Les refus géométriques,
budgétaires et numériques conservent leurs codes et leur caractère terminal.
Ni succès CPU simulé, ni fallback legacy perdant le mode.

## Prérequis natif borné : remplacement SRC direct

La fermeture CPU a montré que les témoins SrcOver sur blanc dépassent la
borne existante de deux codes adjacents, du fait de la composition et des
conversions fixed-function. Ne pas élargir l'enveloppe pour ces témoins.
L'oracle SRC sans couverture omet cette étape, mais le descripteur natif
actuel émet encore One/Zero. La précision de format cible reste permise par
[D3D11.3 §17.5](https://microsoft.github.io/DirectX-Specs/d3d/archive/D3D11_3_FunctionalSpec.htm) ;
ne pas supposer One/Zero équivalent numériquement à blending désactivé.

Après diagnostic ciblé Astra, le lot inclut un remplacement explicite
`blend=null` uniquement pour le chemin direct PremulSrc, coverage None,
single-sample, sans AA, masque ni clip analytique. Le domaine de composition
et l'équation SRC ne changent pas ; seule sa réalisation native devient
un remplacement sans calcul de blend. Pas de règle générique sur One/Zero,
ni de modification des routes AA/destination-read.

`GPUW5aHostColorTargetV1` et le template scellé doivent représenter fidèlement
l'absence de blending (pas un One/Zero fictif), jusqu'au cache et au
descripteur natif. `hostTargetV1` impose actuellement requireNotNull(blend) :
traiter ce couplage explicitement, sans retirer les contrôles d'authenticité
dans `composeW5aHostSourceV1`. Contrôler cette route par un SRC public existant
et un remplacement semi-transparent sur destination préremplie, RGB/alpha,
deux rendus. Ce prérequis est justifié par inspection et spécification ;
aucun RED natif antérieur n'est inventé si les pixels du Mac sont déjà conformes.

## Preuves publiques attendues

- Témoins causaux SRC source seule, couverture complète, AA désactivé,
  sans fond ; oracle destination Transparent/finalBlend SRC dans les deux modes.
  Blanc opaque → noir transparent, t=.5 : fermeture CPU STRAIGHT RGB91–92,
  PREMULTIPLIED RGB187–188, alpha127–128 dans les deux modes.
- Rouge alpha128 → bleu alpha64, t=.5 : fermeture CPU straight R/B80–81,
  premul R108–109, G0–1, B51–52, alpha96. RequireBounded avant le GPU,
  ensembles RGB disjoints puis pixels admis exactement par ces ensembles,
  Render/Readback et deux rendus. Pas d'égalité à un octet idéal unique.
  Les couples alternatifs192/96 et254/127 ont été examinés uniquement sur
  CPU et ferment aussi ; le premier128/64 suffit, aucun choix après mesure GPU.
  Les repères analytiques sur blanc205/255 et (228,207,212)/(218,207,218)
  restent utiles au diagnostic, mais ne sont pas ces témoins numériques bornés.
- Alphas nuls, nul à gauche/droite, alpha minimal 1, stops opaques, un stop,
  égalités/hard stops, extrémités clamp et dégénérescence gardent le contrat.
  Pour un axe linéaire dégénéré CLAMP, le graphe existant fixe t=1 : la
  couleur attendue est celle du dernier stop, et non une moyenne.
  La moyenne dégénérée n'est consommée que par REPEAT/MIRROR, refusés ici.
- 2/16/17 stops, ressources partagées straight/premul, ordre et mutations
  après capture ne confondent ni les ranges ni les identités.
- Rect, un Path fill déjà admis, wrappers SRGB/Opacity/coordonnées admis et
  un mélange racine avec Rect stroke AA : aucune perte du mode entre routes.
- Picture directe puis archive/replay conservent les pixels ; archive ancienne
  straight reste décodable. Capturer avant modification du writer une Picture15
  de 1×1 : fond blanc puis gradient straight blanc255→noir0, t=.5, sans AA ;
  son replay doit conserver le résultat voisin de205. Ne pas accepter
  silencieusement un decode null.
- Refus non-SRGB/non-CLAMP/legacy avant publication ; sentinel inchangé,
  récupération de la Surface après discard, puis deux rendus stables.
- Budget B/B−1 dérivé statiquement avant le premier essai, avec les vrais
  buffers V4, stops, targets/readback ; aucune recherche empirique de B.

## Validation et critère d'arrêt

Baseline fraîche : 15/15 contrôles sur c80e5b56d, Gradle 0, 13 s ; XML dans
`/private/tmp/kanvas-w7-alpha-mode.vpi7cG/baseline/xml`.
Tests nouveaux ciblés puis voisins, une globale bornée à 240 s, corpus
631/443 inchangé et comparaison appariée au snapshot parent. Le GM ne demande
pas encore le nouveau mode : aucun gain de son score n'est attendu ici.
Tout échec global antérieur reste nommé et distinct des nouveaux échecs.

Une tâche atomique, revue Sol de tâche puis revue de branche. Publication
draft seulement après examen des résultats ; W7 reste actif. Si la capacité
exige une modification de composition/images ou un assouplissement des
preuves, remonter le couplage démontré et réviser le design, pas élargir le lot.
