# Plan de tests manuels

Campagne complète à mener sur le téléphone (Android 16) et le PC (Windows),
après le développement de l'appli PC (branche `desktop`, 2026-10-12). Les tests
automatiques (162, voir [`TESTS.md`](TESTS.md)) couvrent les calculs, les
règles, les formats, la synchronisation et les interfaces sans écran ; ce plan
couvre ce qu'ils ne peuvent pas voir : le vrai téléphone, la vraie fenêtre,
Syncthing, le ressenti.

Pour chaque test : cocher, et noter tout écart (capture si possible) avec son
identifiant (A-03, C-12…) pour le retour.

## 0. Préparation

| # | Étape | Résultat attendu |
|---|---|---|
| 0-01 | Installer l'APK debug (`./gradlew :app:assembleDebug`) sur le téléphone. | L'appli s'installe ; icône à chevrons sur pastille blanche. |
| 0-02 | Construire le dossier portable PC (`./gradlew :desktop:packageZip`), le dézipper, lancer `PatrimoinePlus.exe`. | Avertissement Windows possible au premier lancement (« Exécuter quand même ») ; la fenêtre s'ouvre sur l'écran d'accueil (logo, deux boutons). |
| 0-03 | Installer Syncthing-Fork (téléphone) et Syncthing Windows Setup (PC), les associer, partager `Documents/PatrimoinePlus` (marche à suivre dans le README). | Les deux appareils se voient « à jour » dans Syncthing. |

## A. Appli Android

### Lancement, connexion, premier usage

| # | Étape | Résultat attendu |
|---|---|---|
| A-01 | Lancer l'appli (fermée au préalable depuis les applis récentes). | Pastille du logo, puis les chevrons glissent et s'emboîtent, le logo remonte, nom et bouton apparaissent (≈ 1,25 s) ; aucun saut de taille ou de position. |
| A-02 | Sans verrou : toucher « Entrer ». | L'écran s'efface en fondu ; assistant du premier lancement (base vide) ou Accueil. |
| A-03 | Mode sombre du téléphone, relancer. | Même enchaînement, titre lisible. |
| A-04 | Animations désactivées (Accessibilité), relancer. | Écran de connexion posé directement. |
| A-05 | Assistant : choisir des comptes avec soldes et des catégories, terminer. | Comptes et catégories créés ; Accueil avec le bon total. |

### Comptes et catégories

| # | Étape | Résultat attendu |
|---|---|---|
| A-10 | Créer un compte de chaque type avec solde initial ; un compte joint à 50 %. | Comptes listés ; le joint compte pour moitié dans la répartition et le total. |
| A-11 | Onglet Comptes : regarder la barre « Répartition » et les parts. | Pastille par compte, parts qui font 100 %, gris lisibles (clair et sombre). |
| A-12 | Réordonner (appui long, glisser), modifier, archiver, désarchiver un compte. | Ordre, nom, état conservés après redémarrage. |
| A-13 | Supprimer un compte sans opération, puis un compte avec opérations (définitif). | Suppression simple ; définitive après double confirmation, virements supprimés des deux côtés. |
| A-14 | Catégories : créer, renommer, réordonner, supprimer une catégorie utilisée en la réaffectant. | Opérations passées dans la catégorie de remplacement. |

### Saisie et vues

| # | Étape | Résultat attendu |
|---|---|---|
| A-20 | Saisir une dépense, un revenu, un virement, un ordre d'achat (nouvel actif avec ISIN). | Signes corrects ; virement visible sur les deux comptes ; actif créé. |
| A-21 | **Nouveau** : saisir une dépense avec la case « Remboursement » cochée. | Montant positif, déduit des dépenses de la catégorie du mois. |
| A-22 | **Correctif** : modifier un remboursement existant (ex. démo « Remboursement sécu »), changer le libellé, enregistrer. | Il reste un remboursement (montant positif), pas une dépense. |
| A-23 | Suggestions de libellés : commencer à taper un libellé déjà utilisé. | Suggestions ; la catégorie suit. |
| A-24 | Vue d'un compte Courant : changer de mois, lire synthèse, dépenses par catégorie, courbe, opérations ; appui long et suppression multiple. | Chiffres cohérents ; « À venir » en tête. |
| A-25 | Vue d'un livret : solde et totaux, courbe, relevés (ajout, écart, « Créer l'opération Intérêts »), opérations par mois. | Pas de navigation par mois ; dates visibles sur chaque ligne. |
| A-26 | Vue du PEA : valeur, plus-values, courbe de la valeur au-dessus des positions, barre de répartition des titres, lignes sans sous-titre. | Parts qui font 100 % ; nom long sur deux lignes. |
| A-27 | Accueil : total, variation du mois, courbe, répartition par type. | Total = somme des comptes (part détenue appliquée). |
| A-28 | Mode discret (œil) puis apparence Système / Clair / Sombre. | Montants masqués partout ; apparence mémorisée. |

### Cours automatiques

| # | Étape | Résultat attendu |
|---|---|---|
| A-30 | Dans le PEA, toucher un ETF, saisir son ISIN (ex. LU1681043599), « Chercher la cotation », « Utiliser », enregistrer. | Symbole CW8.PA ; cours et courbe mis à jour, historique des fins de mois récupéré. |
| A-31 | Même chose avec un ETF coté en dollars dans la recherche (ex. IE00B4L5Y983). | Cotation trouvée sur une autre place (IWDA.AS), signalée « à vérifier ». |
| A-32 | Tirer la vue vers le bas, puis « Actualiser » ; mode avion puis « Actualiser ». | Mise à jour ; hors ligne : message, dernier cours conservé, pas de plantage. |

### Verrou, sauvegardes, import

