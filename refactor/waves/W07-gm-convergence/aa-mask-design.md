# W7 — couverture AA filtrée

## Objectif et baseline

Raccorder un Path AA, fill solide SrcOver à `MaskFilter.Blur(NORMAL)` au
niveau root. La couverture géométrique doit rester indépendante de la peinture.
Le témoin GM est `blur2rects` ; le rendre ne prouve pas la parité visuelle.

Base : `a21bb6472ac7561aa288c79ff19969b7cfca10f4`, draft #2418.
Les 22 tests publics W6bMaskBlur et W7RootAA passent sur cette base.
`blur2rects` refuse `w6a.layer.unsupported_child` en 113 ms. Archives :
`/private/tmp/kanvas-w7-aa-mask.9OewPc/{baseline,baseline-gm}`.

## Contrat

`géométrie blanche AA4 → resolve couverture 1× → blur X/Y → NORMAL →
matériau W5 × couverture → composite SrcOver parent`

La source blanche vaut `(1,1,1,1)` avant couverture, même pour une peinture
d'alpha zéro. Le resolve contient `alpha=C`, jamais `C×paintAlpha`.
La couleur/opacité de la peinture est appliquée une seule fois, après blur,
sur tout le domaine de sortie (halo compris). Ne pas rerasteriser le contour
pour matérialiser la couleur : cela couperait le halo.

Créer un binding frère typé `PlanW4dAaCoverageSourceBindingV1`, distinct de
`PlanW4dAaSourceBindingV1` (couleur). Mutualiser les faits internes de géométrie,
MSAA4, stencil et resolve ; ne pas recopier une seconde chaîne native complète.
Le nouveau contrat possède sa capability et sa native recipe. Il ne transforme
ni `PathAaResolvedColor` en masque, ni une source Path en `PictureAlphaSourceV1`.
`FilterCoverageSourcePass` doit porter une troisième variante exclusive :
couverture AA résolue, distincte du raster 1× et de l'alpha Picture.

La texture single-sample `CoverageSource` est le vrai resolve du producer AA.
Un FilterCoverageSourcePass possède une seule passe native MSAA4, avec un groupe
de commandes direct ou deux groupes stencil producer puis cover. Clear transparent
et stencil0 à l'ouverture, test/reset au cover, resolve seulement à la fin.
Ce contrat réemploie la géométrie/pipelines W4d, pas les deux passes couleur
W4d et leurs load/store séparés. Il n'utilise pas RenderPassSegment.
Les groupes native directs ou stencil producer/cover restent authentifiés :
command identity, géométrie, blanc canonique, V/I/U, extent/origine, format,
sample count, usages, clear/load/store, profondeur/stencil, groupe atomique,
resolve et sampling consommateur appartiennent au contrat gelé avant allocation.
La chaîne couleur AA existante conserve son composite adjacent et ses validations.
Un commun interne ne donne pas à la couverture les droits d'une source couleur.

## Domaine et limites

Root seulement, Path fill WINDING/EVEN_ODD, solide, AA, SrcOver, NORMAL,
sigma fini strictement positif dans l'enveloppe existante, sans shader,
colorFilter, imageFilter, pathEffect ni destination-read. Transformations
déjà admises par W4d ; clip vide ou rectangle device hard intégral.
La demande W6b et ses halos déterminent le domaine du producer : origine device
explicite, géométrie localisée une seule fois, taps hors contenu transparents.
Le clip terminal ne doit pas tronquer prématurément les contributions au blur.

Les AA filtrés en layer/Picture, autres styles de blur, strokes et blends,
clips complexes/inverses et retrait du legacy restent ouverts. Ce lot ne retire
pas globalement le garde `!ownsW6b` des sources couleur AA ordinaires.
Deux occurrences de couverture ne partagent pas leur état stencil ou resolve.

## Preuves publiques attendues

- Anti-fallback HARD_EDGE : triangle fractionnaire, sigma 0,1. Les poids voisins
  gaussiens valent au plus `exp(-50)` ; le blur seul ne produit aucune fraction
  RGBA8 visible. Sur plusieurs phases subpixel, exiger une arête d'alpha strictement
  entre 0 et 255, sans supposer les positions exactes des quatre samples GPU.
- Halo : sigma 1,5, triangle suffisamment grand, intérieur opaque, pixels hors
  triangle mais dans le support non transparents, extérieur du support nul.
- Alpha une fois : blanc alpha128 sur noir opaque, grand intérieur loin de
  l'arête, RGB188 (tolérance 1), alpha255 ; alpha0 laisse le fond inchangé.
- Stencil : anneau à trou, contours WINDING opposés, sigma2,3, seconde occurrence
  translatée avec une phase 0,25 ; anneau, trou et halo interne/extérieur vérifiés.
- Mapping : forme partiellement hors viewport, translation non nulle et clip
  terminal hard ; source extérieure pouvant contribuer à l'intérieur du clip.
- Chronologie : deux occurrences disjointes autour d'un sibling hard coloré ;
  pas de fuite de source/stencil. Second rendu identique byte à byte.
- Budget : somme B des allocations finales explicitement dérivée des dimensions,
  samples et floors, écrite avant création de Surface ; B passe, B−1 refuse
  `w6b.filter.frame_budget_exceeded`, sentinel intact puis discard/récupération.
  Un fixture direct et un stencil prouvent aussi le coût du depth-stencil4.
- Refus hors scope : fixtures explicites, diagnostic exact observé avant patch,
  sentinel intact et récupération sur la même Surface.

Les oracles sont indépendants du planner et des pixels du renderer. Les positifs
exigent Render/Readback ; un refus de capability ou un GPU unavailable ne vaut
jamais un succès. Un contrôle HARD_EDGE et les sources AA couleur existantes
restent dans les validations proches. Aucun oracle ne déduit sa cible d'une
autre image GPU ni n'ajuste sa tolérance à la sortie obtenue.

## Global Constraints

- Fonts, codecs, external decoding et `jpg-color-cube` restent hors périmètre.
- Ne modifier ni fixture GM, adaptateur GM, référence, seuil, exclusion, budget cap, enveloppe numérique ou contrôle d'autorité pour faire passer un cas.
- Aucun nouveau test d'infrastructure : pixels publics Surface, Render/Readback, second rendu, refus/sentinel et récupération uniquement.
- Les types géométriques appartiennent à math avec nomenclature I/F32/64 ; aucun nouveau type géométrique n'est prévu.
- Un seul processus Gradle/GPU à la fois, terminal réel avant handoff ; tous les échecs et warnings restent rapportés.
- Mesurer les mêmes 631 identités avec timeout 30 s ; ne pas présenter un diagnostic déplacé comme un gain de rendu.
- Suite globale connue rouge/incomplète : publication draft empilée sur #2418 seulement, sans merge ni clôture W7.

## Pilotage

Le pilotage délégué couvre ce contrat borné. Terra réalise l'implémentation,
Sol les revues ; Astra intervient ici sur le choix architectural natif.
La stratégie retient le binding frère plutôt qu'un mode permissif sur la source
couleur : coût supplémentaire de raccordement, séparation sémantique explicite.
La restriction root/NORMAL/solide réduit le lot vérifiable, pas l'objectif global.
