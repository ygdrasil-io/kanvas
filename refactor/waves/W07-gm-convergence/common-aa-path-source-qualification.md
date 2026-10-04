# Qualification common AA PATH source — corpus sans gain

État au 4 octobre 2026 : Task1 est qualifiée sur son périmètre natif focalisé ; le census complet Task2 est terminé, audité et postscellé. Le résultat du corpus est nul en admission et en pixels. Ce constat n’invalide pas la fondation native, mais ne lui attribue aucun ROI GM. La relecture documentaire et la publication restent en attente. W7 reste actif : aucun global GREEN, merge, ni W7 terminé n’est revendiqué.

## Contrat et preuves natives

Le produit mesuré est W6a `6ed6737599673a442f576593fdeaae868f44bf89e0157deaffb57da6ee0ba562` / W4d `1fcb3a87283a58be8a97d8ee83271e3b2121eb32d60042ce4f59b3ed4f04ff62`, sur la branche locale `codex/w7-common-aa-layer-source`, base `5df3c9e8c22f5b5e4de9e23739113bb4a678b026`. Le contrat sélectionne la source AA ordinary commune : root conserve `ownsW6b`, child plain est admis via le même contrat si `original == unfiltered`. Les chemins deferred et historical FILL restent prioritaires. Root plain-layer-owned sans `ownsW6b` est différé ; filtres propres, shader/blend/AA étrangers, fractional clip et encoded layer restent refusés. Aucune lane nouvelle n’est ajoutée.

Après nettoyage, le run focalisé est 9/9 PASS, avec 108 buffers natifs complets et 10 refus exacts. Source-alpha, restore-alpha, ordre, translation, hint hors clip, hard clip, replay et fresh surface sont couverts. Budget B=25 312 et B−1=25 311 du composé W5g (Solid16 + tail-alpha16), sentinel et récupération sont qualifiés. Fixtures `b6206` / `490760` et inverse SHA `96cd8349` sont inchangés. Les archives et le reçu `cleanup-qualified-audit.json` sont sous `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/`. La compilation W6a effective n’a plus le safe-call warning ; les warnings native-access, Unsafe et Gradle persistent.

La relecture Sol de Task1 est Approved ; re-review M1/M2 : C0/I0/M0. Le contexte compte 119/120 : l’unique échec historique reste `emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers()` (exception attendue absente, `readPixels` true), identique au contexte SolidRect hors horodatage. Les classes ordinary 5/5, AA FILL 9/9, bounds 9/10, restore 14/14, deferred 72/72 et budget 10/10 ont ces résultats historiques ; les branches capability existantes ne constituent pas une preuve native stricte de chaque positif.

La tentative globale antérieure au nettoyage portait sur W6a f152 / W4d 1fcb : borne 240 s atteinte, 727 START/END, 690 SUCCESS, 36 FAILURE et 1 SKIPPED à l’interruption de `W5eDecodedImageSurfacePixelTest.formatsAlphaAndColorSpaceMatchOracle()`. XML finaux absents, `ownPGID` vide. Les 36 identités avaient déjà été observées dans la tentative SolidRect, mais aucun rerun du parent courant ne prouve l’absence de régression. Cette suite n’a pas été répétée après le safe-call et ne qualifie pas globalement le hash W6a courant. Les détails et hashes restent dans `task-1-controller-validation.md` et `global1-incomplete-audit.json`.

## Census et delta mesuré

Snapshot frais [`common-aa-path-source-corpus.json`](common-aa-path-source-corpus.json) : 740 716 octets, SHA-256 `e8528b81c9167ac693f83de4348262434573ff71351e0ad2921f7a9a8af732ad`. Les cinq journaux utilisent le renderer commit `bf38d08be20edaa487ae2fddab43dda03fd4f268`, registry 631 / SHA-256 `4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7`, timeout par GM 30 s, borne 240 s, `images=false`, Mac OS X / aarch64 / Java 25.0.1. L’agrégation utilise le script inchangé ; aucune exclusion nouvelle.

