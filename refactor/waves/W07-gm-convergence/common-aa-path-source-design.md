# W7 — source couleur AA PATH commune aux scopes W6

4 octobre 2026. Base : draft #2447, renderer produit2485cfb. Lot
architectural limité à un contrat existant, sans nouvel algorithme AA.

## Autorité et intention

L'utilisateur délègue le pilotage et les changements W7, breaking changes
autorisés. Main prend les décisions de cette spec sous cette délégation ;
elle n'est pas présentée comme personnellement relue par l'utilisateur.
Exécution Subagent conservée, Luna pour implementation, Sol pour reviews ;
Astra réservée aux diagnostics/choix difficiles, si disponible.

Objectif : une même source couleur PATH AA non filtrée doit pouvoir être
consommée au root d'une frame W6b et dans un saveLayer plain compatible,
avec une géométrie math et une autorité de samples identiques. Le census
443éligibles/393entrées/220rendus/197comparés n'a montré aucun gain
d'admission. Les32 premiers refus layer sont hétérogènes ; ni32 ni17
nouveaux rendus ne sont promis.

## Alternatives et décision

1. **Source AA commune root/layer** : préparation math et resolve AA4
   existent ; prouver d'abord que le scope est le seul obstacle. Axe B
   recommandé par la revue stratégique Sol indépendante.
2. Rect STROKE→Path : le Core commun ne consomme actuellement ni contour
   stroke fermé ni AA4. Un simple helper de mapper ne règle pas cette
   fermeture ; axe A différé jusqu'à une preuve ciblée distincte.
3. Encoded général : déplace aujourd'hui des scènes vers d'autres refus
   publics de composition. Axe C différé, sans migration des defaults GM.

Pas de nouvelle lane, flag par GM, algorithme de couverture ou duplication
de géométrie dans le renderer. Renommer/factoriser le contrat ordinary
existant plutôt qu'en ajouter une quatrième variante.

## Gate causale avant produit

Surface32×32 LINEAR, fond transparent. PATH horizontal(8,16)→(24,16),
STROKEwidth2 puis1, BUTT/MITER, solide rouge opaque SrcOver, clip hard
I32[0,0,32,32]. Trois scènes publiques : root seul ; même draw sous
saveLayer plain sans paint/bounds ; root avec sibling NORMAL-blurred
solide bleu, sigma1, séparé par un hard clip I32[0,0,4,4]. Le sibling
dessine Rect[-16,-16,48,48] : tout le support du noyau autour des16pixels
visibles est intérieur opaque, sans estimer un halo. Il reste un vrai
filtre enregistré ; si ce contrôle n'est pas admis, aucun fix avant un
nouveau diagnostic/contrôle justifié indépendamment.

