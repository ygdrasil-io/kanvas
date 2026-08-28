# W36 — Blocker : LinearGradient stroke à trois stops

## Décision

W36 ne promeut pas de contour `DrawRect` avec un `LinearGradient` à trois
stops. L'ABI native pourrait le rendre, mais l'admettre maintenant élargirait
implicitement le contrat W32, qui ne prouve le troisième stop que pour le
`FillRect` public borné.

## Chemin constaté

`GPUPreparedStrokeRectLowerer` accepte actuellement un gradient linéaire
`CLAMP` sRGB de un à seize stops, puis abaisse le `DrawRect` stroke en quatre
`FillRect` device. Le descripteur `GPUMaterialDescriptor.LinearGradient` est
conservé sur les quatre bandes.

Le pipeline CorePrimitive possède effectivement l'ABI nécessaire : payload
gradient de 592 octets, `stop_count`, 512 octets de `stop_data` et WGSL
multi-stop. Ce constat est une capacité interne, pas une nouvelle promesse
Surface.

## Pourquoi la promotion serait incorrecte

L'analyse décide l'admission de trois stops à partir du `FillRect` normalisé :
non-AA, CTM identité, `CLAMP` et `localMatrix` identité. Après l'abaissement
des quatre bandes, elle ne distingue plus le `DrawRect` stroke public du
`FillRect` public W32. Un cas W36 à trois stops pourrait donc satisfaire cette
garde par accident, tout en contredisant le périmètre explicitement annoncé
pour W32.

Les diagnostics de `GPUPreparedStrokeRectLowerer` restent corrects pour la
translation d'un gradient (`unsupported.stroke.rect_transform`), AA
(`unsupported.stroke.rect_anti_alias`) et les matériaux hors contrat
(`unsupported.stroke.rect_material`), mais ils ne ferment pas ce contournement
spécifique de nombre de stops.

## Correctif requis avant une tranche positive

Un futur changement de production devra préserver l'identité de l'opération
publique jusqu'à la décision d'admission, ou porter explicitement une
capacité distincte `DrawRect.stroke` à trois stops avec ses propres gardes et
tests de refus. Cette décision devra être TDD, suivre le chemin Surface
complet et générer une nouvelle preuve CPU/GPU. W36 ne modifie ni le lowerer,
ni W32–W35, ni le catalogue promu.

Il n'y a donc ni bundle généré ni promotion pour cette vague : le fichier est
un blocker d'architecture de route, pas une preuve de support.
