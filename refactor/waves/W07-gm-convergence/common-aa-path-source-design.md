# W7 — source couleur AA PATH commune aux scopes W6

4 octobre 2026. Base : draft #2447, renderer produit2485cfb. Lot
architectural limité à un contrat existant, sans nouvel algorithme AA.

État courant : gate initialement falsifiée par le témoin filtré, puis
rejouée après la fondation SolidRect au produit867cd2146 / HEAD5df3c9e8.
Root et contrôle filtré PASS ; layer RED attendu, audit et postseal faits.
Voir common-aa-path-source-gate.md. Depuis, discriminants RED causaux puis
source commune GREEN9/9 (budget corrigé analytiquement et relu avant run) ;
contexte119/120, globale du module interrompue. Revue produit/corpus requis.

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

Ce lot est une closure/foundation du contrat source, pas un ROI corpus
établi. Périmètre explicite :

| Scope | Décision |
| --- | --- |
| Root autonome corrélé, sans propriétaire W6b | Inchangé |
| Root ordinary d'une frame W6b | Contrat existant réemployé |
| Child d'un plain layer compatible | Extension visée après gate |
| Root d'une frame plain-layer-owned sans ownsW6b | Différé ; guard conservé |

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
ordre avec sibling opaque intersectant. Pour bounds/translation : translate
(3,2) avant saveLayer, hint LOCAL[12,15,20,17] donc DEVICE[15,17,23,19],
trait DEVICE(11,18)→(27,18). Attendre les pixels x11..26/y17..18, y compris
x11..14 et x23..26 hors hint. Un cas distinct pose le hard clip DEVICE
[12,0,20,32] avant translation et n'attend que x12..19. Le hint n'est pas
un clip ; origin et transform restent observables. Distinguer samples corrélés root et sources isolées,
ne pas rendre leur différence invisible par un oracle d'équivalence faux.
Oracles analytiques fullbuffer fixés avant run, pixels pleins/vides exacts,
fractionnaires ±1LSB existant ; aucune attente ajustée au GPU.

Négatifs : propre mask/image filter non promu, shader, PLUS, AA/fractional
clip ; refus précis actuels, sentinel readPixels intact, discardRecordedOperations
puis récupération native même Surface. Un axe déjà admis reste admis : le
test ne doit pas imposer un refus à une autre lane positive existante.
LINEAR positif ; layer SRGB_ENCODED garde son refus public de composition,
avec root PATH encoded admissible comme contrôle distinct.

Budget nouveau STROKE : fixture indépendante2×2, line(0,1)→(2,1), width1
BUTT/MITER, plain layer sans hint/sibling. B analytique25312bytes : root
RGBA16 + readback512 (2rows alignées256) + layerRGBA16 + sourceAA4RGBA64
+ sourceAA4D24S8 64 + resolveRGBA16 + poolsV/I/U16384/4096/4096
+ uniformW6 16 + SolidRGBA16 + paint tail-alpha16 (layout composé32). Le quad stroke suit StencilCover, pas
DirectTriangle ; aucun discount cache/lifetime. La pré-évaluation source
25104 ne dépasse pasB. ÀB : les4pixels[188,0,0,128]±1, completion/replay
stricts. ÀB−1 : budget.w5g.composed-uniform, sentinel16bytes0x5a intact,
discard puis bleu plein/récupération native même Surface. La dérivation a
été relue indépendamment avant GREEN2 ; ces frontières sont qualifiées ;
l'ancien AA FILL B26980 reste un contexte distinct. Le surcoût provisoire
source full-target32×32 ne sera pas affaibli pour faire passer ce test.

Après GREEN, revoir le lot et mesurer tout le census, avec les17 anciens
premiers préfixes w6a.layer.unsupported_child PATH/AA identifiés :14root et
3child,12filteredroot et5unfiltered. Les3child ne sont pas des témoins de
simple STROKE solide SrcOver. Identité/scènes/domaines/références/budgets
inchangés, images=false ; aucun ROI immédiat n'est déduit de ce sous-groupe.
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
