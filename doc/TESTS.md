# Tests

Tous les tests sont des tests JVM (`app/src/test`), lancés par :

```sh
./gradlew :app:testDebugUnitTest
```

## Ce qui est couvert

| Classe de test     | Code testé                         | Ce qui est vérifié |
|--------------------|------------------------------------|--------------------|
| `MoneyFormatTest`  | `domain/format/MoneyFormat.kt`     | montant masqué du mode discret ; zéro ; centimes complétés à deux chiffres ; séparateur des milliers (espace fine insécable) ; espace insécable avant « € » ; signe négatif ; signe « + » optionnel et jamais devant zéro ; bornes `Long.MIN_VALUE` / `Long.MAX_VALUE` |
| `ScreenshotTest`   | écrans Compose (Robolectric)       | **aucune assertion** : produit des captures (accueil clair, sombre, discret, vide ; comptes ; réglages) pour relecture humaine, via `./gradlew :app:recordRoborazziDebug`. Données synthétiques. Pas de comparaison automatique avec des images de référence. |

## Ce qui n'est pas couvert

- **Interface Compose, navigation, injection Hilt** : aucun test automatisé du
  comportement (clics, navigation, persistance du mode discret, animations).
  Les captures JVM montrent le rendu statique seulement ; vérification
  manuelle sur le téléphone.
- **Tests instrumentés** (`androidTest`) : aucun pour l'instant. La base Room
  et ses migrations seront testées à l'étape 2 ; le choix entre tests
  instrumentés et Robolectric y sera documenté.

## Vérifications par mutation

Aucune à ce stade : la première portera sur les calculs de soldes de l'étape 2.
