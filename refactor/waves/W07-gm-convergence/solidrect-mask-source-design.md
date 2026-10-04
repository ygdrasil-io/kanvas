# W7 — autorité source SolidRect avant mask blur

4 octobre 2026. Sous-lot architectural préalable à common AA PATH, sous
pilotage/carte blanche W7 explicitement délégué par l'utilisateur. Main
choisit et relit cette spec ; aucune relecture personnelle humaine inventée.
Le rendu produit, les codecs externes et fonts restent inchangés ici.

## Intention et cause

Restaurer les pixels qu'un mask blur doit pouvoir lire avant le clip final.
La gate common-aa-path-source a établi root natif PASS et layer refusé,
mais son témoin filtré a échoué `[0,0,186,125]` contre bleu opaque.
Le rapport indépendant Astra `blur-control-strategy-report.md` (workspace
common-aa-path-source-plan) identifie statiquement W3 SolidRect : geometry
complète perdue dans visible=target∩shape∩clip ; W6b réemploie visible comme
raw source. Le kernel sigma1 sept taps prédit exactement alpha125/bleu186.
Ce n'est pas une capture de plan natif ; il faut un discriminant public.

## Alternatives et décision déléguée

1. Retenir la source RectI32 complète dans l'autorité W3, utiliser ce fait
   contextuellement au raw raster mask blur W6 : retenu, sans nouveau compiler.
2. Ajouter un compiler source dédié : différé, duplication/transport plus
   large que le défaut établi ; aucune nouvelle lane pour cette correction.
3. Effacer le clip/togglage AA/clamp/padding opaque : rejeté, perte du halo
   root négatif encore possible ou véritable bord géométrique falsifié.

Ce changement d'interface de plan est traité architecturalement, non comme
un simple réglage de fixture. Source AA PATH demeure STOP conditionnel ;
ni la correction blur ni un diagnostic supprimé ne prouvent sa qualification.

## Autorité et transport

W3 possède déjà le résultat `resolveTransformed` RectI32 avant target/clip.
SolidRectDraw conserve un snapshot défensif explicite
`copySourceRasterBoundsI32(): RectI32`, même repère que visible/scissor.
Factories prennent `sourceRasterBoundsI32: RectI32 = visibleBounds` en
paramètre final ; W3 fournit la géométrie authentifiée entière. Les producteurs
historiques sans source plus large conservent le default, pas un faux domaine.
Visible/scissor, matériel, blend, coverage/sample restent les mêmes au root.

Le fait traverse command/material/blend/clip/origin rebinds et les relations
immutables/seals/canonical recipes pertinentes ; aucun clone ne le perd.
Pour direct MaskFilter.Blur seulement, W6 lit les bounds source complets,
les intersecte avec l'inverse-demand existante, puis produit réellement la
couverture dans cette allocation. Ne pas modifier globalement
w6aRasterBoundsI32/known-content des scopes ordinaires. Pour le témoin bord,
source `[-3,-3,7,7]`, origin device(-3,-3), extent10×10 ; clip final[0,0,4,4].
Le fullscreen opaque est valable seulement si l'allocation authentifiée
est contenue dans la véritable géométrie. Aucune connaissance opaque
étendue sans producteur ; source texels transparents restent transparents.

Renderer consomme l'autorité gelée et les coordonnées localisées existantes,
ne reconstruit pas la géométrie. Aucun nouvel objet géométrique hors math,
nomenclature I/F32/64 respectée. Budgets/validators/caps inchangés, coûts
physiques de la vraie allocation chargés sans discount cache/lifetime.

## Preuves publiques avant/après source

Ancienne gate et oracle SHA b6206a00 restent intacts. Nouvelle classe
W7SolidRectMaskBlurSourceSurfacePixelTest, Surface32×32 LINEAR.

- Clip intérieur[8,8,12,12], même bleu non-AA Rect[-16,-16,48,48]/NORMALsigma1
  et trait rouge AAwidth2(8,16)→(24,16) : fullbuffer4096, bleu opaque dans
  clip, trait rouge x8..23/y15..16, transparent ailleurs. Distingue clip
  comme source de root-surface truncation. Deux renders même Surface et
  deux Surfaces fraîches ; aucun purge/dispose interframe.
- Sans filtre, grand Rect bleu et clip[0,0,4,4], puis clip intérieur : bleu
  uniquement dans le clip et transparent ailleurs, même native/replay.
  Préserve cropping ordinaire et ne remplace pas un négatif par un skip.
- Vrai bord géométrique : Rect bleu[8,8,24,24], NORMALsigma1 et clip[8,8,12,12],
  fond rouge opaque. Oracle indépendant du code : `w(k)=exp(-k²/2)` pour
  k=-3..3, `q(d)=sum(w(k),k>=-d)/sum(w(k))`, d=x−8/y−8. Horizontal UNORM8
  h=round(255*q(x−8)), alpha=round(h*q(y−8)); composite LINEAR sur rouge,
  bytesRGB=sRGB(1−alpha/255),0,sRGB(alpha/255), alpha255. Fractionnaires
  ±1LSB existant, valeurs pleines/vides exactes. Formule fixée avant native,
  pas d'attente dérivée des buffers GPU. Garde le véritable falloff et
  protège contre opaque-padding/clamp/normalisation des taps survivants.

Tous positifs : entier RGBA8, diagnostics/refus0, vrai Render+Readback,
submission/completion, draws/pipelines réels ; aucun capability-refusal
accepté. Sentinel/récupération avec budget public strict inchangé sur un
cas refusé ; dérivation des nouvelles charges documentée avant GREEN,
aucun chiffre de boundary calibré au GPU. Tests de contexte W6a bounds,
restore/composition et masque existants ; legacy branches identifiées.

Un source entièrement hors consumer mais dans le halo reste une admission
séparée : W3 peut le rejeter avant publication. Le suivre explicitement,
pas débloquer silencieusement tous les filtres/geometry/materials/domaines.
Si RED intérieur ne présente pas le déficit de clip prévu, arrêter source
et revenir au diagnostic. Après correction, ancien témoin doit devenir
fullbuffer/native/replay PASS ; layer common AA peut rester RED attendu.

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

## Livraison

SDD Luna implémentation, main native/Git, reviewer indépendant Astra pour
ce choix architectural diagnostiqué (Sol review tenté mais capacité refusée).
Aucune prétendue nouvelle review Sol. Preuve source puis revue, mesure complète
631/443 sur cinq slices inchangées images=false seulement si fix retenu ;
delta réel, régressions et limite fully-clipped explicites. Draft empilée sur
2447 avec source/preuves retenues après review ; aucun merge/globalGREEN/W7complete.
