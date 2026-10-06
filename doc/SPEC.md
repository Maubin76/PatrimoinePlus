# PatrimoinePlus — Spécification

Version 1 — 2026-10-06 — validée à l'étape 0 (cadrage).

Application Android personnelle, mono-utilisateur, de suivi du patrimoine et des
dépenses : comptes courants, livrets d'épargne, compte-titres (PEA).
Données **uniquement en base locale** (Room) ; aucune synchronisation, aucun
service distant, aucune importation de données existantes : l'application
démarre vide.

---

## 1. Besoins

### 1.1 Vues (version 1)

| Vue                         | Contenu                                                                                   |
|-----------------------------|-------------------------------------------------------------------------------------------|
| Mois par mois (par compte)  | Solde d'ouverture, entrées, dépenses, épargne, virements internes, solde de clôture ; liste des opérations du mois |
| Répartition par catégorie   | Dépenses nettes du mois par catégorie (montant, part en %), sur un compte ou tous comptes |
| Évolution d'un compte       | Série du solde de clôture mois par mois                                                   |
| Patrimoine total            | Valeur à date (compte-titres valorisé), répartition par type de compte, évolution mensuelle |
| Livret                      | Versements, retraits, intérêts, relevés et écart relevé / solde calculé                   |
| Compte-titres               | Espèces, actifs détenus (quantité, PRU, dernier cours, valeur, plus-value latente), plus-value réalisée, relevés de cours |

Hors version 1 (liste « à faire ») : budget mensuel par catégorie, opérations
récurrentes, pointage avec le relevé bancaire, sous-catégories, ventilation
d'une opération sur plusieurs catégories, multi-devises.

### 1.2 Règles fondamentales

1. **Aucun solde ni total stocké.** Tout solde, total, synthèse ou série se
   recalcule à partir des opérations (jambes), ordres et relevés.
2. **Le solde d'ouverture d'un mois est la clôture calculée du mois précédent.**
   Il n'est jamais saisi.
3. **Les comptes et catégories sont des données**, créées et modifiées dans
   l'application ; rien n'est codé en dur hormis les propositions du premier
   lancement.
4. **Un virement entre deux de mes comptes est une seule opération à deux
   jambes** de somme nulle, saisie une seule fois.
5. **Le compte-titres est suivi en valeur** (quantités × cours relevés), pas
   seulement en montants versés.
6. **Montants en centimes (`Long`)**, jamais en `Double`. Pas d'objet global :
   dépendances injectées, logique métier pure et testable.
7. **Un relevé ne remplace jamais un solde calculé** : il sert de contrôle
   (l'écart est affiché) ou, sur demande explicite, à générer une opération.

### 1.3 Hypothèses retenues

- Devise unique : EUR.
- Pas de carte à débit différé : une dépense par carte est datée du jour du
  débit sur le compte courant.
- Pas de date de départ globale : chaque compte reçoit une opération
  « Solde initial » à la date de son choix (par défaut : le 1er du mois courant).
- Téléphone : Android 16 (API 36). minSdk 30 conservé comme demandé ; le
  téléphone est couvert.

---

## 2. Modèle de données

### 2.1 Tables Room

Identifiants : UUID sous forme de texte. Dates : `LocalDate` stockée en texte
ISO (`2026-10-06`), ce qui permet le tri et les requêtes par intervalle.
Horodatages : millisecondes epoch (`Long`).

**`account`** — compte
| Colonne      | Type    | Remarque                                         |
|--------------|---------|--------------------------------------------------|
| `id`         | TEXT PK | UUID                                             |
| `name`       | TEXT    |                                                  |
| `type`       | TEXT    | `CHECKING` (courant), `SAVINGS` (livret), `BROKERAGE` (compte-titres) |
| `sortOrder`  | INTEGER | ordre d'affichage                                |
| `archived`   | INTEGER | masqué des listes de saisie, conservé dans l'historique et le patrimoine |
| `createdAt`, `updatedAt` | INTEGER |                                    |

**`category`** — catégorie
| Colonne      | Type    | Remarque                         |
|--------------|---------|----------------------------------|
| `id`         | TEXT PK | UUID                             |
| `name`       | TEXT    |                                  |
| `icon`       | TEXT    | clé d'icône Material             |
| `kind`       | TEXT    | `EXPENSE` ou `INCOME`            |
| `sortOrder`  | INTEGER |                                  |
| `archived`   | INTEGER |                                  |

La nature « épargne » disparaît : l'épargne se reconnaît à la structure de
l'opération (virement vers un livret ou un compte-titres), pas à une catégorie.

**`transaction`** (table `txn`) — opération
| Colonne      | Type        | Remarque                                       |
|--------------|-------------|------------------------------------------------|
| `id`         | TEXT PK     | UUID                                           |
| `date`       | TEXT        | `LocalDate`                                    |
| `kind`       | TEXT        | `STANDARD`, `TRANSFER`, `TRADE`, `OPENING`     |
| `label`      | TEXT        | libellé                                        |
| `categoryId` | TEXT FK ?   | obligatoire pour `STANDARD`, nul sinon         |
| `assetId`    | TEXT FK ?   | facultatif : dividende rattaché à un actif     |
| `notes`      | TEXT ?      |                                                |
| `createdAt`, `updatedAt` | INTEGER |                                    |

**`posting`** — jambe
| Colonne         | Type    | Remarque                                     |
|-----------------|---------|----------------------------------------------|
| `id`            | TEXT PK | UUID                                         |
| `transactionId` | TEXT FK | suppression en cascade                       |
| `accountId`     | TEXT FK | suppression interdite tant qu'il y a des jambes |
| `amountCents`   | INTEGER | signé : positif = entrée sur le compte       |

**`asset`** — actif (titre, ETF…)
| Colonne    | Type    | Remarque          |
|------------|---------|-------------------|
| `id`       | TEXT PK | UUID              |
| `name`     | TEXT    |                   |
| `isin`     | TEXT ?  |                   |
| `archived` | INTEGER |                   |

**`trade`** — ordre de bourse
| Colonne         | Type    | Remarque                                         |
|-----------------|---------|--------------------------------------------------|
| `id`            | TEXT PK | UUID                                             |
| `transactionId` | TEXT FK | cascade                                          |
| `assetId`       | TEXT FK |                                                  |
| `side`          | TEXT    | `BUY`, `SELL`                                    |
| `quantity`      | INTEGER | en dix-millièmes d'unité (fractions d'ETF)      |
| `unitPrice`     | INTEGER | cours en dix-millièmes d'euro                    |
| `feesCents`     | INTEGER | frais                                            |

