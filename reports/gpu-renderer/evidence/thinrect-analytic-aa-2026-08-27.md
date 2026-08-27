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

Les trois GMs requis sont exécutés par `SkiaGmRenderer.renderTerminalAttempt`,
sans modification ni régénération de GM : `thinrects` refuse
`unsupported.core_primitive.geometry.invalid` à l'opération 338,
`thinroundrects` le même diagnostic à 338, et `rrect_clip_aa` refuse
`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted` à 45.

La fixture WebGPU `DIFFERENCE` force un `ShaderBlendWithDstRead`, crée une
destination snapshot et compare les 64 canaux du readback avec
`GPUBlendOracle`: zéro différence, un snapshot, une submission et une copie
de readback.

Les détails CPU, GPU, diff, route, statistiques et refus se trouvent dans les
JSON voisins, dont `destination-read.json`.
