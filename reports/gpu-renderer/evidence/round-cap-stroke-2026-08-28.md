# Stroke à cap rond borné — W25 (2026-08-28)

W25 étend la route native `StrokeStencilEdgeFan` à un unique segment ouvert
avec un cap `round`. Le périmètre prouvé reste volontairement étroit : largeur
finie entre `0.5` et `64`, sans anti-aliasing, sans dash ni path effect, avec
transform identité ou translation et join `miter` (sans effet sur un unique
segment). Les caps `butt` et `square` existants restent inchangés.

La scène promue `round-cap-stroke` passe par l'API publique `Surface`, effectue
une soumission WebGPU native (`submissionDelta=1`) et son readback est identique
à l'oracle CPU analytique indépendant : 32 x 32 pixels, tolérance 0,
similarité 100 %, zéro pixel ou canal différent.

Les variantes non prouvées ne sont pas ouvertes par cette vague. En particulier,
un chemin à plusieurs segments avec `round` est refusé avant préparation native
avec `unsupported.core_primitive.stroke.complex_exact_lowering`. AA/MSAA,
dash, path effects, joins non `miter` et transformations complexes restent hors
de ce contrat.

La source de vérité est le code, les tests et le bundle promu sous
`correctness/promoted/round-cap-stroke/` (`manifest.json`, oracle/readback,
diff, stats, route et diagnostics). Ce fichier sert uniquement d'index humain.