| Slice | Exit wrapper | PGID | SHA-256 log | SHA-256 exit | SHA-256 journal | Postseal : fichiers / manifest SHA-256 |
| --- | ---: | ---: | --- | --- | --- | --- |
| [0,200) | 0 | 94871 | `5a16f0305b1fc668b42e8e70527a8f0aeeb2466199c891d6a8c08f1f5c1cbc5a` | `62835cc6f462a5299f6c6a235f6dd735c3153c524d76a61af6e5d4d508fa3275` | `a5675750ec30229dc99e896ee802acbc914d76d145b2521d5ab25f718efdfe2b` | 7706 / `aa95bb7248495f5d26ed0cefd6983797617bd85cb9433530bcf1d510332b43ec` |
| [200,400) | 0 | 95880 | `4a55741c8278c7285b1df67bb16999c4194d339f7909c9b36b90bc766b25874a` | `67474a269d08f96187abb980982448bf4bf38d51a7c66bdd4b14b509e4aa2c7c` | `ebcb1b2de22cc4e7844d8effed1c82e55c1a722780f873bcd31a4fb8c91aaa99` | 7707 / `59073aea9d66ce4d27ebcbbd78d539bf708e9cbeb5d652dd6d6bf73be93b1b53` |
| [400,607) | 0 | 96542 | `69107ea924700520705411ec09327cc4286f4220a2d28f4a450b7d55f77b874e` | `88d3515f0db65e1e4fe2637fabee9af871c5c32c354b45ff867439d64aa65cb6` | `693e2a5b651ec7ea87a47c7a7d13186655f4b74917422449431fe7b548e4cd3b` | 7708 / `4dadff9a6e93f1ab7011d7425baaf9d1e2c5817ce876a4b2a1f64b9291f24556` |
| [607,608) | 1 | 97048 | `fb38cc4ab47e54ef6dc424037c15988c72df25717eb27ed2ba6c96dcc5bf7f29` | `2dd57dc1377183966e5f872f86f8c49da8d9217a07be61320fd96bcae444f311` | `96afde93f4c273f9cf81fe2043fd4f1655c973a4e0ef9dd4ce1072bcb215e273` | 7709 / `a3d4a2f76d3cf6e791e3c616a36f9e211d2a7656e558aae5ca5e5fdb1f49827b` |
| [608,631) | 0 | 97560 | `584172d7c4c6716a67315737bfde310a0d6d8156fc53d890c4264282715ceaf5` | `160e55c2f98ffbc54ba5d352a6871597ab2f3c47c5295b4a344b45f9580e7565` | `ece558b122002fc4be36fa63e90152b7cbd7942eb6713069ca3f1196f21b2237` | 7710 / `f1265f5a7ab9c54d66d21852bbb91809a1942e0dc72cd3e26088b80fcbd42af7` |

Chaque tranche a été exécutée séquentiellement, puis son log, exit, journal, inventaires et `ownPGID` audités intégralement avant postseal distinct. Les durées Gradle sont 64/65/30/39/13 s. Pour [607,608), le watchdog Java a entraîné exit 1 du wrapper/Gradle, sans timeout externe et sans enregistrement complet ; ce résultat reste dans le corpus. Les quatre autres tranches sont complètes, exit 0. Reçus détaillés : `census-{range}-audit.json` dans les archives précitées. Le helper wrapper SHA-256 `b06c94d3775c5763b4153e256b5cf304086a38c7cad8e4dd97e56b09a4e2a4bd`, init SHA-256 `8d541cd70814c6e93afc25558a1f86c5f78e1a7df143f834910566eb4ca4d917`. Les 814 PNG, 559 scores, 1004 références physiques et 631 statuts n’ont pas changé.

Les 631 champs et leur présence, hormis `elapsedMs` / `renderMs`, sont identiques au snapshot SolidRect (`63f69928d3359eb5ed9f8a71488a92a46389b904ebbcbef2bba67a89d9cc8bb5`) et au snapshot transversal (`ecef91f79e25940f9fabe2e2c624734a9fa2bcaa861391f91f0a8c7b4dcd74f8`). Il n’y a aucun delta observé d’admission ou de pixels.

| Mesure, scope eligible 443 / registry 631 | Résultat |
| --- | ---: |
| Entrés selon la règle | 393 = 392 `attempted=true` + timeout render vertices607 sans ce flag |
| Rendus / comparés | 220 / 197 |
| Outcomes | 197 compared, 172 render_failed, 50 setup_failed, 15 rendered_uncompared, 8 reference_dimension_mismatch, 1 timeout |
| Cas ≥95 % à tolérance ±2 | 65 |
| Cas ≥99 % à tolérance ±2 | 49 |
| Médiane sur les 197 comparés | 77.91666666666667 % |
| PASS déclaré historique / seuil zéro | 195 / 343 |

Le scope est `631 − 133 fonts − 54 codecs − 1 jpg-color-cube`; aucune exclusion nouvelle. Le timeout vertices607 de 30 s reste inclus dans 443 ; son exit Gradle 1 n’est pas un PASS. Les 195 PASS historiques et 343 seuils à zéro ne démontrent pas la parité.

Les 17 premiers refus historiques `w6a.layer.unsupported_child` sur PATH/AA se répartissent en 14 root / 3 child, dont 12 filtered root / 5 unfiltered. Ce ne sont ni 17 gains candidats ni 17 causes indépendantes. Le détail des noms et scopes figure dans `corpus-comparison-audit.json`. Les améliorations historiques tinybitmap, child_sampling et 3x3 ne sont pas attribuables à ce lot.

Conclusion de mesure : une fondation native réelle a été obtenue sans retour sur le corpus actuel. Toute suite doit partir d’un verrou démontré dans un GM, sans déduire un effet des seuls refus supprimés.

## Livraison

Deux branches restent locales : `codex/w7-common-aa-path-source` à `5df3c9e8c22f5b5e4de9e23739113bb4a678b026` (SolidRect), puis `codex/w7-common-aa-layer-source` basée sur cette première. Le parent existant est le draft #2447 : https://github.com/ygdrasil-io/kanvas/pull/2447. Le push public a été rejeté avant création ; aucun retry ni nouvelle PR n’a été effectué, et l’accord explicite requis reste absent. Il n’y a pas de livraison revendiquée.

**État :** Step1 terminée et documentée ; revue indépendante Step2 en attente ; publication Step3 en attente. W7 reste actif, sans global GREEN, merge ni clôture.
