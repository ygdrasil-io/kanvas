# W7 — port fidèle de sk3d_simple

## Intention

Corriger la scène comparée avant d'attribuer ses différences au renderer.
L'utilisateur délègue les arbitrages W7 et demande des PR draft empilées,
avec implémentation par subagent et relecture indépendante. Base : draft
[#2426](https://github.com/ygdrasil-io/kanvas/pull/2426), `b31185372`.
Ce lot ne clôt ni W7 ni les gates globales.

Le corpus au produit `3398dc3` rend ce GM à 51,931111 % à ±2 et SSIM 0,638501.
Le port emploie une rotation 2D, alpha 0,5 et AA implicite. La
[source Skia figée](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/3d.cpp)
emploie une caméra perspective, une rotation autour de Y, alpha 136/255 et
`SkPaint` sans AA. Ce sont trois défauts concrets du port, pas une preuve que
leur correction résoudra toute la différence de couleur avec la référence.

## Correction retenue

Réutiliser `math` : `Matrix4x4F32.rotate`, `lookAt`, `perspective`, `scale`,
`invert` et `asM33`. Pour ce dessin dans le plan z=0, `asM33` conserve
l'homographie (transformation projective plane) ; aucune nouvelle API 3D.

Paramètres Skia : near 0,05, far 4, angle π/4, eye `(0,0,1/tan(angle/2)-1)`,
centre `(0,0,0)`, up `(0,1,0)`, rotation Y π/6, viewport scale `(150,150,1)`.
Ordre exact : `viewport * perspective * camera * model * inverse(viewport)`,
puis translation `(150,150)` avant Rect `(-100,-100,100,100)`.
Appliquer cet ordre séparément au draw rouge direct et au draw bleu dans une
vraie `PictureRecorder` de bounds `(0,0,300,300)`, puis `drawPicture`.
Rouge `0xFFFF0000`, bleu `0x880000FF`, `antiAlias=false` explicite pour les deux.

Conserver les interfaces et chemins existants : le `GmCanvas` direct projette
déjà ses Rect généraux en Path ; le Canvas du recorder conserve Rect+CTM.
Ne pas remplacer la Picture par un dessin direct ou un Path préprojeté pour
masquer un refus. Un refus natif confirmé déclenche un diagnostic de son
contrat avant tout changement de renderer. Une approximation affine ou une
retouche de référence n'est pas une solution.

## Preuves et limites

Trois témoins publics natifs, construits indépendamment avant Surface :
silhouette perspective, couleur intérieure distinguant alpha 136 de 128 et
omission Picture, puis bord hard distinguant AA. Render/Readback réels,
zéro diagnostic/refus/skip et répétition identique. Pas de tests
d'infrastructure, mocks, forwarding ou inspection de source.

Pour contrôler la géométrie sans utiliser les matrices produit, poser
`q=cot(π/8), c=cos(π/6), s=sin(π/6), u=(x+150)/150, v=(y+150)/150`.
Les coordonnées device indépendantes sont `(150*q*c*u/(q+s*u),
150*q*v/(q+s*u))`. Les coins valent environ `(40,504985;46,771127)`,
`(160,949968;37,169803)`, `(160,949968;185,849015)`,
`(40,504985;233,855637)`. Les témoins ne déduisent rien des pixels mesurés.
Sous le domaine LINEAR existant, l'intérieur rouge puis bleu a RGB sRGB
arrondi `(182,0,193)`, A=255 ; le test utilise ±1 sur R/B, G/A exacts.
Cela ne revendique pas que la référence Skia a été générée dans ce domaine.

Références, seuils, domaine LINEAR, exclusions et timeout 30 s/GM restent
inchangés. Mesurer le même corpus 631/443 et distinguer changement de scène,
capacité de rendu et fidélité. Fonts, codecs/décodage externe et jpg-color-cube
restent exclus. Aucune relaxation de proof, epsilon, cap ou oracle existant.
Toute nouvelle géométrie appartiendrait à math avec nomenclature I/F32/64 ;
aucune n'est prévue. Pas de fallback CPU, de routage par nom de GM ni de merge.
