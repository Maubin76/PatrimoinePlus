# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Un seul utilisateur, Mathieu, propriétaire et développeur de l'application. Deux situations d'usage, aussi fréquentes l'une que l'autre :
- **saisie au fil de l'eau** : juste après une dépense, debout, souvent d'une main, en quelques secondes ;
- **revue posée** : de temps en temps, il saisit en lot, contrôle les soldes contre les relevés de la banque et regarde la répartition et l'évolution.

## Product Purpose
Suivre ses dépenses et son patrimoine (comptes courants, livrets, compte-titres PEA) sur son téléphone, avec des soldes justes par construction. Le succès : le patrimoine total affiché est exact sans effort de vérification, et noter une dépense ne prend que quelques secondes.

## Positioning
Outil personnel, local et sans compte : aucune agrégation bancaire, aucun service distant. Tout est recalculé à partir des opérations saisies (comptabilité à jambes ; un virement entre ses propres comptes est saisi une seule fois). Le compte-titres est suivi en valeur, à partir de cours relevés à la main.

## Operating Context
- Téléphone Android 16, utilisé aussi en public : il faut pouvoir **masquer les montants** (mode discret, mémorisé entre les sessions).
- Verrou biométrique avec repli sur le code du téléphone.
- Les relevés de la banque et l'application de la banque servent de référence pour contrôler les soldes et saisir les cours.

## Capabilities and Constraints
- Voir `doc/SPEC.md` pour le modèle de données et le plan des étapes.
- Données uniquement en base Room locale ; export et import JSON manuels.
- Une seule devise : EUR, au format français (`1 234,56 €`).
- Textes d'interface en français.
- Jetpack Compose + Material 3, minSdk 30.
- Hors version 1 : budget par catégorie, opérations récurrentes, pointage, sous-catégories.

## Evidence on Hand
Aucune donnée : l'application démarre vide. Les écrans ne doivent jamais afficher de chiffres inventés présentés comme réels.

## Product Principles
1. **Exact avant joli** : un chiffre affiché est toujours calculé, jamais saisi ni mis en cache.
2. **La saisie rapide prime** : une dépense se note en quelques gestes, d'une main.
3. **Lisible d'un coup d'œil** : le montant important de chaque écran se voit en premier.
4. **Discret par choix** : les montants peuvent être masqués à tout moment.

## Accessibility & Inclusion
Pas de besoin particulier. Respect standard : tailles de police système, contrastes suffisants, aucune information portée par la seule couleur (le signe d'un montant reste écrit).
