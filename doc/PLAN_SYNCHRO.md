# Plan : synchronisation intégrée en Wi-Fi local, téléphone relais

Objectif : synchroniser PatrimoinePlus entre plusieurs PC et un téléphone
Android sans application tierce ni serveur sur internet. La synchronisation
intégrée **remplace** le dossier synchronisé (Syncthing). Les
données ne quittent jamais le réseau de la maison.

Environnement visé : **3 PC et 1 téléphone**. Deux PC sont rarement allumés
en même temps avec l'appli ouverte ; le téléphone est presque toujours
disponible. Il sert donc de **relais** : il porte la version de référence et
la transmet d'un PC à l'autre.

Le plus délicat existe déjà et reste utilisé : empreintes de contenu
(`syncFingerprint`), décision importer / exporter / conflit (`decideSync`),
fenêtre de conflit, contrôle du format (`validateBackup`, fenêtre « Mise à
jour nécessaire »). Ce chantier change surtout le **transport** (connexion
directe au lieu d'un dossier partagé) et ajoute la **fusion élément par
élément**.

## 1. Le schéma

```
   PC bureau ─┐
   PC salon  ─┼──  Wi-Fi local  ──  téléphone (relais, version de référence)
   PC portable┘
```

- Les PC ne se parlent jamais entre eux : tout passe par le téléphone.
- **Le PC reste le serveur, le téléphone se connecte à lui.** Android ne
  permet pas de garder un serveur fiable en arrière-plan, alors qu'un PC dont
  l'appli est ouverte accepte les connexions à tout moment ; le téléphone,
  lui, peut se connecter même en veille (synchronisation en arrière-plan).
- Chaque PC s'appaire **une fois** avec le téléphone (3 appairages au total).

### Déroulé type

1. **Session sur un PC** : chaque saisie est enregistrée sur le PC environ
   1,5 s après, et transmise au téléphone dès qu'il est joignable.
2. **Fermeture du PC** : s'il reste des modifications que le téléphone n'a
   pas reçues, une fenêtre le dit : « Ces modifications n'ont pas encore été
   transmises au téléphone. Ouvrez PatrimoinePlus sur le téléphone… ». Dès
   que le téléphone se connecte : « Transmis ✓ » et le PC se ferme.
   « Fermer quand même » garde les modifications sur le PC ; elles partiront à
   sa prochaine ouverture.
3. **Le téléphone** a reçu la version à jour, avec l'appareil qui l'a
   modifiée et l'heure.
4. **Ouverture d'un autre PC** : bandeau « Vérification auprès du
   téléphone… ». Le téléphone se met à jour tout seul en arrière-plan sur le
   Wi-Fi (au mieux toutes les 15 min environ, Android espaçant ces tâches) ; « Synchroniser maintenant » sur le PC, ou l'ouverture de l'appli sur
   le téléphone, accélèrent. On peut travailler pendant ce temps ; le bandeau
   signale qu'on saisit peut-être sur une version ancienne.
5. **Saisies sur le téléphone** : transmises au prochain PC joignable, comme
   celles d'un PC.

## 2. Être sûr de travailler sur la dernière version

On ne peut pas l'empêcher absolument : chaque appareil doit pouvoir saisir
hors ligne (téléphone hors de la maison, PC fermé trop tôt). On garantit en
revanche que **rien n'est jamais écrasé en silence**, et on rend les conflits
rares.

1. **Auteur et date dans le fichier, pour l'affichage.** Chaque version
   enregistre l'appareil qui l'a modifiée et l'heure (« Dernière
   modification : PC du bureau, le 28/10 à 14:32 »).
2. **Empreintes pour la décision, jamais la date seule.** Se fier à « la plus
   récente gagne » ferait perdre des données si les horloges diffèrent, ou si
   deux appareils ont modifié chacun de leur côté. Pour chaque pair,
   l'appareil compare trois empreintes : la dernière version commune avec ce
   pair, la sienne, celle du pair :
   - seul l'autre a changé : il importe ;
   - seul lui a changé : il exporte ;
   - les deux ont changé : **fusion** (point 4).
3. **Écriture conditionnelle.** Un envoi dit « applique ces données
   **seulement si les tiennes ont toujours l'empreinte X** ». Si l'autre a
   changé entre-temps, il refuse ; l'appareil récupère la nouvelle version et
   refait la décision. Deux envois simultanés ne peuvent pas s'écraser.
