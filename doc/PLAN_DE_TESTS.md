# Plan de tests manuels

Campagne complète à mener sur le téléphone (Android 16) et le PC (Windows),
après le développement de l'appli PC (branche `desktop`, 2026-10-12), mis à
jour après la revue de code et la refonte du PC en tableau de bord. Les tests
automatiques (261, voir [`TESTS.md`](TESTS.md)) couvrent les calculs, les
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
| A-15 | Créer un compte « Biens immobiliers » à 50 % et un compte « Objets de collection » ; ajouter un bien déjà possédé et un objet payé depuis le compte courant ; vendre un objet (prix versé sur le compte courant) ; annuler la vente ; supprimer un objet vendu. | Pas de champ « Solde initial » ; valeur au prix d'achat (bien compté pour moitié) ; l'achat débite le compte payeur sans changer le patrimoine ; la vente fait apparaître la plus-value ; annuler la vente rend l'objet détenu ; supprimer l'objet supprime aussi sa vente. Après synchronisation, mêmes biens sur le PC. |
| A-16 | Comptes › « Opérations récurrentes » : créer une dépense mensuelle commencée le mois dernier, un virement trimestriel ; saisir une opération avec « Répéter : Chaque mois » ; créer un compte « Crédit » (prêt commencé il y a un an) ; quitter puis rouvrir l'appli le lendemain d'une échéance ; supprimer une opération créée par une récurrence ; synchroniser avec le PC. | Échéances passées créées dès l'enregistrement, une seule fois ; récurrence de « Répéter » partant de la date de l'opération saisie, première échéance suivante le mois d'après (pas de doublon) ; crédit : capital restant dû juste, prochaines mensualités, **une seule opération « Mensualité »** créée à chaque échéance ; opération supprimée non recréée ; après synchronisation, aucune opération en double sur le téléphone ni sur le PC. |

### Saisie et vues

| # | Étape | Résultat attendu |
|---|---|---|
| A-20 | Saisir une dépense, un revenu, un virement, un ordre d'achat (nouvel actif avec ISIN). | Signes corrects ; virement visible sur les deux comptes ; actif créé. |
| A-21 | **Nouveau** : saisir une dépense avec la case « Remboursement » cochée. | Montant positif, déduit des dépenses de la catégorie du mois **et des « Dépenses » de la synthèse du mois** (pas compté en « Entrées »). |
| A-22 | **Correctif** : modifier un remboursement existant (ex. démo « Remboursement sécu »), changer le libellé, enregistrer. | Il reste un remboursement (montant positif), pas une dépense. |
| A-23 | Suggestions de libellés : commencer à taper un libellé déjà utilisé. | Suggestions ; la catégorie suit. |
| A-23b | Taper deux fois très vite sur « Enregistrer ». | Une seule opération créée. |
| A-23c | Sur un PEA, toucher « + ». | La saisie s'ouvre en mode « Ordre ». |
| A-23d | Ordre sur un nouvel actif refusé (vente sans titre), puis annuler. | Aucun actif fantôme dans la liste des actifs. |
| A-23e | Achat de 10 et vente de 4 du même titre le même jour ; puis sélection multiple sur l'achat seul et Supprimer. | Plus-value réalisée juste (calculée sur le PRU) ; suppression refusée avec un message (la vente deviendrait impossible). |
| A-23f | Tourner le téléphone pendant une saisie. | La saisie reste ouverte et remplie. |
| A-24 | Vue d'un compte Courant : changer de mois, lire synthèse, dépenses par catégorie, courbe, opérations ; appui long et suppression multiple. | Chiffres cohérents ; « À venir » en tête. |
| A-25 | Vue d'un livret : solde et totaux, courbe, relevés (ajout, écart, « Créer l'opération Intérêts »), opérations par mois. | Pas de navigation par mois ; dates visibles sur chaque ligne. |
| A-26 | Vue du PEA : valeur, plus-values, courbe de la valeur au-dessus des positions, barre de répartition des titres, lignes sans sous-titre. | Parts qui font 100 % ; nom long sur deux lignes. |
| A-27 | Accueil : total, variation du mois, courbe, répartition par type. | Total = somme des comptes (part détenue appliquée) ; **parts par type qui font 100 %** ; pas de « variation du mois » le tout premier mois. |
| A-28 | Mode discret (œil) puis apparence Système / Clair / Sombre. | Montants masqués partout ; apparence mémorisée. |
| A-29 | **Nouveau** : Réglages › « Hausses en vert, baisses en rouge ». | Variation du mois et plus-values en vert ou rouge (clair et sombre), signe toujours écrit ; désactivé : tout en noir et blanc. |

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
| A-41b | Verrou activé : ouvrir une saisie, quitter l'appli plus d'une minute, revenir. | Écran de connexion seul : la feuille de saisie n'est plus visible ni utilisable par-dessus ; après déverrouillage, l'écran d'avant est retrouvé. Aperçu masqué dans les applis récentes. |
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
| B-07b | Choisir à nouveau le même dossier dans les Réglages. | Pas de conflit. |
| B-07c | Laisser l'appli ouverte pendant une mise à jour des cours sans changement de cours. | Pas de nouvel export ni de conflit. |
| B-07d | (debug) « Charger la démo » avec la synchronisation active. | La synchronisation s'arrête d'abord : le fichier du PC n'est pas remplacé. |
| B-08 | Fermer l'appli PC juste après une saisie (moins de 2 s). | La saisie est quand même dans le fichier (écrite à la fermeture). |
| B-09 | Provoquer un conflit (B-05), modifier encore sur le téléphone pendant que la fenêtre du PC est ouverte, puis choisir « Ce PC ». | Le PC montre de nouveau le conflit avec le dernier contenu du téléphone au lieu de l'écraser. |
| B-10 | Pendant un conflit, fermer l'appli PC. | Fenêtre « Modifications non enregistrées » : « Enregistrer une copie… » écrit un classeur lisible ; « Annuler » garde l'appli ouverte. |
| B-11 | PC : ouvrir la modification d'une opération, la supprimer sur le téléphone, attendre la synchronisation. | Le panneau du PC se ferme avec un message. |

