# PatrimoinePlus

[![CI](https://github.com/Maubin76/PatrimoinePlus/actions/workflows/ci.yml/badge.svg)](https://github.com/Maubin76/PatrimoinePlus/actions/workflows/ci.yml)

Application Android personnelle de suivi du patrimoine et des dépenses :
comptes courants, livrets d'épargne, compte-titres (PEA). Données uniquement
en base locale sur le téléphone, sans synchronisation. Seule exception : les
cours des titres sont demandés à Yahoo Finance (voir « Cours automatiques »).

La spécification complète (besoins, modèle de données, plan des étapes) est
dans [`doc/SPEC.md`](doc/SPEC.md) ; la couverture des tests dans
[`doc/TESTS.md`](doc/TESTS.md) ; le plan de tests manuels dans
[`doc/PLAN_DE_TESTS.md`](doc/PLAN_DE_TESTS.md).

## Construire

```sh
./gradlew :app:assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test                # tests du module commun (Kotlin pur)
./gradlew :desktop:run               # lance l'appli PC (Windows)
./gradlew :desktop:packageZip        # dossier portable zippé de l'appli PC
./gradlew :desktop:test             # tests de l'appli PC
./gradlew :app:testDebugUnitTest    # tests de l'appli Android (Robolectric)
./gradlew :app:assembleRelease      # APK optimisé (R8), signé avec la clé de debug :
                                    # app/build/outputs/apk/release/app-release.apk
```

## Intégration continue

GitHub Actions (`.github/workflows/ci.yml`) : à chaque push sur `main`
seulement (les branches de travail ne la déclenchent pas ; lancement manuel
possible depuis l'onglet Actions), la CI installe le JDK 21, lance tous les
tests JVM (`:core:test`, `:app:testDebugUnitTest`, `:desktop:test`) puis
construit l'APK debug. En cas
d'échec, les rapports de tests sont joints à l'exécution (artefact
« rapports-tests », 14 jours). Un nouveau push sur la même branche annule
l'exécution en cours. Les tests ne dépendent ni du fuseau horaire ni de la
langue du système (vérifié en UTC et en anglais, comme sur la machine de CI).

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

Package racine : `fr.maubin.patrimoineplus`. Le projet est découpé en modules
Gradle ; un module ne peut utiliser que ce dont il dépend, ce qui garantit les
frontières :

```
:core   Kotlin pur sur la JVM, sans Android ni interface — partagé par les applis
        domain/        modèles, règles de saisie, calculs, formats d'affichage
        data/backup/   format de sauvegarde (classeur Excel, ancien JSON), rotation
        data/demo/     données de démonstration
        data/market/   analyse des cours, client Yahoo Finance
        data/sync/     synchronisation par fichier : empreintes, décision
        data/DataSet   toutes les données en modèles du domaine (base, fichier, démo)
        testFixtures/  aides de test partagées (fausse source de cours, réponses enregistrées)
        ui/            états des écrans (accueil, comptes, vue d'un compte, tableau
                       de bord du PC) et logique de saisie, sans Compose
:ui     interface commune, Compose Multiplatform compilé pour Android et pour la
        JVM du PC (dépend de :core)
        theme/         couleurs, typographie, PatrimoineTheme, ThemeMode, couleurs
                       des hausses et baisses ; police Hanken Grotesk en ressource
        components/    montants, courbes, barres, marques, sélecteur de mois, logo,
                       tuiles et courbes miniatures
        icons/         icônes au trait et icônes de catégories
:app    appli Android (dépend de :core et :ui)
        data/          base Room (db), dépôts (repo), réglages DataStore (settings),
                       sauvegarde sur le téléphone, mise à jour des cours (market)
        di/            modules Hilt (horloge, base, réglages, source de cours)
        ui/            écrans Compose, ViewModels, navigation, feuilles de saisie
:desktop appli PC, Compose Multiplatform (dépend de :core et :ui)
        data/          réglages (%APPDATA%\PatrimoinePlus), espace de travail
        ui/            tableau de bord, vue d'un compte, panneaux de saisie
```

Le code commun garde ses noms de packages : passer d'un module à l'autre n'a
changé aucun import. Les états des écrans sont calculés par les mêmes
fonctions sur le téléphone et sur le PC : les chiffres affichés sont
identiques. Le thème et les composants graphiques sont écrits une seule fois
(`:ui`) ; seule la mise en page diffère : écrans et feuilles du bas sur le
téléphone, tableau de bord à tuiles et panneau latéral sur le PC.

Règles :

- aucun solde ni total stocké : tout est recalculé par `domain` à partir des
  opérations ;
- montants en centimes (`Long`), jamais en virgule flottante ;
- la date courante vient d'une `Clock` injectée, jamais de `LocalDate.now()`
  appelé directement dans la logique.

## Interface

Direction visuelle monochrome, noir sur blanc (blanc sur noir en mode sombre),
inspirée de deux maquettes de référence fournies le 2026-10-07.

- Palette fixe (`:ui`, `theme/Color.kt`) : blanc, encre presque noire, gris
  secondaire, filets gris très clair ; aucun accent par défaut. Rouge discret
  réservé aux montants négatifs. Option « Hausses en vert, baisses en rouge »
  (Réglages › Apparence, téléphone et PC) : variations et plus-values
  colorées, signe toujours écrit. Les couleurs dynamiques Material You sont écartées.
- Police unique Hanken Grotesk (OFL, ressource Compose du module `:ui`, licence dans
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
- Comptes : barre « Répartition » en tête de la liste (part de chaque compte
  dans le patrimoine, part détenue appliquée, comptes archivés encore valorisés
  compris, comptes à découvert exclus) ; chaque compte porte la pastille de sa
  teinte et sa part sous son montant. Même rampe de gris que les positions
  (`allocationColor`), les plus petits comptes au-delà du quatrième partageant
  la dernière teinte.
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

## Appli PC (branche `desktop`)

Appli Windows en Compose Multiplatform (`:desktop`), même identité noir et
blanc que le téléphone, mais pensée pour un grand écran : un **tableau de
bord** de pilotage plutôt qu'une suite d'écrans.

- **Installation** : `./gradlew :desktop:packageZip` produit
  `desktop/build/distributions/PatrimoinePlus-1.0.0-windows.zip` (≈ 60 Mo) :
  à dézipper n'importe où, puis lancer `PatrimoinePlus.exe` (Java inclus, sans
  installation ni droits administrateur). Non signé : Windows peut afficher un
  avertissement au premier lancement (« Informations complémentaires ›
  Exécuter quand même »). En développement : `./gradlew :desktop:run`.
- **Écran d'accueil** : choisir le dossier synchronisé avec le téléphone, ou
  **essayer avec la démonstration** (données fictives en mémoire, modifiables,
  jamais enregistrées). Le choix est mémorisé et rouvert au lancement suivant.
- **Tableau de bord** (Accueil et Comptes réunis) : patrimoine et variation du
  mois, variation sur douze mois, entrées et dépenses du mois, épargne du mois ;
  évolution du patrimoine et répartition par type ; **une tuile par compte**
  (valeur, variation, petite courbe sur douze mois et aperçu selon le type :
  dépenses du mois et principales catégories, intérêts de l'année et dernier
  relevé, plus-value latente et principales positions), qui ouvre le compte
  d'un clic ; « Réorganiser » pour changer l'ordre des tuiles ; dépenses du
  mois par catégorie ; dernières opérations (un clic pour les modifier) ;
  comptes archivés repliés.
- **Vue d'un compte**, en tuiles : chiffres clés, courbe, dépenses par
  catégorie (Courant, mois par mois) ou répartition des titres et tableau des
  positions (Bourse), relevés, opérations.
- **Saisie** (panneau latéral à droite ; Échap ferme, Entrée dans un champ
  enregistre ; Ctrl+N ou « Nouvelle opération ») : opérations (dépense,
  remboursement, revenu, virement, ordre avec nouvel actif), comptes (solde
  initial, archivage, suppression simple ou définitive confirmée), catégories
  (icône, suppression avec réaffectation), relevés, cours saisis à la main,
  positions initiales, fiche d'un actif. Mêmes contrôles et mêmes règles que
  le téléphone (`:core` : `DataEditor`, logique de saisie partagée).
- **Rapprochement avec le relevé de la banque** (vue d'un compte, tuile
  Relevés › « Rapprocher un relevé… ») : le fichier CSV exporté par le site de
  la banque est lu sans réglage (encodage, séparateur, lignes d'informations,
  colonnes Montant ou Débit/Crédit, dates et montants à la française) et
  comparé aux opérations du compte : même montant, dates à cinq jours au plus.
  Trois listes : lignes retrouvées ; lignes de la banque absentes de l'appli,
  avec libellé et catégorie proposés d'après les saisies passées, à cocher et
  corriger puis créer en une fois ; opérations de l'appli absentes du relevé.
  Rien n'est mémorisé (pas de pointage) ; le format exact de chaque banque reste
  à vérifier sur un vrai export.
- **Analyse** (barre latérale) : dépenses et revenus des douze derniers mois,
  tous comptes : moyennes (dépenses, revenus, taux d'épargne), plus fortes
  hausses du dernier mois, barres mois par mois, tableau catégories × mois
  (moyenne des mois complets, écart du dernier mois). Un clic sur une case
  ouvre ses opérations.
- **Opérations** (barre latérale, Ctrl+F) : recherche dans toutes les
  opérations (mots sans accents ni majuscules dans le libellé, les notes, la
  catégorie, les comptes ; montant exact ; filtres de compte, de catégorie, de
  période et de montant), total des résultats ; sélection multiple (cases,
  Ctrl+clic, Maj+clic, tout sélectionner) pour **changer la catégorie** de
  plusieurs dépenses ou revenus d'un coup, ou les **supprimer** (confirmé ;
  refusé si une vente devenait impossible).
- **Saisie rapide** (barre latérale) : dépenses et revenus en série dans une
  grille au clavier (Tab, Entrée pour une nouvelle ligne, Ctrl+Entrée pour tout
  enregistrer) ; catégorie proposée d'après le libellé ; montant sans signe =
  dépense, « + » = entrée ; tout est contrôlé puis enregistré en une fois.
- **Bilan annuel** (barre latérale) : patrimoine au début et à la fin de
  l'année, comptes, revenus et dépenses par catégorie, taux d'épargne,
  intérêts, dividendes, plus-values réalisées par titre ; **export PDF** A4
  prêt à imprimer (générateur PDF minimal écrit pour l'appli, polices
  standard, sans bibliothèque).
- **Projection** (barre latérale) : patrimoine estimé à 5, 10, 20 ou 30 ans ;
  épargne mensuelle (par défaut la moyenne des douze derniers mois),
  rendements de l'épargne et de la bourse, part versée en bourse ; trois
  scénarios et courbe année par année ; une estimation, sans impôts ni
  inflation.
- **Budget** (barre latérale) : budget prévisionnel d'un mois type, en
  lignes libres d'entrées, de dépenses et d'investissements ; chaque ligne
  peut suivre une catégorie (ou un compte d'épargne ou de bourse) pour
  afficher son réel du mois et l'écart ; **diagramme de cashflow (Sankey)**
  du prévu ou du réel d'un mois (entrées → budget du mois → dépenses,
  investissements, reste ; déficit visible). Enregistré sur le PC
  (`%APPDATA%\PatrimoinePlus\budget.json`), pas dans le fichier
  synchronisé.
- **Synchronisation côté PC** : le fichier d'échange est lu, puis surveillé
  (2 s) ; une modification du PC est écrite 1,5 s plus tard (fichier
  temporaire ignoré par Syncthing, puis remplacement atomique), une
  modification du téléphone est relue ; même décision par empreintes que le
  téléphone, même fenêtre de conflit (le fichier est relu avant d'écraser).
  Un fichier illisible n'est jamais écrasé. Si des modifications ne peuvent
  pas être écrites (conflit, fichier refusé), la fermeture et le changement de
  source proposent d'en **enregistrer une copie**. Les **cours automatiques**
  restent récupérés par le téléphone seul (le PC n'a aucun accès réseau).
- **Vérification sans fenêtre** : `PatrimoinePlus.exe --verifier <fichier>`
  lit et vérifie un fichier d'échange (code de sortie 0 s'il est valide) et
  montre une date en français (contrôle de la JVM embarquée).
- Réglages de l'appli PC : `%APPDATA%\PatrimoinePlus\desktop.properties`.
- Un installateur `.msi` est possible gratuitement (le plugin Compose
  télécharge WiX lui-même).

## Synchronisation avec le PC

Les données sont partagées avec l'appli PC (branche `desktop`) par un
**fichier d'échange** déposé dans un dossier que
synchronise un outil comme Syncthing (gratuit, sans cloud).

- **Dossier** : choisi une fois dans Réglages › Synchronisation (sélecteur du
  système, ouvert sur `Documents/PatrimoinePlus`) ; Android mémorise
  l'autorisation, ce qui permet de lire les fichiers déposés par Syncthing.
- **Fichier** : `patrimoineplus-donnees.xlsx`, même classeur que les
  sauvegardes. Réécrit 3 s après chaque modification, relu au démarrage et à
  chaque retour dans l'appli.
- **Décision** (`:core`, `data/sync/SyncLogic.kt`) : par **empreintes de
  contenu** (SHA-256 de la forme canonique), sans aucune date — les horloges
  des appareils peuvent différer. Dernière synchronisation, téléphone et
  fichier : seul le téléphone a changé → le fichier est réécrit ; seul le
  fichier → les données du téléphone sont remplacées (après une sauvegarde de
  sécurité) ; les deux, différemment → **conflit** : une fenêtre demande
  quelle version garder, rien n'est modifié avant. Un téléphone vide (nouvelle
  installation) reprend le fichier sans conflit.
- **Fichier illisible** (copie interrompue) : refusé, rien n'est modifié.
- La rotation des sauvegardes ne compte que les fichiers datés : le fichier
  d'échange et les copies de conflit de Syncthing ne sont jamais supprimés.

### Mise en place avec Syncthing

Syncthing est gratuit et libre ; il synchronise le dossier directement entre
le téléphone et le PC (Wi‑Fi local), sans serveur intermédiaire.

1. **Téléphone** : installer **Syncthing-Fork** depuis le Google Play Store
   (l'ancienne appli « Syncthing » n'y est plus disponible).
2. **PC (Windows)** : installer Syncthing avec **Syncthing Windows Setup**,
   l'installateur listé sur la page de téléchargement de syncthing.net
   (`github.com/Bill-Stewart/SyncthingWindowsSetup`). L'interface de Syncthing
   s'ouvre ensuite dans le navigateur.
3. **Associer les appareils** : dans Syncthing-Fork, ajouter le PC en scannant
   le QR code de son identifiant (interface PC : Actions › Afficher
   l'identifiant), puis accepter l'appareil côté PC.
4. **Partager le dossier** : sur le téléphone, partager
   `Documents/PatrimoinePlus` avec le PC ; côté PC, accepter le partage et
   choisir son emplacement (par exemple `Documents\PatrimoinePlus`).
5. **Dans PatrimoinePlus** : Réglages › Synchronisation › « Choisir le dossier »,
   sélectionner `Documents/PatrimoinePlus` et autoriser.
6. **Vérifier** : `patrimoineplus-donnees.xlsx` apparaît dans le dossier du PC
   au bout de quelques secondes et suit chaque modification faite sur le
   téléphone.

Si la synchronisation tarde, vérifier dans Syncthing-Fork les conditions
d'exécution (Wi‑Fi seulement, en charge…) et l'autoriser à fonctionner en
arrière-plan : Android peut le mettre en pause. Dans un tableur, ne jamais
modifier les colonnes repliées (identifiants) du fichier d'échange.

## Cours automatiques

Les comptes Bourse se valorisent seuls : il suffit de saisir les ordres.

- **Source** : Yahoo Finance (API publique non officielle, sans clé,
  `data/market/YahooQuoteSource.kt`). Seuls des ISIN et des symboles sont
  envoyés, jamais de quantité ni de montant ; le fournisseur voit toutefois
  quels titres sont suivis et l'adresse IP. Permission `INTERNET`.
- **Identifiant** : l'ISIN de chaque actif (saisi à la création d'un actif
  dans un ordre, ou dans la fiche de l'actif en touchant sa position).
  L'appli cherche la cotation **en euros** : correspondance directe adoptée
  automatiquement (LU1681043599 → CW8.PA) ; sinon, dans la fiche, « Chercher
  la cotation » essaie le même symbole sur les places de la zone euro
  (IE00B4L5Y983 → IWDA.AS) et montre le titre trouvé pour confirmation. Le
  symbole peut aussi être saisi à la main (colonne `ticker`, schéma v2).
- **Quand** : à l'ouverture d'un compte Bourse et par une tâche de fond
  quotidienne (avec réseau), au plus toutes les 4 heures ; « Actualiser » ou
  tirer la vue vers le bas pour forcer.
- **Historique** : à la première mise à jour, les clôtures de fin de mois
  depuis le premier ordre sont enregistrées (courbe de la valeur et
  patrimoine justes sur tout l'historique) ; ensuite, seuls les mois manquants
  et le dernier cours.
- **Sans réseau ou titre introuvable** : le dernier cours connu reste utilisé ;
  « Saisir des cours » permet toujours la saisie manuelle.

## Lancement et verrou

- **Lancement** : l'écran de démarrage système montre la pastille du logo
  (`res/drawable/splash_logo.xml`, bibliothèque `core-splashscreen`) ;
  l'animation Compose prend le relais au même endroit et à la même taille
  (l'écran système reste affiché jusqu'à ce que le logo soit mesuré, 1,5 s au
  plus) : les deux chevrons glissent dans la pastille et s'emboîtent, le logo
  remonte, puis le nom et le bouton apparaissent (≈ 1,25 s,
  `ui/lock/LockScreen.kt`). Animation sautée si les animations du téléphone
  sont désactivées.
- **Écran de connexion** : toujours franchi au lancement. Avec le verrou, le
  bouton « Déverrouiller » demande l'empreinte ou le code du téléphone
  (`BIOMETRIC_STRONG` ou `DEVICE_CREDENTIAL`) — plus de demande automatique ;
  sans verrou, « Entrer » ouvre l'appli. Il s'efface en fondu.
- **Verrou** : désactivé par défaut ; interrupteur dans Réglages › Sécurité,
  qui demande une première authentification pour s'activer. Une fois activé,
  l'écran de connexion revient (sans animation) après une minute ou plus en
  arrière-plan (`domain/rules/Safety.kt` : `shouldLock`, `gateShown`).
- L'écran se pose par-dessus l'appli, qui garde son état ; le contenu dessous
  est retiré de l'accessibilité. Choix assumé : l'aperçu des applis récentes et
  les captures d'écran restent permis (pas de `FLAG_SECURE`).

## Reste à faire

- Archivage d'un actif (la fiche permet le renommage, l'ISIN et le symbole).
- ISIN des positions initiales : à compléter dans la fiche de l'actif.
- Cours d'un titre coté seulement hors zone euro : pas de conversion de devise
  (saisie manuelle des cours).
- Légende de la répartition coupée avec une police système à 130 %.
- Fond qui peut clignoter au démarrage en mode clair forcé quand le téléphone
  est en sombre.
- Version release : construite mais jamais exécutée par les tests.
- PC : barre de titre Windows claire même en mode sombre ; sélecteur de
  dossier de Java (pas celui de l'Explorateur).
- Téléphone : quand la liste des opérations tient sur l'écran, le bouton « + »
  peut recouvrir le montant de la dernière ligne.

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