4. **Fusion élément par élément.** Deux saisies différentes faites des deux
   côtés (une dépense sur le téléphone, une autre sur un PC) sont **gardées
   toutes les deux**, sans question. Le choix n'est demandé que si **le même
   élément** a été modifié des deux côtés. Les éléments ont déjà un
   identifiant et une date de modification ; il faut en plus mémoriser les
   suppressions (pour qu'un élément supprimé ailleurs ne « réapparaisse »
   pas).
5. **Synchroniser avant de travailler, envoyer tout de suite après.** À
   l'ouverture et au retour au premier plan, l'appareil récupère d'abord la
   version du pair ; après une saisie, il l'envoie en quelques secondes.
6. **État visible** : « à jour », « modifications en attente de
   transmission », « téléphone joignable » ou non, et depuis quand.
7. **Historique** : les 20 dernières modifications (appareil, heure,
   nombre d'éléments touchés), consultables dans la page Appareils.
8. **Toujours une sauvegarde** : avant chaque import et chaque jour, comme
   aujourd'hui.

**Écarté** : un verrou (« un seul appareil modifie à la fois »), qui
empêcherait toute saisie hors ligne sur le téléphone.

## 3. Uniquement sur le réseau familial

La synchronisation n'est **active que sur le Wi-Fi de la maison**. Ailleurs
(travail, hôtel, café, données mobiles), elle ne fait **rien** : aucune
recherche d'appareil, aucune connexion, aucune annonce sur le réseau.

### Reconnaître le réseau familial

- **Enregistré à l'appairage** : le réseau sur lequel se fait le premier
  appairage devient le réseau familial. La page Appareils permet d'en ajouter
  d'autres (box en 2,4 et 5 GHz, répéteur, nouvelle box) ou d'en retirer.
- **Téléphone et PC portables** : reconnus par le **nom du Wi-Fi** (SSID).
- **PC fixe branché par câble** : il n'a pas de nom de Wi-Fi ; il est
  reconnu par l'**identifiant de la box** (adresse matérielle de la
  passerelle), relevé à l'appairage.
- Le téléphone se synchronise seulement sur un Wi-Fi qui n'est pas facturé à
  l'usage (jamais en données mobiles).

### Ce qui se passe hors du réseau familial

- **Téléphone** : la synchronisation en arrière-plan ne démarre pas ;
  l'ouverture de l'appli et les saisies ne déclenchent rien ; l'état indique
  « Hors du réseau familial : synchronisation au retour à la maison ». Les
  saisies restent sur le téléphone et partent au retour.
