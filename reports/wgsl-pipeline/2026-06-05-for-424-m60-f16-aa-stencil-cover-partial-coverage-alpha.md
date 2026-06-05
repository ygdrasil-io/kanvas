# FOR-424 - M60 F16 divergence alpha coverage AA stencil-cover

Date: 2026-06-05

## Résultat

Classification: `partial-coverage-alpha-quantization-mismatch`.

Le diagnostic borne confirme que les 6 pixels partiels identifiés par FOR-423
ont tous le même profil: la couverture CPU attendue vaut `160/255`, mais
l'alpha de source vérifiée envoyé au blend vaut `96/255`. Le ratio est donc
constant: `96/160 = 0.6`.

## Ce que cela élimine

- Le retour WGSL instrumenté reste vérifié par FOR-421.
- La source vérifiée correspond au scratch color-target et reconstruit la
  mutation finale selon FOR-422.
- FOR-423 reste le prérequis direct: la divergence de couverture porte sur 6
  pixels partiels, distincts des 10 divergences de source opaque.
- Les 6 pixels FOR-424 appartiennent au même rôle de sous-dessin: `inside`,
  drawIndex `1`, bande `round-round`, cap `round`, join `round`.

## Preuve produite

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-partial-coverage-alpha-for424/m60-f16-aa-stencil-cover-partial-coverage-alpha-for424.json`

L'artefact liste les 6 pixels partiels et inclut pour chacun:

- alpha source vérifiée;
- couverture CPU attendue;
- ratio source/couverture;
- drawIndex et rôle de sous-dessin;
- bande, cap et join;
- voisinage local de couverture CPU en 3x3;
- classification locale.

## Interprétation

L'hypothèse la plus probable n'est plus le stockage diagnostique, le scratch
color-target, le blend final, ni un mauvais sous-dessin. Le motif stable
`96/255` contre `160/255` pointe vers une erreur de quantification ou de
conversion d'alpha dans le chemin AA stencil-cover de M60 F16.

Le prochain ticket doit donc ajouter une mesure renderer plus proche du calcul
AA/stencil, ou corriger cette conversion, sans modifier support, promotion,
seuil, score ou fallback avant preuve.

## Non-objectifs conservés

- Aucun changement de rendu par défaut.
- Aucun changement de seuil, score, promotion, fallback ou support claim.
- Aucun dump massif de framebuffer ou WGSL.
- Aucun changement wgsl4k.
- Aucune correction renderer appliquée dans FOR-424.

## Validation

- `rtk python3 scripts/validate_for424_m60_f16_aa_stencil_cover_partial_coverage_alpha.py`
- `rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py`
- `rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for424-pycache python3 -m py_compile scripts/validate_for424_m60_f16_aa_stencil_cover_partial_coverage_alpha.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
