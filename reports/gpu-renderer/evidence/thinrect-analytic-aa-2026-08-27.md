# Thin rect — analytic AA (2026-08-27)

La fixture `thinrect-analytic-aa-v1` couvre un unique `FillRect` vert opaque
de largeur `1/8` de pixel dans une cible WebGPU headless/offscreen 4×4. Pour
les rayons nuls, le WGSL calcule l'intersection exacte entre le rect et la
boîte du pixel courant; il ne dépend donc ni d'un fan stencil, ni d'une rampe
de distance qui sous-compterait les primitives minces.

L'oracle CPU et le readback GPU sont byte-exact: 64 canaux comparés, zéro
différence, delta maximal 0. Les deux pixels touchés sont `[0,99,0,32]` après
le store sRGB. Les compteurs de submission/readback sont descriptifs et ne
changent aucun gate de performance ou de similarité.

`thinrects` entier n'est pas promu: il contient volontairement des rects de
largeur nulle et conserve le refus terminal stable
`unsupported.core_primitive.geometry.invalid`. `thinroundrects` et
`rrect_clip_aa` restent hors du sous-ensemble. Aucun GM n'a été modifié ni
régénéré.

Les détails CPU, GPU, diff, route, statistiques et refus se trouvent dans les
JSON voisins.