- **PC** (portable emmené ailleurs) : le serveur n'écoute pas et ne s'annonce
  pas ; le bandeau d'ouverture et la fenêtre de fermeture ne demandent rien
  (pas de « Ouvrez l'appli sur le téléphone » au travail) ; l'état indique
  « Hors du réseau familial ».
- **Changement de réseau** pendant que l'appli est ouverte : détecté
  (évènements réseau d'Android, contrôle périodique sur le PC) ; la
  synchronisation se coupe ou reprend d'elle-même.
- **Double protection** : même sur un réseau reconnu par erreur (un autre
  Wi-Fi du même nom), rien n'est échangé sans un pair appairé qui prouve son
  identité par sa clé.

### Contrainte Android

Depuis Android 10, lire le nom du Wi-Fi demande l'**autorisation de
localisation** (et la localisation activée). L'appli ne s'en sert que pour
lire ce nom, jamais pour une position ; il faudra l'expliquer clairement à la
demande. Décision 14 : autorisation demandée à l'appairage.

## 4. La page « Appareils » du PC

Nouvelle entrée du rail d'icônes ; tout l'appairage s'y configure.

- **Appairer le téléphone** : affiche un QR code (adresse du PC, identifiant
  du PC, clé secrète de la paire, valable 5 minutes). Le téléphone le scanne une fois
  depuis ses Réglages, qui n'ont que ce bouton de scan (et la saisie manuelle
  de l'adresse en secours).
- **Appareils connus** : nom (modifiable), dernière synchronisation,
  « Retirer ».
- **État** : « À jour », « Modifications en attente de transmission »,
  « Téléphone joignable » ; bouton « Synchroniser maintenant ».
- **Historique** des dernières modifications.
- **Réseau familial** : réseaux reconnus (nom du Wi-Fi, ou box pour un PC
  branché par câble), « Ajouter le réseau actuel », « Retirer » ; indication
  « Vous êtes sur le réseau familial » ou « Hors du réseau familial ».
- **Réglages** :
  - nom de ce PC ;
  - « Rester dans la zone de notification à la fermeture » et « Démarrer
    avec Windows », désactivés par défaut.

## 5. Phases de développement

### Phase 0 — Essais préalables

- Découverte réseau (message de diffusion UDP) entre Android et Windows sur
  la box de l'utilisateur ; pare-feu Windows (autorisation sur les réseaux
  privés) ; échange TCP entre les deux appareils.
- Lecture du réseau courant : nom du Wi-Fi sur Windows et Android,
  identifiant de la box pour un PC branché par câble.
- Livrable : un petit utilitaire de diagnostic à lancer chez soi (pas de test
  sur émulateur ni à la souris dans ce projet).

### Phase 1 — Fusion et historique (`core`, sans réseau)

- **Suppressions mémorisées** (identifiant et date) dans les données et le
  fichier d'échange (nouveau format, fenêtre « Mise à jour nécessaire » pour
  l'appareil resté en arrière).
- **Fusion** de deux versions à partir de leur dernière version commune :
  éléments ajoutés, modifiés ou supprimés d'un seul côté appliqués
  automatiquement ; même élément modifié des deux côtés : conflit, pour cet
  élément seulement. Règles de cohérence vérifiées après fusion
  (`validateBackup`) : une opération dont le compte a été supprimé ailleurs,
  par exemple.
- **Auteur et date** de la dernière modification, **historique** des
  20 dernières.
- Interface **transport** qui sépare l'arbitrage de la façon d'échanger (le
  dossier synchronisé sert de premier transport le temps du passage, puis est
  retiré en phase 6).
- Tests JVM nombreux : c'est le cœur de la sûreté des données.

### Phase 2 — Identité, appairage, protocole (`core`)

- **Clé secrète par paire** PC ↔ téléphone, créée par le PC à l'appairage et
  transmise par le QR code (valable 5 minutes) ; chaque message est chiffré
  et authentifié avec elle (AES-GCM), avec protection contre le rejeu (date et
  numéro à usage unique) ; un appareil n'accepte que les pairs qui
  connaissent leur clé.
- Protocole : informations de l'appareil (identifiant, nom, version, format
  lu) ; lecture du contenu avec son empreinte ; écriture conditionnelle.
  Contenu en JSON (`encodeBackup`), vérifié comme aujourd'hui.
- État par pair : dernière version commune, date.
- **Réseau familial** : liste des réseaux reconnus, partagée par le
  téléphone avec chaque PC à l'appairage ; règle « synchroniser seulement
  sur un réseau reconnu » testée sans réseau réel.
- Retrait d'un appareil : il n'est plus accepté ; il garde sa copie locale
  (effacement à distance impossible).

### Phase 3 — Serveur du PC (`desktop`)

- Serveur intégré, démarré avec l'appli, en tâche de fond ; port fixe avec
  repli ; annonce sur le réseau local, **seulement sur le réseau familial**
  (arrêt et reprise quand le PC change de réseau).
- Fenêtre de fermeture (modifications non transmises) et bandeau
  d'ouverture.
- Options « Rester dans la zone de notification à la fermeture » et
  « Démarrer avec Windows », désactivées par défaut.
- Données rangées dans le dossier de l'appli ; sauvegardes quotidiennes
  (`PcBackup`) déplacées dans `Documents\PatrimoinePlus\Sauvegardes PC`,
  puisqu'il n'y a plus de dossier synchronisé.

### Phase 4 — Client du téléphone (`app`)