| # | Étape | Résultat attendu |
|---|---|---|
| A-40 | Activer le verrou (Réglages › Sécurité). | Demande d'empreinte pour l'activer. |
| A-41 | Relancer l'appli. | Animation puis « Déverrouiller » ; l'empreinte ne se lance qu'au bouton. |
| A-42 | Quitter moins d'une minute puis revenir ; plus d'une minute puis revenir. | Pas de verrouillage ; puis écran de connexion sans animation. |
| A-43 | Ouvrir l'appli un jour donné ; vérifier `Documents/PatrimoinePlus` (appli Fichiers). | `patrimoineplus-AAAA-MM-JJ.xlsx` du jour ; « Sauvegarder maintenant » le remplace. |
| A-44 | Sur plusieurs jours : nombre de sauvegardes datées. | Jamais plus de 3 ; `patrimoineplus-donnees.xlsx` n'est jamais supprimé. |
| A-45 | Ouvrir une sauvegarde dans Excel ou Google Sheets. | Onglets Courant, Épargne, Bourse, Comptes, Catégories, Actifs, Cours, Relevés, Fichier ; en-têtes figés, montants en euros, colonnes d'identifiants repliées. |
| A-46 | Debug › « Tout effacer », puis Importer une sauvegarde. | Résumé, confirmation, données restaurées à l'identique. |
| A-47 | Importer un fichier qui n'est pas une sauvegarde. | « Fichier refusé », rien de modifié. |

## B. Synchronisation téléphone ↔ PC

| # | Étape | Résultat attendu |
|---|---|---|
| B-01 | Téléphone : Réglages › Synchronisation › « Choisir le dossier », sélectionner `Documents/PatrimoinePlus`. | État « À jour » ; `patrimoineplus-donnees.xlsx` apparaît dans le dossier du PC en quelques secondes. |
| B-02 | PC : « Choisir le dossier synchronisé… », sélectionner le dossier du PC. | Les données du téléphone s'affichent ; bandeau « Synchronisé avec … à HH:MM:SS ». |
| B-03 | Téléphone : saisir une opération. PC : attendre. | L'opération apparaît sur le PC en quelques secondes, sans action. |
| B-04 | PC : saisir une opération. Téléphone : revenir dans l'appli. | L'opération apparaît sur le téléphone. |
| B-05 | Couper le Wi-Fi du téléphone ; modifier sur le téléphone ET sur le PC ; rétablir le Wi-Fi ; revenir dans les deux applis. | Fenêtre « Quelle version garder ? » ; le choix est appliqué ; l'autre appareil reprend la version gardée. |
| B-06 | Téléphone : « Arrêter la synchronisation ». | Les modifications du téléphone ne partent plus ; le fichier reste. |
| B-07 | Réinstaller l'appli Android (base vide), choisir le même dossier. | Les données du fichier sont reprises sans conflit. |
| B-08 | Fermer l'appli PC juste après une saisie (moins de 2 s). | La saisie est quand même dans le fichier (écrite à la fermeture). |

## C. Appli PC

| # | Étape | Résultat attendu |
|---|---|---|
| C-01 | Écran d'accueil : « Essayer avec la démonstration ». | Données fictives ; bandeau « Démonstration : … jamais enregistrées ». |
| C-02 | Démonstration : saisir une opération, fermer et relancer l'appli. | Démonstration rouverte, saisie disparue (jamais enregistrée) ; aucun fichier créé. |
| C-03 | Parcourir Accueil, Comptes, chaque type de compte, Catégories, Réglages ; agrandir et réduire la fenêtre. | Deux colonnes sur grand écran, une seule quand c'est étroit ; rien de coupé. |
| C-04 | « Nouvelle opération » et Ctrl+N (depuis la vue d'un compte). | Panneau à droite, compte affiché présélectionné ; Échap ferme, Entrée enregistre. |
| C-05 | Saisir dépense, remboursement, revenu, virement, ordre avec nouvel actif. | Mêmes résultats que sur le téléphone. |
| C-06 | Saisie incomplète puis Enregistrer. | Messages d'erreur sous les champs, rien d'enregistré. |
| C-07 | Cliquer une opération pour la modifier, puis la supprimer ; cliquer un solde initial. | Modification / suppression appliquées ; le solde initial ouvre la fiche du compte. |
| C-08 | Comptes : ajouter, modifier, réordonner (flèches), archiver, supprimer (simple et définitive). | Mêmes règles que le téléphone (type verrouillé, confirmation). |
| C-09 | Catégories : ajouter, choisir une icône, réordonner, supprimer avec réaffectation. | Appliqué et synchronisé vers le téléphone. |
| C-10 | Livret : ajouter et supprimer un relevé. PEA : saisir des cours, ajouter et retirer une position initiale, cliquer un titre (fiche : ISIN, symbole). | Valeurs et courbes mises à jour. |
| C-11 | Réglages : apparence (Système / Clair / Sombre), mode discret, puis relancer. | Choix conservés. |
| C-12 | Rendre le fichier d'échange illisible (le copier tronqué à sa place) pendant que l'appli est ouverte. | Bandeau « Fichier d'échange refusé » ; données précédentes affichées ; le fichier n'est pas écrasé. |
| C-13 | `PatrimoinePlus.exe --verifier chemin\\vers\\patrimoineplus-donnees.xlsx` dans un terminal. | Code de sortie 0 pour un fichier valide. |

## D. Limites connues (pas des anomalies)

- Les cours automatiques ne sont récupérés que par le téléphone.
- Titres cotés uniquement hors zone euro : pas de conversion de devise.
- Thème et composants graphiques dupliqués entre Android et PC (sans effet visible).
- Légende de la répartition coupée avec une police à 130 % (Android) ; fond qui
  peut clignoter au démarrage en mode clair forcé.
- Appli PC non signée : avertissement Windows au premier lancement.
