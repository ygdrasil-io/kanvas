# Radial gradient sous clip stencil — W26 (2026-08-28)

W26 ajoute une route ClipStencil native étroite pour un `FillRect` à radial
gradient `clamp`, sous un clip de path `Winding` dur, sans AA ni MSAA. La
preuve publique utilise deux stops opaques sRGB et conserve les refus des
autres tile modes, de MSAA et des consommateurs qui ne correspondent pas au
`radial.gradient.fill` préparé.

La scène promue `clip-path-triangle-radial-gradient` passe par `Surface` et
effectue une soumission WebGPU native : `submissionDelta=1`, trois draws,
trois pipeline binds, un `HardClipStencilProducer` et aucun diagnostic.
L'oracle CPU est indépendant : il évalue au centre des pixels le winding du
clip, la distance radiale, l'interpolation linéaire prémultipliée et le
stockage sRGB.

La comparaison est volontairement bornée à un LSB RGBA8, à 100 % des pixels.
Les 46 différences observées sont toutes de magnitude 1, sur les rayons non
entiers (par exemple `(11,8)` : CPU `ff6900ee`, GPU `ff6900ef`). Elles
proviennent de `length(d) / radius` évalué en `f32` par WGSL, face à la
distance/interpolation/quantification calculée en double par l'oracle. Le
test catalogue verrouille qu'un LSB passe et que deux LSB échouent. Cette
policy est identique au radial gradient déjà promu hors clip ; elle n'autorise
ni approximation géométrique du clip ni baisse de similarité.

La source de vérité reste le code, le test de route et le bundle promu sous
`correctness/promoted/clip-path-triangle-radial-gradient/` ; ce rapport est
un index humain du contrat et de sa limite numérique.