Oracle entier avant GPU : root/layer width2 ont rouge[255,0,0,255] sur
x8..23/y15..16, transparent ailleurs ; width1 a [188,0,0,128] sur les mêmes
cellules (sRGB d'une valeur linéaire prémultipliée1/2), transparent ailleurs.
Le contrôle W6 ajoute bleu[0,0,255,255] sur x0..3/y0..3 uniquement. Les
valeurs pleines/vides sont exactes ; les valeurs fractionnaires ont ±1LSB
par canal pour quantification UNORM existante, pas une tolérance de score.
Égalité root/layer complète en plus de l'oracle, jamais seul oracle.

Chaque positif doit prouver RGBA8/4096bytes, zéro refus/diagnostic, vrais
draws/pipelines, scopes Render+Readback, submission puis completion native
réussie. Deux rendus même Surface et deux Surfaces fraîches, sans purge ou
dispose entre frames. Le seul AfterAll dispose conserve le protocole JVM.
Le RED attendu est `w6a.layer.unsupported_child` du layer, pas une erreur
de compilation/device. Ce résultat est une hypothèse à mesurer.

Si root/contrôle refusent, si layer passe déjà, si un consommateur AA4
manque ou si restore plain change les pixels, arrêter l'extension de scope
et consigner le falsifier ; aucune suppression de guard spéculative.

## Contrat retenu après gate positive

- PATH/ANTIALIASED, FILL ou STROKE, solide SrcOver réellement sans
  shader/colorFilter/pathEffect/imageFilter/maskFilter/effet ; clips hard
  I32 et transforms déjà acceptés par la préparation existante.
- L'original enregistré et le child préparé doivent être le même draw
  ordinaire. `stripW6bPayload` ne permet pas de faire passer un filtre propre
  pour une source couleur ; ResolvedCoverage reste un autre contrat.
- Une factory/predicate ordinary commune à W6 consomme la même préparation
  `preparePathStrokeGeometryF32`/`PathFillGeometryF32` existante et le même
  stencil/directAA4→resolve1x→AaResolvedColor. Source consommée une fois.
- Root historique corrélé sans ownership W6b inchangé. Les lanes AA FILL
  layer/deferred déjà admises conservent pixels/ordre/admission ; éviter un
  changement de priorité gratuit lorsqu'une autorité historique suffit.
- Scope ne change que cible/localisation/clip/restore et lifetimes. Garder
  occurrence originale, transform appliquée une fois, formats/domaine,
  alpha prémultiplié, seals et coûts exacts jusqu'au consommateur natif.
- Propriétés de layer/restore déjà admises seulement. Pas de filtre/AA clip,
  nouveau blend/matériau/encoded layer débloqué implicitement.

## Preuves comportementales et livraison

Au-delà de la gate : alpha128, double occurrence demi-couverte dans le même
layer (alpha3/4, couleur LINEAR≈225, alpha191/192), restorealpha128,
ordre avec sibling opaque intersectant, layer bounds/translation et hard
clip touchant le trait. Distinguer samples corrélés root et sources isolées,
ne pas rendre leur différence invisible par un oracle d'équivalence faux.
Oracles analytiques fullbuffer fixés avant run, pixels pleins/vides exacts,
fractionnaires ±1LSB existant ; aucune attente ajustée au GPU.

Négatifs : propre mask/image filter non promu, shader, PLUS, AA/fractional
clip ; refus précis actuels, sentinel readPixels intact, discardRecordedOperations
puis récupération native même Surface. Un axe déjà admis reste admis : le
test ne doit pas imposer un refus à une autre lane positive existante.
LINEAR positif ; layer SRGB_ENCODED garde son refus public de composition,
avec root PATH encoded admissible comme contrôle distinct.

Après GREEN, revoir le lot et mesurer les17 anciens PATH/AA layer refusés
du census, identité/scènes/domaines/références/budgets inchangés, images=false.
Les filtered PATH restent une frontière distincte. Une disparition du
premier diagnostic sans vrai rendu/metrics n'est pas une admission livrée.
Si aucun GM n'est débloqué, le dire et re-prioriser avant un nouveau lot.
Publier uniquement la preuve/produit réellement retenus en draft empilé,
avec limites ; pas de global GREEN, merge ou clôture W7.

## Contraintes globales

- Fonts/codecs externes/jpg-color-cube exclus, aucune nouvelle exclusion.
- Pas de CPU renderer/fallback, mock/fake/injected callback, skip GPU,
  tests d'infrastructure/source-text/forwarding.
- Références, scores/seuils/tolérances, registre631/scope443, sample count,
  validators, caps et budgets inchangés.
- Géométrie dans math, nomenclature I/F32/64 ; aucune reconstruction renderer.
- Contrôleur seul pour runtime/Git, un runtime à la fois, borne240s inchangée ;
  terminal→audit intégral log/exit/events/XML/inventaires/ownPGID→postseal
  séparé avant toute écriture ou run suivant.
- Workspaces/raw receipts et inverse untracked SHA96cd8349 préservés ;
  aucun cleanup/merge, pas d'écriture code/docs/Git pendant native.
- Reviews indépendantes, suites globales RED/incomplètes explicites.
