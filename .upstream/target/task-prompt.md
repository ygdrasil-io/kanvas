Use skills:
- kanvas-wgsl-pipeline-architect
- kanvas-linear-ticket-executor
- kanvas-pm-demo
- linear:linear
- github:github
- github:gh-address-comments
- github:gh-fix-ci
- github:yeet

Objectif:
Avancer automatiquement la prochaine phase non clôturée du projet Linear
"Kanvas - WGSL Pipeline Target", jusqu’à PR, review, correction, CI et merge
sur master si tout est OK.

Contexte:
- Le worktree est déjà créé automatiquement.
- Utilise le worktree courant.
- Ne crée pas de nouveau worktree.
- Le repository courant est Kanvas.
- La branche de travail peut être créée dans ce worktree si nécessaire.

Projet Linear:
- Project: "Kanvas - WGSL Pipeline Target"
- Cherche la prochaine milestone/ticket non clôturée dans l’ordre M0 → M11.
- Ignore les milestones/tickets déjà Done, Closed, Canceled ou déjà mergés.
- Ne traite pas les epics parent directement; traite le ticket milestone actif.
- Si un ticket est In Progress mais non terminé, continue celui-là.
- Sinon prends le premier ticket Ready/Backlog/Planned non clôturé dans l’ordre des milestones.

Must read:
- AGENTS.md
- CLAUDE.md
- .upstream/target/high-performance-wgsl-pipeline-target.md
- .upstream/target/linear-agent-methodology.md
- Le ticket Linear sélectionné
- Son parent epic Linear
- Les GitHub issues/PRs liées dans Linear
- Les docs externes explicitement référencées par le ticket

Workflow obligatoire:
1. Identifier la prochaine phase non clôturée dans Linear.
2. Vérifier que le ticket est prêt:
    - scope clair;
    - parent epic;
    - milestone;
    - acceptance criteria;
    - tests ou evidence attendue;
    - non-goals;
    - dépendances bloquantes.
3. Si le ticket n’est pas prêt:
    - ne pas implémenter;
    - commenter Linear avec ce qui manque;
    - répondre avec le blocage.
4. Si le ticket est prêt:
    - passer le ticket en In Progress si possible;
    - créer une branche dédiée depuis master si nécessaire, avec un nom du type:
      <linear-id>-<short-slug>
5. Implémenter uniquement le scope du ticket sélectionné.
6. Respecter strictement les non-goals:
    - ne pas porter Ganesh ou Graphite;
    - ne pas reconstruire SkSL compiler/IR/VM;
    - ne pas traiter les plans archivés comme backlog actif;
    - ne pas ajouter de substituts courts pour les sujets dependency-gated;
    - ne pas élargir au milestone suivant.
7. Lancer les validations utiles:
    - d’abord les tests ciblés;
    - puis un check plus large si le changement touche un contrat partagé.
8. Committer les changements.
9. Pousser la branche.
10. Ouvrir ou mettre à jour une Pull Request GitHub liée au ticket Linear.
11. Faire une review indépendante:
- si les outils multi-agent sont disponibles, lancer un review subagent;
- sinon faire toi-même une passe de review séparée en mode code-review strict.
12. Corriger tous les retours bloquants dans la même branche.
13. Vérifier la CI GitHub.
14. Si review OK et CI verte:
- merger la PR dans master;
- mettre le ticket Linear en Done;
- commenter Linear avec PR, commits, validations et evidence PM.
15. Si review ou CI bloque:
- ne pas merger;
- commenter Linear avec le blocage précis;
- répondre avec l’état exact.

Autorisation explicite:
- Tu peux créer une branche.
- Tu peux pousser la branche.
- Tu peux ouvrir une PR.
- Tu peux corriger la PR.
- Tu peux merger dans master uniquement si review OK et CI verte.
- Tu peux mettre à jour Linear.

Réponse finale attendue:
- Ticket Linear traité;
- milestone traitée;
- PR URL;
- status review;
- status CI;
- merge status;
- commit final ou raison du non-merge;
- validations exécutées;
- evidence PM;
- risques ou blocages restants.