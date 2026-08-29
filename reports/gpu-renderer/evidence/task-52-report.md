# W76 — replay ciblé du path stencil et du destination-read

## Conclusion

Le correctif W75 est vérifié sur un GM non-font qui atteint effectivement la
route de rendu : `clip_shader_difference`. Il produit un PNG dans un répertoire
temporaire, avec `dispatch=1` et `refuse=0`. Le PNG produit après W75 est
identique au PNG suivi par W73 : 0 pixel différent et 0 différence de canal.

Ce replay ne permet donc pas de déclarer une amélioration de similarité Skia :
le score reste celui du catalogue (`58.2834%` selon la comparaison RGBA avec la
référence, tandis que le seuil du GM est `0.0`). Il prouve surtout l'absence de
régression et l'exécution native de la combinaison path + composition
destination-read utilisée par ce GM.

## GMs sélectionnés dans le code

Les trois cas ont été choisis après inspection des sources Kotlin :

| GM | Relation avec la route | Résultat W76 |
| --- | --- | --- |
| `clip_shader_difference` | `drawPath` dans un `saveLayer` `SRC_OUT`, après un fond shader ; c'est le meilleur cas non-font atteignant le path compositional | rendu, `dispatch=1`, `refuse=0` |
| `aaxfermodes` | chemins AA et modes de blend destination-read dans une composition plus large | refus stable `unsupported.composite.picture.budget` |
| `dstreadshuffle` | formes path et blend `COLOR_BURN`, avec lecture de destination | refus stable `unsupported.stroke.width_invalid` |

`PlusMergesAA` a aussi été vérifié comme candidat adjacent (deux paths dans un
`saveLayer` `PLUS`), mais il reste hors de cette route :
`unsupported.layer.bounds_unbounded`.

Les refus sont intervenus avant la production d'un PNG ; aucune comparaison
pixel n'est donc inventée pour ces cas. Ils ne sont pas causés par le bind-group
W75, mais par des contrats de lowering indépendants.

## Commandes et artefacts

Les replays ont utilisé `generateSkiaRendersFor` avec une sortie temporaire :

```text
./gradlew --offline --no-daemon --max-workers=1 \
  :integration-tests:skia:generateSkiaRendersFor \
  -Pgm.name=clip_shader_difference -Pgm.includeBlocking=true \
  -Pgm.outputDir=/tmp/kanvas-w76-gms
=> [RENDER] composite/clip_shader_difference.png (512x512, dispatch=1, refuse=0)
=> Done: 1 rendered, 0 failed

./gradlew --offline --no-daemon --max-workers=1 \
  :integration-tests:skia:generateSkiaRendersFor \
  -Pgm.name=aaxfermodes -Pgm.includeBlocking=true \
  -Pgm.outputDir=/tmp/kanvas-w76-gms
=> [FAIL] aaxfermodes — unsupported.composite.picture.budget

./gradlew --offline --no-daemon --max-workers=1 \
  :integration-tests:skia:generateSkiaRendersFor \
  -Pgm.name=dstreadshuffle -Pgm.includeBlocking=true \
  -Pgm.outputDir=/tmp/kanvas-w76-gms
=> [FAIL] dstreadshuffle — unsupported.stroke.width_invalid
```

Comparaison réalisée entre le PNG temporaire W76, le PNG suivi par W73 et la
référence Skia :

```text
clip_shader_difference vs W73: 0 pixels différents, somme absolue 0
clip_shader_difference vs référence: 262144 pixels concernés,
  somme absolue des canaux 111544822, similarité RGBA 58.2834%
```

La commande de test paramétrée pour `clip_shader_difference` a également
terminé avec `BUILD SUCCESSFUL`; les deux autres cas échouent volontairement au
lowering avec les refus indiqués ci-dessus. Les sorties temporaires sont hors
du dépôt.

## Portée et limites

W75 corrige l'identité du composant bind-group pour `PathStencilCoverDstRead`.
Ce replay confirme le cas positif déjà couvert par la régression native W75,
et montre que les GMs plus larges sélectionnés ne sont pas encore dans ce
contrat de route. Aucun PNG suivi, score, seuil, rebaseline ou fichier de
`gpu-renderer-scenes` n'a été modifié.
