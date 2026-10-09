# PatrimoinePlus

Application Android personnelle de suivi du patrimoine et des dépenses :
comptes courants, livrets d'épargne, compte-titres (PEA). Données uniquement
en base locale sur le téléphone, sans synchronisation ni service distant.

La spécification complète (besoins, modèle de données, plan des étapes) est
dans [`doc/SPEC.md`](doc/SPEC.md) ; la couverture des tests dans
[`doc/TESTS.md`](doc/TESTS.md).

## Construire

```sh
./gradlew :app:assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # tests JVM
./gradlew :app:assembleRelease      # APK optimisé (R8), signé avec la clé de debug :
                                    # app/build/outputs/apk/release/app-release.apk
```

## Socle technique

| Élément          | Choix                                                                    |
|------------------|--------------------------------------------------------------------------|
| Langage          | Kotlin (intégré à AGP 9), JVM 17                                         |
| Interface        | Jetpack Compose + Material 3, monochrome noir sur blanc, police Hanken Grotesk |
| Navigation       | `navigation-compose`, routes typées (`kotlinx.serialization`)             |
| Injection        | Hilt (KSP)                                                               |
| Asynchronisme    | Coroutines et Flow                                                       |
| Réglages         | DataStore Preferences (mode discret)                                     |
| Captures         | Roborazzi + Robolectric (rendu JVM, sans émulateur)                      |
| Base de données  | Room 2.8, schéma exporté (`app/schemas`)                                  |
| SDK              | minSdk 30, targetSdk 35, compileSdk 37                                   |
| Versions         | catalogue `gradle/libs.versions.toml`                                    |

`compileSdk` est à 37 parce que les bibliothèques AndroidX actuelles
(Compose 1.12, core-ktx 1.19) l'exigent ; cela ne change ni les appareils
compatibles (`minSdk`) ni le comportement à l'exécution (`targetSdk`).

## Architecture

Package racine : `fr.maubin.patrimoineplus`.

```
domain/   modèles, règles de saisie, calculs — Kotlin pur, sans Android, testé en JVM
data/     base Room (db), dépôts (repo), réglages (settings), sauvegarde (backup), démonstration (demo)
di/       modules Hilt (horloge, base, dépôts)
ui/       écrans Compose, ViewModels, thème, navigation, composants
data/settings/  réglages DataStore
```

Règles :

- aucun solde ni total stocké : tout est recalculé par `domain` à partir des
  opérations ;
- montants en centimes (`Long`), jamais en virgule flottante ;
- la date courante vient d'une `Clock` injectée, jamais de `LocalDate.now()`
  appelé directement dans la logique.

## Interface

Direction visuelle monochrome, noir sur blanc (blanc sur noir en mode sombre),
inspirée de deux maquettes de référence fournies le 2026-10-07.

- Palette fixe (`ui/theme/Color.kt`) : blanc, encre presque noire, gris
  secondaire, filets gris très clair ; aucun accent. Rouge discret réservé aux
  montants négatifs. Les couleurs dynamiques Material You sont écartées.
- Police unique Hanken Grotesk (OFL, `res/font`, licence dans
  `doc/licenses/`) ; titres et total en demi-gras ; chiffres à chasse fixe.
- Accueil : total centré en très grand, variation du mois, pastille « Calculé
  au … », courbe d'évolution du patrimoine (12 mois au plus), répartition par
  type en niveaux de gris dont chaque ligne ouvre l'onglet Comptes (la liste des
  comptes n'est pas répétée sur l'accueil).
- Navigation : les quatre onglets sont les pages d'un pager horizontal (on
  glisse de l'un à l'autre) ; pilule flottante à icônes fines dont l'indicateur
  suit le doigt (`ui/shell/AppFrame.kt`) ; bouton rond noir de saisie au-dessus,
  sur Accueil et Comptes ; le retour système ramène à l'Accueil.
- Composants : `AmountText` (masqué en mode discret, chiffres qui roulent),
  `InfoPill`, `SectionHeader`, `BorderedCard`, `ProportionBar`, `EmptyState`,
  `ScreenScaffold`.
- Apparence : Système (suit le téléphone), Clair ou Sombre, au choix dans
  Réglages ; mémorisée en DataStore, les barres système suivent.
