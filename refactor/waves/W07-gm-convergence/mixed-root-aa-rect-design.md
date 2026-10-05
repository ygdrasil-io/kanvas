# W7 — Rect STROKE AA dans une frame racine mixte

## Décision et diagnostic

Base `1cd04aa77096eb2ae73b76ccc97743ae6388c671`, parent draft #2419.
Le pilotage est délégué par l'utilisateur. Diagnostic Terra puis avis ciblé
Astra : les 14 premiers refus `unsupported.stroke.rect_anti_alias` ne sont
pas 14 gains promis. `alphagradients` est le témoin GM prioritaire : ses
remplissages en dégradé empêchent la route standalone entièrement solide.
Le lowerer Prepared à quatre bandes est hard et doit conserver son refus AA.
La fidélité polygonale de `circle_sizes` reste ouverte : rejeu actuel
94,342041 %, l'ancienne expérience 1/16px ayant perdu deux autres rendus.

Lot architectural borné : réutiliser la projection locale Rect→Path de W4d,
le contour stroke produit par math, la source `AaResolvedColor` MSAA4/resolve1
par occurrence et le composite W6 immédiat vers la racine 1×. Pas de layer
synthétique, nouveau shader, quatre bandes AA ni refonte générale du router.

## Domaine nouveau

Frame sans layer, Picture ni filtre. Chaque draw est un Rect d'origine RECT,
SrcOver, sans effet/blender/colorFilter/pathEffect, avec transform axis-aligned
fini non singulier et clip vide ou DeviceRect hard entier. Les strokes sont
AA, plain solid (shader null), STROKE seulement, width0 hairline ou positive
finie, join MITER avec miterLimit fini ≥2. Les autres draws sont FILL, solides
ou dégradés linéaires dans le domaine déjà admis par W3/W4a. Au moins un stroke
et un sibling LinearGradient sont nécessaires. Pas de nouvelle admission
Image/RRect/Path/shader sur stroke, ni STROKE_AND_FILL, skew/perspective ou clip
complexe. Ne pas détourner les frames standalone entièrement solides.

Préflight sémantique complet puis sélection de chaque segment avant émission
d'un candidat racine; réutiliser ces candidats. Une frame hors domaine garde
son routage historique. Une fois propriétaire, tout refus matériau, numérique,
resource ou capability reste terminal; jamais de retry legacy après plan.
Les branches layer/W6b et leur ownership restent inchangées. Aucune promesse
nouvelle sur un stroke complètement transparent/offscreen : vérifier leur refus
propre et transactionnel, sans prétendre à un rendu obtenu.

## Producteurs et consommateurs

`W6aLayerPlanCompiler.select` reçoit un domaine racine explicite, indépendant
des marqueurs layer/filter. La source Rect AA a un opt-in propre dans
`W4dGeneralPathPlanCompiler` : conserver DrawOrigin.RECT, la peinture originale,
le style STROKE et `PathDrawGeometry.Stroke`; ne pas falsifier FILL/PATH.
`PlanW4dAaSourceBindingV1` encode explicitement cette variante et le fill mesh
de `copyFillGeometryF32()`, sans cast forcé ni suppression d'autorité. Préserver
l'encodage historique Fill. Les ressources, samples, stencil, charges, resolves,
seals et capacités restent vérifiés par les mêmes consommateurs natifs.

Le gate Surface et l'ordre des compilateurs paraissent déjà suffisants : ne
pas les changer sans RED causal et arbitrage documenté. Le coût conservateur
plein viewport par source est accepté; aucun pooling ni hausse de budget.

## Validation et limites

Tests publics : mélange réel sans layer, pixels littéraux de trou/anneau,
alpha128 full et demi-coverage, ordre de siblings, translation/scale/hairline,
clip hard, second rendu identique, Render/Readback. Un budget exact B/B−1 doit
être dérivé des ressources avant le premier rendu de sa fixture, jamais du peak
produit; refus avec sentinel intact puis récupération sur la même Surface.
Les coins full-coverage vérifient l'absence de couture; ne pas inventer une
position de sample GPU pour un coin fractionnaire.

Mesurer ensuite les mêmes 631 identités et comparer au snapshot #2419, dont
les 197 anciennes images. Les diagnostics déplacés ne sont pas des gains.
Les performances sont des observations, pas un benchmark. W7 reste ouvert.

## Contraintes globales

- Fonts, codecs, external decoding et `jpg-color-cube` restent hors périmètre.
- Aucun changement de GM/adaptateur, référence, seuil, exclusion, budget cap, enveloppe numérique ou contrôle d'autorité pour faire passer un cas.
- Aucun nouveau test d'infrastructure : Surface public, pixels, Render/Readback, second rendu, refus/sentinel/récupération.
- Les objets géométriques restent dans math, nomenclature I/F32/64; aucun nouveau type géométrique n'est prévu.
- Un seul processus Gradle/GPU à la fois, terminal réel avant handoff.
- Suite globale connue rouge/incomplète : une tentative bornée, échecs et warnings rapportés; draft empilée seulement, ni merge ni clôture W7.
