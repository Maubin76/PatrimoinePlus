# Tests

Tous les tests tournent sur la JVM (`app/src/test`), sans émulateur :

```sh
./gradlew :app:testDebugUnitTest        # 97 tests
./gradlew :app:recordRoborazziDebug     # régénère les captures d'écran
```

Les tests de la couche `domain` sont du Kotlin pur. Ceux de la couche `data`
et les captures utilisent Robolectric (Android simulé sur la JVM).

## Ce qui est couvert

| Classe de test | Code testé | Ce qui est vérifié |
|---|---|---|
| `MoneyFormatTest` (6) | `domain/format` | format français des montants : centimes, milliers, espaces insécables, signes, bornes `Long` ; montant masqué |
| `AmountInputTest` (3) | `domain/format/AmountInput` | lecture d'un montant saisi (virgule ou point, espaces, €, signe moins) ; formes refusées (vide, trois décimales, lettres, démesuré) ; aller-retour avec la forme éditable |
| `DecimalInputTest` (2) | `domain/format/DecimalInput` | lecture des quantités et cours (jusqu'à 4 décimales, virgule ou point, €) ; formes refusées ; écriture sans zéros inutiles, cours à 2 décimales minimum ; aller-retour éditable |
| `EntryTest` (5) | `domain/calc/Entry` | signe d'une dépense ou d'un revenu déduit de la nature (jamais tapé) ; suggestions de libellés (sans accents ni majuscules, début de mot, fréquence puis récence, dernière catégorie, virements exclus) ; série des soldes de clôture (mois sans opération reportés) ; opérations d'un mois et montant vu d'un compte ; **virement** (deux jambes opposées) et **ordre** (jambe = cours × quantité ± frais) construits depuis la saisie |
| `MoneyMathTest` (5) | `domain/calc/MoneyMath` | arrondi commercial symétrique ; part détenue au centime (positif, négatif) ; montant brut quantité × cours ; grands produits sans débordement ; signe et frais des ordres |
| `LedgerTest` (10) | `domain/calc/Ledger` | solde à une date (jour inclus) ; **ouverture d'un mois = clôture du précédent** ; **invariant ouverture + ventilation = clôture** pour chaque compte et chaque mois ; classement des virements (épargne / internes) des deux côtés ; remboursement ; opérations à venir exclues du solde du jour mais incluses dans la clôture du mois ; synthèse par catégorie (remboursements déduits, tri, pourcentages, bornes) **compte par compte à 100 %** |
| `PortfolioTest` (8) | `domain/calc/Portfolio` | PRU moyen pondéré frais inclus ; PRU inchangé par une vente ; plus-value réalisée ; espèces = solde ; dernier cours connu à la date (cours postérieur ignoré) ; position sans cours valorisée au prix de revient ; positions initiales ; quantité détenue ; position soldée |
| `WealthTest` (6) | `domain/calc/Wealth` | patrimoine avec part détenue (joint à 50 %), compte Bourse valorisé, compte archivé inclus ; virement neutre ; répartition par type ; série mensuelle (mois en cours arrêté à la date, opérations à venir exclues) ; **variation du mois** (depuis la fin du mois précédent, depuis zéro au premier mois) ; écart de relevé |
| `TransactionValidatorTest` (6) | `domain/rules/TransactionValidator` | chaque nature d'opération valide ; chaque violation : libellé, nombre de jambes, compte inconnu, montant nul, catégorie manquante ou inattendue, virement déséquilibré ou sur un seul compte, compte non Bourse, montant d'ordre incohérent, valeurs d'ordre, actif inconnu, **vente à découvert**, position initiale en vente |
| `SafetyTest` (2) | `domain/rules/Safety` | **verrou** : jamais s'il est désactivé, toujours au démarrage, après une minute ou plus en arrière-plan et pas avant (bornes à la milliseconde) ; **rappel d'export** : seulement s'il y a des données, sans export ou après trente jours |
| `BackupFormatTest` (4) | `data/backup/BackupFormat` | aller-retour JSON sans perte ; fichier d'une autre appli ou d'une version plus récente refusé ; JSON illisible, valeur d'énumération ou date inconnue refusés ; identifiants en double, références cassées (compte, catégorie, actif), opération qui viole les règles de saisie refusés |
| `AccountRulesTest` (2) | `domain/rules/AccountRules` | nom et part détenue d'un compte ; réaffectation de catégorie (même nature, autre catégorie) |
| `DataLayerTest` (16) | `data/db`, `data/repo`, `data/demo` (Room en mémoire) | création de comptes et règles ; ordre, modification ; **aller-retour complet** dépense / virement / ordre ; opération invalide refusée et absente de la base ; modification qui remplace les jambes ; suppression refusée pour un compte utilisé, **contrainte SQL** qui interdit d'orpheliner des jambes ; archivage ; **suppression définitive** (virement supprimé en entier, relevés en cascade) ; réaffectation de catégorie ; un cours par actif et par jour ; chargement et effacement de la démo ; schéma exporté ; **solde initial** (création avec le compte, modification, suppression à zéro, non compté comme opération, parti avec une suppression simple) ; type verrouillé dès la première opération ; ordre des catégories ; **positions initiales** (ajout, valorisation, retrait, solde initial supprimé quand il est vide) ; cours groupés (un par actif et par jour) ; suppression d'un relevé ; **export puis import** : un compte ajouté après l'export disparaît, les opérations sont identiques, et un nouvel export redonne le même contenu |
| `DemoDataTest` (2) | `data/demo/DemoData` | toutes les opérations de démo passent les règles de saisie, pour plusieurs dates du jour ; aucun compte à découvert ; une opération à venir ; écart de relevé de 38,20 € |
| `ScreenshotTest` (20) | écrans Compose | **aucune assertion** : produit des captures (accueil clair, sombre, discret, vide, police à 130 %, calculé depuis la démo ; comptes et catégories depuis la démo ; feuille de création de compte ; assistant du premier lancement ; **vue d'un compte** (mois en cours depuis la démo, mode sélection en sombre) ; **feuille de saisie** avec suggestions, en mode virement et en mode ordre ; **vue d'un compte Bourse** (valeur, plus-values, positions) et **d'un livret** (relevé avec écart) ; réglages (verrou, sauvegarde) ; accueil avec le rappel d'export ; pilule à mi-glissement) pour relecture humaine. |

## Vérifications par mutation (2026-10-07)

Chaque règle a été cassée volontairement, la suite relancée, puis le code restauré.

| Règle cassée | Tests qui ont échoué |
|---|---|
| Ouverture d'un mois = solde au 1er du mois (au lieu de la clôture du mois précédent) | `LedgerTest.openingIsPreviousClosing`, `LedgerTest.monthBreakdownAddsUpToClosing` |
| Virement déséquilibré accepté | `TransactionValidatorTest.transferRules` |
| Le solde initial compte comme une opération (suppression simple refusée) | `DataLayerTest.openingBalanceLifecycle` |
| Une vente fait sortir tout le prix de revient (PRU faux) | `PortfolioTest.saleKeepsAverageAndRealizesGain`, `PortfolioTest.valuationUsesLastKnownPrice` |
| Re-verrouillage à plus d'une minute au lieu d'une minute ou plus (2026-10-08) | `SafetyTest.lockAfterOneMinuteInBackground` |
| Import : jambe vers un compte inconnu acceptée (2026-10-08) | `BackupFormatTest.refusesBrokenReferencesAndRules` |

## Ce qui n'est pas couvert

- **Migrations Room** : il n'y a encore qu'une version du schéma. Le premier
  changement de schéma devra ajouter une migration et un test qui l'applique
  sur le schéma 1 exporté (`room-testing` est déjà en place).
- **Interface** : pas de test automatisé du comportement (clics, glissement,
  glisser-déposer, feuilles, boîtes de confirmation, assistant, navigation,
  changement de mois, sélection multiple, saisie et suppression d'opérations,
  mode discret persistant, apparence, animations). Les captures ne
  montrent que des états figés ; vérification manuelle sur le téléphone.
- **ViewModels** (accueil, comptes, catégories, assistant, vue d'un compte, saisie) : la combinaison
  des flux et la lecture des formulaires ne sont pas testées en tant que telles
  (les règles qu'ils appliquent le sont dans le domaine et les dépôts). Pour l'accueil,
  en tant que telle ; sa mise en forme (`toHomeUiState`) l'est indirectement par
  la capture `home-demo.png`, sans assertion.
- **DataStore** (mode discret, apparence, verrou, date du dernier export) : non testé.
- **Verrou** : `AppLock` (cycle de vie du processus) et la demande
  d'authentification du système ne sont pas testés automatiquement ; seule la
  règle `shouldLock` l'est. Vérification manuelle sur le téléphone.
- **Export / import par fichiers** : le choix du fichier (Storage Access
  Framework) et l'écriture par `ContentResolver` ne sont pas testés ; le
  contenu (format, vérification, remplacement de la base) l'est.
- **Version release (R8)** : construite, jamais exécutée par les tests.