- Comptes et catégories : listes réordonnables par glisser-déposer (actions
  « Monter » / « Descendre » pour TalkBack), création et modification en feuille
  du bas, archivage, suppression (simple, ou définitive avec double
  confirmation ; réaffectation des opérations pour une catégorie). Icônes de
  catégories au trait fin (`ui/icons/CategoryIcons.kt`).
- Vue d'un compte (`ui/account`) : page dédiée ouverte depuis l'onglet
  Comptes, adaptée au type du compte.
  - Courant : mois par flèches (sélecteur en touchant le mois), synthèse
    (ouverture, entrées, dépenses, épargne, clôture), dépenses par catégorie,
    courbe du solde sur 12 mois, opérations par jour avec « À venir » en tête.
  - Épargne : vue globale depuis l'ouverture, sans navigation par mois ; solde
    du jour et ce qui l'a constitué (solde initial, versements nets, intérêts
    et revenus), courbe du solde, relevés, opérations groupées par mois.
  - Bourse : vue globale ; valeur et plus-values, courbe de la **valeur**
    (espèces et positions au dernier cours connu) au-dessus des positions,
    puis opérations groupées par mois. Les positions s'ouvrent sur une barre
    de répartition des titres (espèces exclues) en quatre gris au plus, les
    plus petits titres partageant le dernier ; chaque ligne porte sa pastille,
    sa valeur et sa part.
  - Partout : sélection multiple par appui long et suppression confirmée.
