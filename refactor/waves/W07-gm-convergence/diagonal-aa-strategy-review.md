# W7 — revue Astra du diagnostic et priorité suivante

3 octobre 2026, package d12749b64..85a673093. Revue unique finale et stratégique.
**Diagnostic conforme, Quality Approved, C0/I0/M2 hérités.** Pas de nouveau
blocage, de gain corpus, de merge exécuté ou de GREEN global. Astra n'a lancé
aucun runtime ; elle accepte les trois archives et la custody du contrôleur.

M1 helpers des trois ancres dupliqués : consolidation lors d'un futur changement
comportemental du fichier, blob qualifié conservé maintenant. M2 warnings
JDK/Unsafe/Gradle : dette héritée suivie, aucun chantier toolchain dans ce lot.
Identités natives d'attachments, sample positions, précision universelle et
backend de génération Skia restent UNKNOWN. Fonts/codecs/jpg hors périmètre ;
Picture/RRect/inverse et les suites globales restent ouverts.

## Décision de pilotage

**Différer l'AA analytique convexe** : TeenyStrokes est déjà99.5228%, SSIM.999264,
dans LINEAR. Le modèle d'aire donnerait sur blanc environ238/92 dans ce domaine,
contre218/28 en encoded et223/31–32 dans la référence. Ce sont des prédictions,
pas des résultats natifs nouveaux ; une amélioration d'aire seule peut dégrader
le corpus actuel. Aucun changement de domaine GM/default n'est décidé ici.

Le corpus parent631/443 donne194 comparés,175 render_failed,50 setup_failed,
15 rendered_uncompared,8 mauvaises dimensions et1 timeout. Parmi les refus :
36 child layers,18 path resource limits,14 runtime effects non enregistrés.
Parmi les comparés : IMAGE37/44 sous95%, COMPOSITE36/41, PATH18/29, BLUR18/20.
Ces comptes proviennent du snapshot parent inchangé, pas d'un nouveau run.

Le groupe child layers se scinde notamment en deux sous-groupes de9
PATH/AA/WINDING/ownsW6b=true avec les mêmes childReasons, selon présence ou
absence d'un payload filtre sur le draw fautif. Le groupe sans filtre contient
rrect_blurs et blurcircles2. Les18/36 sont des bornes d'opportunité, pas des
corrections garanties ni la preuve d'une cause unique.

Lot choisi : **source couleur PATH AA ordinaire FILL/STROKE solide SrcOver
dans une frame W6**, avec isolation AA4→1x existante. La garde !ownsW6b et
la restriction FILL sont deux frontières ; retirer seulement la première
ne rendrait pas les séparateurs PATH/STROKE des vrais GM. Capturer exactement
les draws refusés avant patch. Les9 occurrences réellement filtrées sont
exclues de cette extension ; ni filtre dépouillé ni provenance Rect empruntée.

[Design](w6-ordinary-aa-path-source-design.md) et
[plan](w6-ordinary-aa-path-source-plan.md) fixent des témoins RED publics,
deux scènes complètes avec contenu vérifié, même sampling/budget/domaines,
contrôles natifs et un critère d'arrêt si une autre famille substantielle
bloque les témoins. Pas de capacité présentée comme parité.

## AA différé : garde-fous conservés

Une future couverture convexe devra être certifiée dans math, porter un
contrat scalaire GPU/ABI/seal distinct, sélectionner la scène entière sans
mélange implicitement corrélé, intégrer l'aire de tout le contour et tester
coins/minceur. Deux demi-couvertures dupliquées produisent3/4 en SrcOver scalaire
contre1/2 avec samples root corrélés : ce serait un changement de contrat,
pas une optimisation transparente. Qualifier le domaine local de référence
avant activation corpus ; aucun default global déduit de Teeny.
Ces détails restent archivés dans le rapport privé complet Astra ; aucun
algorithme convexe n'est commandé par le prochain plan.