- Scan du QR code ; saisie manuelle de l'adresse en secours.
- **Seulement sur le réseau familial** (nom du Wi-Fi reconnu, Wi-Fi non
  facturé à l'usage) ; ailleurs, rien n'est tenté.
- Recherche des PC appairés sur le Wi-Fi ; synchronisation à l'ouverture,
  après chaque saisie (regroupée), et **en arrière-plan sur Wi-Fi**
  (WorkManager, réseau non facturé à l'usage).
- Sauvegarde de sécurité avant import conservée ; sauvegardes quotidiennes
  dans `Documents/PatrimoinePlus`, puisqu'il n'y a plus de dossier
  synchronisé.

### Phase 5 — Interface

- Page **Appareils** du PC (section 3), indicateur dans la barre d'état.
- Réglages du téléphone : scan, appareils connus, état, synchronisation
  maintenant.
- Fenêtre de conflit par élément (« Modifié sur le téléphone le … / sur le PC
  du bureau le … ») ; elle indique d'où vient chaque version.

### Phase 6 — Passage depuis Syncthing, puis retrait du mode dossier

- Assistant au premier lancement de la nouvelle version : une dernière
  synchronisation par le dossier, puis appairage ; les données à jour
  deviennent la version de référence du téléphone.
- Retrait du mode dossier (réglages, surveillance du fichier, textes et
  README) une fois le passage fait ; le classeur Excel reste le format des
  exports et des sauvegardes, et l'import d'un classeur reste possible.
- Sans mode de secours, la **saisie manuelle de l'adresse du PC** devient
  indispensable (Wi-Fi qui bloque la découverte), et l'utilitaire de
  diagnostic de la phase 0 reste livré.

### Phase 7 — Tests, documentation, release

- Tests serveur et client dans le même processus, sur l'interface réseau
  locale (sans réseau réel) ; Robolectric côté Android.
- Entrées dans `doc/PLAN_DE_TESTS.md` pour la passe manuelle à quatre
  appareils.
- README, `doc/DEVELOPPEMENT.md`, `doc/TESTS.md`, release (les deux applis à
  mettre à jour ensemble).

**Ordre de grandeur** : phase 1 en 3 à 4 sessions (la fusion est le gros
morceau), phases 2 à 4 en 3 à 4 sessions, phases 5 à 7 en 2 sessions.

## 6. Décisions

| # | Question | Décision |
|---|---|---|
| 1 | Organisation | Téléphone relais ; le PC reste le serveur ; pas de PC ↔ PC |
| 2 | Appairage | Une fois par PC avec le téléphone, par QR code, dans la page Appareils du PC |
| 3 | Décision de synchronisation | Empreintes (comparaison à trois) ; auteur et date pour l'affichage seulement |
| 4 | Enregistrement | En continu sur le PC ; fenêtre à la fermeture si rien n'a encore été transmis |
| 5 | Téléphone en arrière-plan | Oui, synchronisation automatique sur le Wi-Fi |
| 6 | Fusion élément par élément | Oui, dès la première version |
| 7 | Fermeture du PC | « Zone de notification » et « Démarrer avec Windows » en option, désactivés par défaut |
| 8 | Syncthing | Remplacé : assistant de passage, puis retrait du mode dossier |
| 9 | Serveur du PC | Serveur intégré à Java (aucune dépendance) — *par défaut* |
| 10 | QR code sur Android | ZXing (indépendant des services Google) — *par défaut* |
| 11 | Repli manuel | Saisie de l'adresse IP du PC quand la découverte est bloquée — indispensable sans Syncthing |
| 12 | Hors de la maison | Synchronisation inactive : aucune recherche, connexion ni annonce ; saisies gardées et transmises au retour |
| 13 | Réseau familial | Enregistré à l'appairage, modifiable dans la page Appareils ; nom du Wi-Fi, ou identifiant de la box pour un PC branché par câble |
| 14 | Wi-Fi sur Android | Autorisation de localisation demandée une seule fois à l'appairage, avec une explication (seul le nom du Wi-Fi est lu, jamais une position) ; refusée : la synchronisation reste inactive et l'appli explique comment l'accorder |
| 15 | Découverte | Message de diffusion UDP sur le réseau local (Java standard, sans bibliothèque), dernière adresse connue essayée d'abord, saisie manuelle en secours |
| 16 | Chiffrement | Clé secrète par paire transmise par le QR code ; messages chiffrés et authentifiés (AES-GCM) sur une connexion TCP ; pas de certificats (impossibles à créer en Java standard sans grosse bibliothèque) |

Point de vigilance : sans mode de secours, tout repose sur la découverte et
la connexion directe en Wi-Fi. La phase 0 (diagnostic sur le réseau réel)
conditionne la suite : si la box bloque les appareils entre eux, il faudra
le savoir avant d'écrire le reste.