- Saisie (`ui/entry`) : feuille du bas ouverte par le bouton « + » (onglets
  Accueil et Comptes, vue d'un compte) ; dépense ou revenu, montant sans
  signe, compte (dernier utilisé par défaut), libellé obligatoire avec
  suggestions qui reprennent la dernière catégorie, catégorie, date, notes.
- Virements, épargne, bourse : la saisie a quatre modes (Dépense, Revenu,
  Virement en une seule saisie, Ordre de bourse avec montant calculé en
  direct). Un livret affiche ses relevés et l'écart au solde calculé, avec la
  création de l'opération Intérêts. Un compte Bourse affiche sa valeur, ses
  plus-values latente et réalisée, ses positions (quantité, PRU, cours,
  plus-value), la mise à jour groupée des cours et ses positions initiales.
- Premier lancement : assistant qui propose comptes (avec solde du jour) et
  catégories ; réapparaît après « Tout effacer » (debug).
- Mode discret : l'œil de la barre du haut ou le réglage masque tous les
  montants ; mémorisé en DataStore.
- Icône : deux chevrons noirs sur une pastille blanche, fond noir
  (`res/drawable/ic_launcher_*.xml`, vectorielle) ; version monochrome pour les
  icônes à thème d'Android 13 et plus.
- Contexte de conception : `PRODUCT.md`, `DESIGN.md` et `.impeccable/`.

Captures de relecture : `./gradlew :app:recordRoborazziDebug` écrit les PNG
dans `app/build/outputs/roborazzi/`.

## Modèle de données

Détail complet : [`doc/SPEC.md` § 2](doc/SPEC.md#2-modèle-de-données).

- **Base Room** (`data/db`), seule source de données, schéma exporté dans
  `app/schemas` (version 1). Tables : `account`, `category`, `asset`, `txn`
  (opérations), `posting` (jambes), `trade` (ordres), `account_statement`
  (relevés), `asset_price` (cours). Aucun solde ni total stocké.
- **Comptes** créés dans l'application, trois types : Courant, Épargne, Bourse ;
  établissement et part détenue (compte joint) facultatifs.
- **Opérations à jambes** : dépense ou revenu (1 jambe), virement (2 jambes de
  somme nulle), ordre de bourse (1 jambe + 1 ordre), solde initial.
- **Montants** en centimes ; quantités et cours en dix-millièmes.
- **Dépôts** (`data/repo`) : toute opération passe par `TransactionValidator`
  avant d'atteindre la base ; suppression d'un compte (simple, archivage ou
  définitive), réaffectation d'une catégorie avant suppression.
- **Domaine** (`domain/calc`) : `Ledger` (soldes, synthèse mensuelle,
  synthèse par catégorie), `Portfolio` (PRU, positions, plus-values),
  `Wealth` (valeur des comptes, patrimoine avec part détenue, séries, écart de
  relevé).
- **Démonstration** (`data/demo`) : six mois de données fictives, chargeables
  depuis les Réglages en version debug seulement.

## Sauvegarde

La sauvegarde cloud Android est désactivée (`allowBackup=false`, et toutes les
données sont exclues de `cloud-backup` pour Android 12 et plus). Le transfert
direct entre téléphones reste possible.

- **Sauvegarde automatique** : chaque jour, un classeur Excel
  `patrimoineplus-AAAA-MM-JJ.xlsx` est écrit dans `Documents/PatrimoinePlus`
  (stockage partagé du téléphone, par MediaStore, sans autorisation) ; les
  **3 plus récents** sont conservés. Tâche de fond WorkManager quotidienne,
  avec rattrapage au démarrage si celle du jour manque ; une base sans compte
  n'est jamais sauvegardée (elle évincerait de bonnes sauvegardes). Bouton
  « Sauvegarder maintenant » dans Réglages › Données (remplace celle du jour).
  Fichiers **non chiffrés**, lisibles par toute appli ayant accès aux fichiers.
- **Classeur** (`data/backup/BackupWorkbook.kt`, écrit et lu sans
  bibliothèque par `data/backup/xlsx/`) : un onglet par type de compte
  (Courant, Épargne, Bourse : une ligne par mouvement sur un compte, ordres
  de bourse détaillés), puis Comptes, Catégories, Actifs, Cours, Relevés et
  Fichier (signature, version). En-têtes figés et filtrables, dates et
  montants en vrais nombres, négatifs en rouge. Les identifiants sont dans des
  colonnes techniques repliées : ils rendent la restauration exacte. Un
  virement entre deux types de comptes figure sur les deux onglets et n'est
  relu qu'une fois.
- **Import** (Réglages › Données) : un classeur (même réenregistré par un
  tableur) ou un ancien export JSON, reconnu à son contenu. Le fichier est
  entièrement vérifié avant toute écriture (signature, version, valeurs,
  identifiants, références, règles de saisie rejouées dans l'ordre
  chronologique) ; un résumé est affiché, puis, après confirmation, toutes
  les données sont remplacées en une seule transaction (tout ou rien).
- **Limite Android** : après une désinstallation, l'appli ne peut plus
  supprimer les fichiers qu'elle avait créés ; la rotation ne s'applique
  qu'aux fichiers créés depuis la réinstallation (l'import, lui, passe par le
  sélecteur de fichiers et reste possible).

## Verrou

Désactivé par défaut ; interrupteur dans Réglages › Sécurité, qui demande une
première authentification pour s'activer. Une fois activé, l'appli demande
l'empreinte ou le code du téléphone (`BIOMETRIC_STRONG` ou `DEVICE_CREDENTIAL`)
au démarrage et au retour après une minute ou plus en arrière-plan
(`domain/rules/Safety.kt`, `ui/lock/`). L'écran de verrouillage se pose
par-dessus l'appli, qui garde son état ; le contenu dessous est retiré de
l'accessibilité. Choix assumé : l'aperçu des applis récentes et les captures
d'écran restent permis (pas de `FLAG_SECURE`).

## Reste à faire

- Gestion des actifs (renommage, ISIN, archivage) : ils ne se créent que
  depuis la saisie d'un ordre.
- Légende de la répartition coupée avec une police système à 130 %.
- Fond qui peut clignoter au démarrage en mode clair forcé quand le téléphone
  est en sombre.
- Migrations Room : à écrire au premier changement de schéma.
- Version release : construite mais jamais exécutée par les tests.

## Conventions de code

- Indentation par tabulations (largeur 4).
- Constantes préfixées `k`, variables locales `v`, paramètres `in`.
- Un commentaire par classe et par fonction ; commentaires et textes en français.
- Alignement des `=` et des commentaires de fin de ligne sur les déclarations
  consécutives.
- Commits en français, style Conventional Commits.

Conséquence connue : un paramètre de méthode redéfinie (par exemple
`onCreate(inSavedInstanceState)`) porte un autre nom que dans la classe
parente, ce qui produit une alerte du compilateur Kotlin sans effet.
