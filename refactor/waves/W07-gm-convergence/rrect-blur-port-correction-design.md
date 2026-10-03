# W7 — correction bornée des entrées du port RRectBlur

3 octobre 2026. Autorité utilisateur : carte blanche W7, Subagent-driven,
Sol seulement pour les reviews, Astra support stratégique ponctuel.
[Avis Astra et falsification](w6-ordinary-aa-full-gm-strategy.md).
Parent local qualifié pour admission, pas fidélité :
`2d84851d62ade6757628053189701f9d408927f3`. Ce lot ne clôt pas Task2 originale.

## But et choix

Corriger un défaut certain des paramètres du GM et ses opérations proches,
pas la math ni l'algorithme AA. RRectF32.of(rect, CornerRadii) désigne topLeft
uniquement ; l'appel actuel n'est pas un constructeur uniforme. Paramètres
nommés pour les quatre coins, API inchangée. Corriger le port en préservant
les references est légitime ; un faux panneau noir gagnerait un score sans
réaliser la scène attendue, donc interdit.

La source primaire Skia consultée par Astra est8d5cb2e, lien dans l'avis.
Target8019 non vérifiée : pas de restauration exacte upstream/PNG revendiquée.
Les coins uniformes de la première ligne sont corroborés par la référence et
le contrat du GM ; la révision consultée fixe également les opérations ci-dessous.

## Paramètres et opérations bornés

- cellY0 : bounds0,0..50,50 ; TL/TR/BR/BL=(10,15), sigma1, blanc.
- cellY100 : bounds0,0..60,80 ; TL/TR/BR/BL=(3.1,1.5), sigma0.5, jaune.
- cellY200 neuf-patch inchangé : TL5,10/TR13,10/BR13,7/BL5,7 ; sigma2.5,
  couleur200,100,30. cellY300 : TL0,0/TR20,1/**BR10,30/BL30,30** ;
  bounds0,0..90,90, sigma1.1, couleur35,120,220.
- Même padding entier, background68, dimensions300×400 et domaine LINEAR.
- Gauche appelle le vrai GmCanvas.drawRRect ; droite garde Path.addRRect/drawPath.
- Séparateurs AA blancs hairline width0, verticales100 ET200, horizontales
 100/200/300. Canvas.drawLine garde sa correction STROKE qualifiée.
- Paint AA des formes filtrées et MaskFilter existants conservés pour isoler
  ce lot ; respectCTM=false upstream n'a pas de paramètre public équivalent,
  reste une dette, sans effet affirmé au-delà de la CTM identité de cette scène.
  Pas d'extension MaskFilter ni de migration globale des defaults Paint.

## Preuve causale avant correction

Un contrôle natif sans filtre dessine le même RRect via Path.addRRect blanc AA
sur transparent100×100, bounds25,25..75,75. Quatre coins explicites10,15 :
RGBA0 aux27,27/72,27/27,72/72,72 ; centre50,50white255. Le constructeur
actuel à un seul argument de coin est un contrôle API valide : seulementTL
transparent, les trois autres blancs. Variante asymétrique non filtrée90×90 :
BR10,30/BL30,30, cellule5,80 transparente/84,80 blanche ; inversion contraire.
Ces cellules entières sont séparées de l'ellipse, pas ajustées au rendu.

Ce contrôle math seul ne prouve pas le port : deux témoins consomment le GM
réel300×400, pas une copie de ses constructeurs. Première ligne filtrée sigma1
supportceil(3sigma)=3 : bg68 aux24,24/75,24/24,75/75,75 et aux miroirs x+200.
Les fenêtres de source restent hors ellipse (minimum normalisé>=1.13 à la
frontière des cellules) ; les trois coins carrés actuels laissent du contenu
dans ces fenêtres. Cellule bleue10,394 : sous BL30,30 et support4, les cellules
source les plus proches ont somme normalisée>=1.1388, donc bg68 attendu.
Centres blancs50,50/250,50 et bleus50,350/250,350 gardent leurs couleurs.
Deuxième verticale : x199/200,y50 RGB192±1/alpha255, extérieurs198/201bg68.
Toujours natif positif, zéro refus, Render+Readback, background opaque.

Les scopes Render/Readback sont requis pour les deux témoins du GM W6.
Le contrôle API sans filtre utilise la route prepared publique, qui exporte
scopes[] mais de vrais drawCallCount/pipelineCount issus de l'exécution GPU.
Il exige clean/refused0/ops>0/drawCallCount>0/pipelineCount>0 et les pixels
littéraux, pas des scopes que cette route ne produit pas. Aucun filtre/sibling
artificiel n'est ajouté pour forcer une autre route. RED1 a confirmé les deux
pixels fautifs du GM, mais son contrôle API a échoué sur cette attente de
metadata avant ses pixels : RED causal complet doit donc être rejoué.

## Gates et limites explicites

RED pixels du vrai GM puis correction minimale puis GREEN sur mêmes oracles.
Si les quatre coins explicites du contrôle natif échouent, attribuer math/
renderer séparément ; si drawRRect filtré révèle une autre famille refusée,
stopper et consigner, pas modifier silencieusement W6/consumer/shader/budget.
GM entier, référence SHA3327fa6254d219f5a23c5bdcdab30f0f363da1834353ff246f7e0b5ce66beaaa
inchangée, comparison/crops avant hardassertions,
replay, nouvelles régions de coins et x200 inspectées. Blurcircles reste
inchangé,60.84485%±2/SSIM0.91397 témoin de dette sans cause attribuée.

Panneau central/labels restent non livrés ; metrics entières les incluent,
pas de crop utilisé comme nouvelle exclusion. Pas de fonts, codecs externes,
jpg-color-cube, CPU renderer/diff/fallback, mocks/skips ou tests d'infrastructure/
source-text/packet-forwarding. Objects géométriques math I/F32/64 inchangés.
Pas de registre631/scope443, références/scores/seuils/sampling/budgets modifiés.
Contrôleur seul runtime/Git, un runtime240s max, audit terminal/log/exit/events/
tous XML/stdout/stderr séparé avant prochain run/édition. Protected96cd et raw
workspaces conservés. Relectures locales, pas de merge/globalGREEN/W7 clos.