**`account_statement`** — relevé de solde (livret, ou contrôle d'un compte)
| Colonne        | Type    |
|----------------|---------|
| `id`           | TEXT PK |
| `accountId`    | TEXT FK |
| `date`         | TEXT    |
| `balanceCents` | INTEGER |

**`asset_price`** — relevé de cours d'un actif
| Colonne     | Type    | Remarque                        |
|-------------|---------|---------------------------------|
| `id`        | TEXT PK |                                 |
| `assetId`   | TEXT FK |                                 |
| `date`      | TEXT    |                                 |
| `unitPrice` | INTEGER | dix-millièmes d'euro            |

Écart avec la proposition initiale : la table unique `Valuation` (compte **ou**
actif) est scindée en `account_statement` et `asset_price`. Les deux n'ont pas la
même unité (solde en centimes, cours unitaire) et une colonne « l'un ou l'autre »
ne peut pas être garantie par une clé étrangère.

### 2.2 Types d'opérations et invariants

Vérifiés par la couche `domain` avant tout enregistrement (règles de saisie
testées) :

| `kind`     | Jambes                                         | Autres contraintes                              |
|------------|------------------------------------------------|-------------------------------------------------|
| `STANDARD` | exactement 1, montant ≠ 0                      | catégorie obligatoire ; dépense = montant négatif, revenu = positif ; un montant positif sur une catégorie de dépense est un remboursement (réduit la dépense) |
| `TRANSFER` | exactement 2, comptes distincts, somme = 0     | pas de catégorie                                |
| `TRADE`    | exactement 1, sur un compte `BROKERAGE`        | exactement 1 ordre ; achat : jambe = −(cours × quantité + frais) ; vente : jambe = +(cours × quantité − frais), arrondi au centime ; une vente ne peut excéder la quantité détenue à la date |
| `OPENING`  | 0 ou 1 (espèces)                               | pas de catégorie ; sur un compte-titres, 0..n ordres `BUY` sans jambe (positions initiales au PRU) |

L'utilisateur ne saisit jamais de signe : il est déduit du type d'opération.

### 2.3 Calculs (couche `domain`, pure)

- **Solde d'un compte à une date** = Σ jambes du compte datées ≤ date.
- **Mois M d'un compte** : ouverture = solde au dernier jour de M−1 ;
  clôture = solde au dernier jour de M.
  Ventilation des mouvements de M :
  - *entrées* : jambes `STANDARD` positives ;
  - *dépenses* : jambes `STANDARD` négatives ;
  - *épargne* : virements vers / depuis un `SAVINGS` ou `BROKERAGE` (net) ;
  - *virements internes* : autres virements (entre deux comptes courants) ;
  - *ordres* : jambes `TRADE` (compte-titres uniquement).
  Invariant testé : ouverture + Σ ventilation = clôture.
- **Synthèse par catégorie** : dépense nette par catégorie sur une période
  (remboursements déduits), triée, avec la part en %.
- **Compte-titres**, à une date :
  - espèces = solde du compte ;
  - par actif : quantité détenue, **PRU moyen pondéré frais inclus** (inchangé
    par une vente), dernier cours relevé ≤ date (à défaut le PRU, signalé
    « non valorisé ») ;
  - valeur = espèces + Σ quantité × cours ;
  - plus-value latente = Σ quantité × (cours − PRU) ;
  - plus-value réalisée d'une vente = produit net − quantité × PRU.
- **Livret** : écart = relevé − solde calculé à la date du relevé.
  Sur demande, génération d'une opération « Intérêts » égale à l'écart.
- **Patrimoine à une date** = Σ soldes des comptes courants et livrets
  + Σ valeurs des comptes-titres (comptes archivés compris).
- **Séries de graphiques** : une valeur par fin de mois, de la première
  opération jusqu'au mois courant ; répartition par type de compte.

---

## 3. Architecture

- Kotlin, Jetpack Compose + Material 3, minSdk 30, targetSdk 35, compileSdk 37,
  version catalog, KSP, Hilt (sous réserve de compatibilité avec AGP 9, vérifiée
  à l'étape 1), `navigation-compose`, coroutines et Flow.
- Package : `fr.maubin.patrimoineplus`.
- Couches :
  - `domain` : modèles, règles de saisie et calculs ; **aucune dépendance
    Android** ; testée par des tests JVM ;
  - `data` : entités et DAO Room, conversion entité ↔ modèle, dépôts ;
    schéma exporté dans `app/schemas`, toute évolution passe par une migration ;
  - `ui` : écrans Compose + ViewModels, qui appellent les dépôts et le `domain`.
- Horloge injectée (`Clock`) : aucun appel direct à `LocalDate.now()` dans la
  logique.

---

## 4. Principes d'interface

- Barre de navigation en bas : **Accueil**, **Comptes**, **Catégories**,
  **Réglages** ; bouton flottant « + » de saisie rapide sur Accueil et Comptes.
- Thème Material 3 aux couleurs dynamiques du téléphone ; mode sombre suivant
  le système.
- Montants au format français (`1 234,56 €`) ; négatifs en couleur d'erreur,
  positifs neutres ; chiffres tabulaires pour l'alignement.
- Saisie : sélecteur de type (Dépense / Revenu / Virement / Ordre selon le
  compte), montant sans signe, date (aujourd'hui par défaut), catégorie en
  grille d'icônes, libellé avec suggestions des libellés déjà saisis.
- Liste d'opérations : appui long pour la sélection multiple, suppression avec
  confirmation.
- Graphiques dessinés en Compose (Canvas), sans bibliothèque externe.
- Premier lancement : assistant proposant les comptes et catégories par défaut,
  modifiables avant création, puis la saisie des soldes initiaux.

### Propositions du premier lancement

- Comptes : Compte courant (courant), Livret A (livret), LDDS (livret), PEA
  (compte-titres).
- Catégories de dépense : Logement, Alimentation, Transport, Santé, Loisirs,
  Shopping, Abonnements, Impôts, Cadeaux, Divers.
- Catégories de revenu : Salaire, Intérêts, Dividendes, Remboursements,
  Autres revenus.

---

## 5. Protection contre la perte

- Sauvegarde automatique Android **désactivée** (`allowBackup=false`) : elle
  enverrait les données chez Google.
- **Export** de toute la base en un fichier JSON versionné, à l'endroit choisi
  via le sélecteur de fichiers Android.
- **Import** de ce fichier : remplace toutes les données après confirmation,
  dans une transaction unique (tout ou rien), après contrôle du format.
- Rappel discret à l'accueil si le dernier export date de plus de 30 jours.

---

## 6. Plan des étapes

| Étape | Contenu |
|-------|---------|
| 0 | Cadrage, cette spécification |
| 1 | Socle : Kotlin, Compose, Material 3, Hilt, KSP, navigation ; package `fr.maubin.patrimoineplus` ; versions SDK ; `allowBackup=false` ; écran de démarrage ; un test JVM ; README |
| 2 | Modèle : entités, DAO, base Room (schéma exporté), dépôts ; `domain` : règles de saisie, soldes mensuels, synthèses par catégorie, compte-titres (PRU, valeur, plus-values), patrimoine, séries ; tests JVM complets ; vérification par mutation ; `doc/TESTS.md` |
| 3 | Comptes et catégories : création, modification, archivage, ordre ; assistant du premier lancement et soldes initiaux |
| 4 | Compte courant, vue mois par mois : navigation entre mois, synthèse, saisie / modification / suppression (unitaire et multiple), répartition par catégorie, évolution du solde |
| 5 | Virements en une saisie ; livrets (intérêts, relevés, écart) ; compte-titres (actifs, ordres, relevés de cours, positions, plus-values) |
| 6 | Accueil : patrimoine total, répartition par type, évolution, accès rapide |
| 7 | Verrou biométrique avec repli sur le code du téléphone et re-verrouillage après 1 min en arrière-plan ; export / import JSON et rappel ; revue finale (README, TESTS.md, reste à faire) |
