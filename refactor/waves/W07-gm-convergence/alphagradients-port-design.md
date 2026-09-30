# W7 — Port fidèle d'alphagradients et domaine explicite des GM

30 septembre 2026. Base `1915794f71cc69b2c04718e9ab5b65423ee76d60`, draft
[#2423](https://github.com/ygdrasil-io/kanvas/pull/2423). Branche
`codex/w7-alphagradients-port`. Pilotage délégué, W7 reste ouvert.

## Intention et choix

Les trois capacités moteur sont livrées : politique alpha, composition
encodée et Rect hairline entier. Le corpus reste à198/443 rendus ;
alphagradients est à33,882161% des pixels ±2/canal. Il faut maintenant porter
la scène fidèle, pas modifier le moteur ni remplacer ses contours dans
l'adaptateur. La source épinglée a été relue :
[alphagradients.cpp](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/alphagradients.cpp).
La provenance exacte du PNG historique reste inconnue ; l'audit précédent
établit séparément son comportement intérieur en sRGB encodé.

Trois possibilités : changer globalement le harnais en encoded (rejeté,
modifie630 autres scènes), choisir par nom dans le renderer (rejeté,
sémantique cachée), ou déclarer le domaine dans SkiaGm. La troisième est
retenue : petit contrat partagé, diffusé à chaque création de Surface.
Le lot est architectural à cette seule frontière du harnais ; pas de nouveau
sous-système renderer. Exécution Subagent-Driven Development conservée.

## Contrat

`SkiaGm.compositionDomain: CompositionDomain` vaut LINEAR par défaut.
La déclaration du GM prévaut sur `RenderConfig.compositionDomain` fourni au
harnais ; les autres champs du config sont conservés par copy. Ce choix est
intentionnel : le domaine est une propriété de la scène testée, pas un réglage
de performance ambiant. Pas de matching sur le nom ni de changement de défaut
Surface/RenderConfig hors du harnais.

Une fonction interne `SkiaGm.compositionConfig(base: RenderConfig =
RenderConfig.DEFAULT): RenderConfig` dans SkiaGm.kt ne remplace que ce champ.
Elle alimente SkiaGmRenderer.render, renderTerminalAttempt, inventoryEvidence
et la capture directe de SkiaGmParityCheckpoint. Runner, scanner et generator
délèguent déjà au renderer ; le routage est vérifié statiquement, sans nouveau
test de forwarding. Les Surfaces internes aux autres GM ne sont pas migrées.
Le checkpoint ajoute le domaine déclaré à chaque ligne GM, avant la copie
d'identité du watchdog, pour que les timeouts conservent aussi cette provenance.
Il ne change ni identité de registre ni timeout ni dénominateur.

Un cinquième chemin de recréation a été trouvé pendant la lecture :
DiagnosticRunner -> OpInspector.renderPartial utilise RenderConfig.DEFAULT.
Une Task2 séquentielle transmet le config effectif dans RunnerInput,
OpInspector.inspect et chaque replay Surface. Le Runner GM fournit le même
config projeté que son rendu principal. Les appels existants gardent DEFAULT.
La correction est limitée au domaine/config du replay ; les limites héritées
SetClip ignoré et replay partiel des layers restent explicites, sans promesse
de diagnostic fidèle universel.

AlphaGradientsGm déclare SRGB_ENCODED. Les12 paires, dimensions640×480,
rectangles300×30, translation initiale(10,10), pas vertical38 et horizontal310,
gradient diagonal et ordre fill puis stroke noir restent identiques à la
source. Colonne gauche STRAIGHT, droite PREMULTIPLIED ; paints fill et stroke
explicitement non-AA, strokeWidth0 conservé. Aucun remplacement par des bandes
dans le port, aucun seuil/référence/exclusion modifié, minSimilarity0 historique
conservé (ce seuil n'est pas une preuve de parité).

## Preuve de comportement

Un test public natif rend le vrai GM via SkiaGmRenderer. Attentes indépendantes
avant GPU : pixel(160,25) RGB191±2, (470,25)255, alpha255 ; contour noir
exact à(160,10), voisin blanc exact(160,9). Une table640×480 entière utilise
les24 rectangles aux coordonnées littérales, fond blanc et ring noir exact.
Intérieurs : F64, t=((xLocal+.5)*300+(yLocal+.5)*30)/90900 ; a=1−t.
À gauche srcPremul=a*((1−t)*c0+t*c1), à droite srcPremul=a*c0 ; composition
sur blanc C=srcPremul+t. Arrondi8bits et écart RGB≤2, alpha exactement255.
Aucun helper produit, PNG ou résultat GPU ne construit cette attente. Second
render identique, zéro refus/diagnostic et dispatch natif non nul. Cette
tolérance est celle du contrôle de scène, pas un élargissement des proofs ou
du CompositionEnvelope existant.

Témoins publics complémentaires dans le même fichier : GM par défaut et GM
encoded dessinent noir alpha128 sur blanc2×2, pixels LINEAR187±2 contre
encoded127±2, même si le config appelant demande l'autre domaine. Budget
configuré1 conserve un refus puis rendu sain après retour au config normal.
Un GM encoded avec AA interdit doit refuser par render, terminal attempt et
inventoryEvidence, puis laisser réussir un GM encoded sain. Ces témoins
exercent la Surface réelle, pas un mock de config ni une inspection de plan.

Le Minor reporté de #2423 est corrigé mécaniquement dans le helper des grilles
hairline : déplacer l'assertion de largeur avant la boucle des caractères.
Rejouer sa classe publique13cas, sans ajouter un test d'infrastructure du helper.

La Task2 ajoute un témoin de pixels PNG issus du replay natif réel : noir
alpha128 sur blanc2×2 en encoded, par le chemin séquentiel (deux ops) et le
chemin checkpoint (plus de50ops, préfixe blanc opaque). La référence de ce
diagnostic est volontairement blanche pour déclencher les captures avant/après.
Le PNG avant reste blanc255, le PNG après vaut127±2, alpha255, comme le rendu
principal ; LINEAR187±2 est discriminé. Aucun mock, introspection de config
ou test de source ; lecture des artefacts publics de DiagnosticRunner.

## Validation et livraison

Baseline fraîche GmCanvasSurfacePixelTest3/3, sorties0. Le renderer produit
et ses69tests de #2423 sont inchangés ; ce lot n'en déduit pas une suite globale
verte. RED du vrai GM sur le parent avant édition du port, puis tests publics
du lot+3contrôles GmCanvas et13hairline. Aucun test nouveau d'infrastructure.
Review Sol de tâche puis review finale indépendante Astra, au plus une vague
finale de correction et contre-relecture ciblée. Un corpus complet631/443
après le code final,30s par cas, compare toutes les197 anciennes images autres
qu'alphagradients et toutes les références/scopes/seuils. Inspecter le PNG et
les métriques d'alphagradients avant de publier un gain, attribué au port.
Globale héritée678PASS/40FAIL/1interrompu/25nonatteints reste rouge/incomplète ;
pas de nouvelle globale Kanvas sur un moteur inchangé.

Suivi dans refactor, draft empilée sur2423, pas de merge ni clôture W7. Fonts,
codecs/décodage externe, jpg-color-cube et scores historiques restent exclus.
