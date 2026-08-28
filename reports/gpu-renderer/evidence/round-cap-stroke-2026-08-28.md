# Stroke à cap rond borné — W25 (2026-08-28)

W25 étend la route native `StrokeStencilEdgeFan` à un unique segment ouvert
avec un cap `round`, seulement dans le contrat pixel-exact prouvé : largeur
`4` (rayon `2`), segment horizontal de gauche à droite d'au moins `4` pixels,
extrémités sur la grille entière du device, sans anti-aliasing, dash ni path
effect, avec transform identité ou translation entière et join `miter` (sans
effet sur un unique segment). Les caps `butt` et `square` existants restent
inchangés.

La scène promue `round-cap-stroke` passe par l'API publique `Surface`, effectue
une soumission WebGPU native (`submissionDelta=1`) et son readback est identique
à l'oracle CPU analytique indépendant : 32 x 32 pixels, tolérance 0,
similarité 100 %, zéro pixel ou canal différent.

Les variantes non prouvées ne sont pas ouvertes par cette vague. En particulier,
largeur différente, segment vertical/diagonal, coordonnées fractionnaires,
sens inverse ou segment trop court avec `round` sont refusés avant préparation
native avec `unsupported.core_primitive.stroke.round_cap_pixel_exact_lowering`.
Un chemin à plusieurs segments conserve
`unsupported.core_primitive.stroke.complex_exact_lowering`. AA/MSAA, dash,
path effects, joins non `miter` et transformations complexes restent hors de
ce contrat.

La source de vérité est le code, les tests et le bundle promu sous
`correctness/promoted/round-cap-stroke/` (`manifest.json`, oracle/readback,
diff, stats, route et diagnostics). Ce fichier sert uniquement d'index humain.