## C. Appli PC (tableau de bord)

| # | Étape | Résultat attendu |
|---|---|---|
| C-01 | Écran d'accueil : « Essayer avec la démonstration ». | Tableau de bord ; bandeau « Démonstration : … jamais enregistrées ». |
| C-02 | Démonstration : saisir une opération, fermer et relancer l'appli. | Démonstration rouverte, saisie disparue (jamais enregistrée) ; aucun fichier créé. |
| C-03 | Tableau de bord : lire le patrimoine, « Sur douze mois », « Ce mois-ci », « Mis de côté », l'évolution et la répartition. | Chiffres cohérents avec le téléphone (même total, mêmes parts qui font 100 %). Dates en français (« 12 octobre 2026 »). |
| C-04 | Tuiles des comptes : valeur, variation, petite courbe, aperçu (dépenses du mois et catégories ; intérêts et dernier relevé ; plus-value et positions). Survoler puis cliquer une tuile. | Bord souligné au survol ; le clic ouvre le compte. |
| C-05 | « Réorganiser », déplacer une tuile avec les flèches, « Terminer » ; relancer. | Nouvel ordre gardé, et retrouvé sur le téléphone après synchronisation. |
| C-06 | Agrandir et réduire la fenêtre, ouvrir le panneau de saisie. | Les tuiles se réorganisent (plus ou moins de colonnes, empilées quand c'est étroit) ; rien de coupé. |
| C-07 | Vue d'un compte Courant (mois par mois), d'un livret, d'un PEA (tableau des positions, répartition des titres). | Tuiles de chiffres, courbe, opérations ; mêmes valeurs que le téléphone. |
| C-07b | Comptes de biens sur le PC : page d'un compte Immobilier et d'un compte Collection ; « Ajouter un objet », « Vendre », modifier un bien vendu, Ctrl+N depuis cette page ; créer un compte de biens (type dans la liste). | Tuiles valeur, nombre, plus-values ; panneaux d'achat et de vente (plus-value en direct) ; prix d'un bien vendu verrouillé avec un message ; la saisie d'opération ne propose pas les comptes de biens ; pas de solde initial pour ces types. Mêmes chiffres que le téléphone. |
| C-07c | Récurrences et crédits sur le PC : écran Opérations, section Récurrences (créer, modifier, supprimer) ; « Répéter » dans le panneau d'opération ; créer un compte Crédit (type dans la liste, mensualité affichée) ; page du crédit (tableau d'amortissement) ; tuile du tableau de bord ; budget avec une ligne Crédit ; laisser l'appli ouverte après minuit un jour d'échéance. | Mêmes calculs que le téléphone ; échéances créées à l'ouverture et au changement de jour ; tableau : prochaine mensualité en gras, passées en retrait ; budget : réel du thème Crédit = capital + intérêts du mois ; le patrimoine baisse du capital restant dû. |
| C-08 | « Nouvelle opération » et Ctrl+N (fenêtre au premier plan, sans avoir cliqué dedans ; puis après avoir fermé un panneau). | Panneau à droite, compte affiché présélectionné ; formulaire vide à chaque fois ; Échap ferme. |
| C-09 | Dans un champ, Entrée (et Entrée du pavé numérique) ; avec le focus sur « Supprimer » (Tab), Entrée. | Entrée dans un champ enregistre ; sur « Supprimer », c'est la suppression qui est demandée (avec confirmation). |
| C-10 | Saisir dépense, remboursement, revenu, virement, ordre avec nouvel actif. Passer d'« Ordre » (actif choisi) à « Revenu ». | Mêmes résultats que sur le téléphone ; le revenu n'est pas rattaché à l'actif. |
| C-11 | Date « 5/3/2026 » ; puis « 31/02/2026 » et Enregistrer. | La première est acceptée ; la seconde est refusée avec « Date illisible ». |
| C-12 | Cliquer une opération (dernières opérations ou vue d'un compte), la supprimer. | Confirmation demandée ; un virement est supprimé sur les deux comptes. |
| C-13 | Comptes : ajouter, modifier, archiver (tuile repliée sous « Comptes archivés »), supprimer (simple et définitive). | Mêmes règles que le téléphone (type verrouillé, confirmation). |
| C-14 | Réglages › Catégories : ajouter (dépense puis revenu sans fermer le panneau), choisir une icône, supprimer avec réaffectation. | Chaque panneau repart vide avec la bonne nature. |
| C-15 | PEA : « Saisir des cours » en ne changeant qu'un seul cours. | Seul ce cours est enregistré. |
| C-16 | Réglages : apparence, « Hausses en vert, baisses en rouge », mode discret ; relancer. | Choix conservés ; couleurs sur les variations et plus-values seulement. |
| C-17 | Rendre le fichier d'échange illisible pendant que l'appli est ouverte, puis le remettre. | Bandeau « Fichier d'échange refusé », données précédentes affichées, fichier jamais écrasé ; relu dès qu'il redevient valide. |
| C-18 | Laisser l'appli ouverte au passage de minuit (ou changer la date de Windows). | La date de calcul suit en moins d'une minute. |
| C-18b | Vue du compte courant › Relevés › « Rapprocher un relevé… », choisir l'export CSV du site de la banque (un mois). | Période et nombre de lignes justes ; opérations déjà saisies dans « Retrouvées » ; lignes manquantes proposées avec un libellé et une catégorie plausibles ; après « Créer », elles passent dans « Retrouvées » ; « Dans l'appli, pas chez la banque » ne liste que des opérations réellement absentes du relevé. Noter toute ligne mal lue (montant, date, libellé). |
| C-18c | Bilan › Mois par mois : lire les moyennes, les barres et le tableau ; cliquer un montant du tableau. | Chiffres cohérents avec les comptes ; le clic ouvre la Recherche filtrée sur la catégorie et le mois. |
| C-18d | Recherche : Ctrl+F (même depuis un autre écran), chercher un marchand, un montant (« 54,90 »), filtrer par compte et période. | Résultats et total justes, accents et majuscules ignorés. |
| C-18e | Sélectionner plusieurs dépenses (cases, Ctrl+clic, Maj+clic), « Changer la catégorie… » ; puis une sélection mêlant dépenses et revenus. | Catégorie changée d'un coup (et sur le téléphone après synchronisation) ; mélange : bouton désactivé avec explication. |
| C-18f | Supprimer une sélection contenant un virement, puis un achat dont dépend une vente. | Confirmation ; virement supprimé sur les deux comptes ; achat refusé avec un message. |
| C-18g | Opérations › Saisie rapide : cinq tickets à la suite au clavier (Tab, Entrée), un revenu avec « + », Ctrl+Entrée. | Catégories proposées plausibles ; tout enregistré d'un coup ; une ligne fausse est signalée et rien n'est enregistré. |
| C-18h | Bilan › Bilan annuel de l'année écoulée et de l'année en cours, avec un bien acheté, un bien vendu et un crédit ; « Exporter en PDF… ». | Chiffres cohérents avec l'Analyse ; avoirs et crédits sous le patrimoine ; sections Biens (achats, ajouts déjà possédés, ventes et plus-values) et Crédits (capital remboursé, intérêts) justes ; le PDF s'ouvre et s'imprime proprement (accents, euros, pages numérotées, libellés longs raccourcis, notes sur plusieurs lignes). |
| C-18i | Projection : changer horizon, épargne mensuelle, rendements, part en bourse ; « Valeurs par défaut » ; avec un crédit. | Chiffres et courbe mis à jour ; prudent ≤ central ≤ favorable ; « Crédits restant dus » diminue avec l'horizon (0 après la dernière mensualité). |
| C-18j | Budget : créer des lignes d'entrées, de dépenses, d'investissements ; ranger les dépenses dans les thèmes (le menu ne propose que Vie quotidienne, Abonnements, Transports, Logement, Crédit) ; suivre un compte pour un investissement ; basculer Prévu / Réel du mois ; changer de mois ; passer en thème sombre ; relancer l'appli. | Schéma lisible en clair et en sombre (étiquettes sans chevauchement) : investissements en haut, dépenses regroupées par thème avec leur détail à droite, reste en bas ; réel et écart sur la ligne de chaque thème ; reste ou déficit justes ; réel et écarts cohérents avec l'Analyse ; budget retrouvé après relance. |
| C-18k | Objectifs : depuis la page vide, reprendre une idée ; créer un objectif de chaque nature (patrimoine, compte, type, épargne mensuelle, taux, montant à la main, à cocher) avec et sans échéance ; modifier, cocher, supprimer ; masquer les montants ; relancer l'appli. | Avancements cohérents avec le tableau de bord et l'Analyse ; rythme, date estimée et effort mensuel plausibles ; états justes (en bonne voie, en retard, échéance dépassée, atteint) ; saisies illisibles refusées avec un message ; montants masqués en mode discret ; objectifs retrouvés après relance. |
| C-03b | Tableau de bord : évolution et répartition côte à côte ; agrandir et réduire la fenêtre ; passer en sombre. | Les deux tuiles se partagent la largeur à parts égales ; répartition rangée de la plus grande part (à gauche, la plus foncée en clair, la plus claire en sombre) à la plus petite, légende dans le même ordre ; pas de ligne « Crédits à déduire ». |
| C-18l | Volet latéral : parcourir les entrées. | Dans l'ordre : Tableau de bord, Objectifs, Recherche, Opérations, Budget, Projection, Bilan, Réglages ; Opérations montre la saisie rapide puis les récurrences et les mensualités ; Bilan garde l'onglet choisi (mois par mois ou annuel) quand on revient ; Réglages se termine par les catégories (panneau de modification inchangé). |
| C-19 | `PatrimoinePlus.exe --verifier chemin\vers\patrimoineplus-donnees.xlsx` dans un terminal. | Code de sortie 0 ; date d'exemple en français (« 12 octobre 2026 »). |

## D. Correctifs de la revue du 2026-10-13

| # | Étape | Résultat attendu |
|---|---|---|
| D-01 | (Téléphone puis PC) Vendre un objet avec le prix versé sur un livret, puis supprimer définitivement ce livret. | La vente reste (l'objet n'est pas « redevenu détenu ») sans versement ; les autres comptes ne bougent pas. |
| D-02 | Supprimer définitivement le compte qui a payé un objet vendu. | L'achat et sa vente disparaissent ensemble ; le fichier reste lisible sur l'autre appareil. |
| D-03 | Changer le type d'un compte sans opération mais utilisé par une récurrence ou un crédit. | Refusé : « Le type ne peut plus changer : un crédit ou une opération récurrente utilise ce compte. » |
| D-04 | Modifier un compte sans opération pour le passer en « Objets de collection ». | Accepté ; son solde initial disparaît. |
| D-05 | Modifier l'achat d'un objet vendu pour en faire une dépense (PC : nature de l'opération). | Refusé avec la date de la vente. |
| D-06 | Créer une récurrence de remboursement (dépense, « Remboursement » coché), la rouvrir et enregistrer sans rien changer. | Elle reste un remboursement (montant positif) ; aucune échéance recréée. |
| D-07 | Téléphone : récurrence avec une date de fin (« Fin (facultatif) », JJ/MM/AAAA), puis une date illisible. | Fin enregistrée, plus d'échéance après ; date illisible signalée sous le champ. |
| D-08 | Saisir une opération avec « Répéter : Chaque mois » ; regarder « prochaine échéance ». | Prochaine échéance le mois suivant ; aucune opération en double aujourd'hui. |
| D-09 | Budget : catégories « Prêt-à-porter » et « Prêt immobilier ». | La première dans Vie quotidienne, la seconde dans Crédit. |
| D-10 | Créer un crédit commencé il y a deux ans ; Bilan de l'année. | Capital remboursé et intérêts comptés seulement depuis la création dans l'appli. |
| D-11 | Double-clic rapide sur « Enregistrer » (PC : panneaux d'opération, compte, bien, récurrence, objectif ; téléphone : bien, récurrence, crédit). | Un seul enregistrement. |
| D-12 | PC : passer de la démonstration aux vraies données après avoir ouvert Projection, Saisie rapide et une recherche. | Hypothèses, lignes et critères remis à zéro (rien de la démo). |
| D-13 | Mode discret : page d'un crédit (PC et téléphone), Projection, ventes du Bilan. | Capital emprunté, intérêts restants, crédits restant dus et prix d'achat masqués. |
| D-14 | Supprimer une catégorie de dépense alors qu'une catégorie archivée existe. | L'archivée n'est pas proposée en remplacement ; un nouveau crédit ne la réutilise pas. |
| D-15 | Modifier une donnée sur le téléphone pendant qu'il importe le fichier du PC (difficile à provoquer : synchroniser juste après une saisie). | Au pire « Données modifiées pendant la synchronisation… » ; la saisie n'est jamais perdue, la synchronisation suivante règle l'écart. |

## E. Crédits : mensualité unique et bien financé (format 5)

À faire avec les **deux applis mises à jour ensemble** : une ancienne version
refuse un fichier au format 5.

| # | Étape | Résultat attendu |
|---|---|---|
| E-01 | Avant la mise à jour, noter les soldes du compte courant et des comptes Crédit ; mettre à jour le téléphone puis le PC ; ouvrir les deux. | Les anciennes mensualités (virement du capital + « Intérêts et assurance ») sont devenues une seule opération « Mensualité … » chacune ; soldes et patrimoine identiques à avant ; aucun conflit de synchronisation. |
| E-02 | Vue du compte courant : une mensualité du mois. | Ligne « Mensualité · <crédit> » au montant total ; synthèse du mois : intérêts et assurance dans les dépenses (catégorie Crédit), le capital dans les virements. |
| E-03 | Vue du compte Crédit : la même opération. | « Remboursement depuis Compte courant » au montant du capital ; le capital restant dû baisse du capital seulement. |
| E-04 | Ouvrir une mensualité pour la modifier (téléphone et PC). | Mode « Crédit » : crédit, payeur, montant payé, dont capital, intérêts et assurance calculés ; enregistrer sans changement ne modifie rien. |
| E-05 | Depuis la page d'un crédit, « Nouvelle opération » (PC : Ctrl+N ; téléphone : « + »). | Mode « Crédit » déjà choisi, mensualité du mois proposée ; changer la date d'un mois propose la mensualité de ce mois. |
| E-06 | Remboursement anticipé : mode « Crédit », montant 2 000 €, « Dont capital » vide. | Accepté sans catégorie ; tout le montant fait baisser le capital restant dû ; aucune dépense. |
| E-07 | Capital supérieur au montant, compte payeur = un compte Crédit. | Refusés avec un message sous le champ. |
| E-08 | Créer un crédit en choisissant « Bien financé » (bien détenu d'un compte Immobilier ou Collection). | Page du crédit : tuile « Bien financé » avec prix d'achat, restant dû et valeur nette ; page du bien : « reste dû … · valeur nette … » et valeur nette du compte. |
| E-09 | Sur la page d'un crédit existant, changer le bien financé, puis « Aucun ». | Lien enregistré et retrouvé sur l'autre appareil après synchronisation ; « Aucun » retire la valeur nette. |
| E-10 | Supprimer l'achat d'un bien financé. | Le crédit reste, sans bien financé. |
| E-11 | Tableau de bord (PC) et accueil (téléphone) avec au moins un crédit. | « Patrimoine net » en grand ; dessous « Brut … · Crédits … » (crédits en positif) ; net = brut − crédits. Sans crédit : affichage inchangé. |
| E-11b | Tuiles du tableau de bord, répartition (PC et accueil du téléphone), onglet Comptes, avec un crédit lié à un bien et un crédit sans bien. | Le compte de biens vaut prix − restant dû (« Net des crédits restant dus »), sa part et sa courbe aussi ; le crédit lié s'affiche en positif « Restant dû · déduit de … », sans part ; le crédit sans bien apparaît en « Crédits à déduire » sous la répartition ; parts des avoirs = 100 % ; total = net. |
| E-11c | Ajouter un bien déjà possédé daté d'il y a plusieurs années. | La courbe du patrimoine ne remonte pas à cette date (pas de mois où seul le bien compte). |
| E-12 | Mode discret. | Montants masqués partout, y compris brut, crédits, valeur nette et « Intérêts et assurance » de la saisie. |
| E-13 | Budget et Analyse sur un mois avec mensualité. | Thème Crédit = capital + intérêts et assurance ; Analyse : la catégorie Crédit ne compte que les intérêts et l'assurance. |
| E-14 | Ouvrir le classeur dans Excel. | Nature « Mensualité » sur les onglets Courant et Crédit ; onglet Crédits : colonne « Bien financé ». |
| E-15 | Téléphone : saisie d'une opération sur un petit écran. | Les cinq modes (Dépense, Revenu, Virement, Ordre, Crédit) lisibles en entier. |

## F. Limites connues (pas des anomalies)

- Les cours automatiques ne sont récupérés que par le téléphone.
- Titres cotés uniquement hors zone euro : pas de conversion de devise.
- Légende de la répartition coupée avec une police à 130 % (Android) ; fond qui
  peut clignoter au démarrage en mode clair forcé.
- Appli PC non signée : avertissement Windows au premier lancement.
- PC : barre de titre Windows claire même en mode sombre ; sélecteur de
  dossier de Java.
- Téléphone : quand la liste tient sur l'écran, le bouton « + » peut couvrir le
  montant de la dernière ligne.
- Pas de confirmation pour « Annuler la vente » ni pour supprimer une
  récurrence ou un objectif.
- Les mensualités d'un crédit ne sont pas listées dans les récurrences du
  téléphone (elles sont sur la page du crédit).
- Mode discret : les pourcentages (variation annuelle, taux des objectifs) et
  les montants dans les champs de saisie (budget, épargne de la projection)
  restent visibles.
- Désarchiver un compte crée les échéances manquées pendant l'archivage.
- Valeur nette d'un bien : à 100 % (prix d'achat et crédits entiers), sans
  les parts détenues ; le patrimoine, lui, applique les parts.
- Un remboursement anticipé ne recalcule pas le tableau d'amortissement : les
  mensualités suivantes gardent leur montant ; la dernière peut alors être
  à ajuster à la main.
- Un objectif sur un compte Crédit (solde négatif) ne peut pas s'exprimer.
