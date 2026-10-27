#!/usr/bin/env bash
# Test de fumée de l'APK release (R8, réduit) sur un émulateur, lancé par la CI :
# installation, lancement, écran de connexion (« Entrer »), assistant du premier
# lancement (« Passer »), accueil (« Patrimoine »), onglet Réglages (« Apparence »),
# retour à l'accueil ; aucun plantage (journal « crash » vide, processus vivant).
# L'appli est pilotée par son arbre d'accessibilité (uiautomator), sans dépendre
# de son code : c'est bien l'APK publié qui est vérifié.
# Usage : ci/release-smoke-android.sh chemin/vers/app-release.apk
set -euo pipefail

kApk="$1"																	# APK à vérifier
kPackage="fr.maubin.patrimoineplus"											# appli
kDump="/sdcard/ui.xml"															# arbre de l'écran

# Arbre de l'écran courant (texte et descriptions des éléments).
screen()
{
	adb shell uiautomator dump "$kDump" > /dev/null 2>&1 || true
	adb shell cat "$kDump" 2> /dev/null || true
}

# Attend qu'un motif apparaisse dans l'arbre de l'écran ; échoue au bout de 60 s.
# $1 : motif attendu (par exemple text="Entrer", pour un texte exact)
wait_for()
{
	for _ in $(seq 1 60)
	do
		if screen | grep -q -- "$1"; then return 0; fi
		close_update
		sleep 1
	done
	echo "::error::Introuvable à l'écran : $1"
	screen | sed 's/></>\n</g' | grep -o 'text="[^"]*"\|content-desc="[^"]*"' | grep -v '=""' | head -40
	return 1
}

# Touche le centre de l'élément qui porte un texte ou une description.
# $1 : texte ou description de l'élément
tap()
{
	local vNode vBounds vX1 vY1 vX2 vY2
	vNode=$(screen | sed 's/></>\n</g' | grep -- "text=\"$1\"\|content-desc=\"$1\"" | head -1)
	vBounds=$(echo "$vNode" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '[0-9]*' | tr '\n' ' ')
	read -r vX1 vY1 vX2 vY2 <<< "$vBounds"
	if [ -z "${vX2:-}" ]; then echo "::error::Impossible de toucher : $1"; return 1; fi
	adb shell input tap $(( (vX1 + vX2) / 2 )) $(( (vY1 + vY2) / 2 ))
}

# Referme la fenêtre de mise à jour si une version plus récente est publiée.
close_update()
{
	if screen | grep -q 'est disponible'; then tap "Plus tard" || true; fi
}

# Échoue si l'appli a planté ou n'est plus lancée.
check_alive()
{
	if adb logcat -d -b crash | grep -q "$kPackage"
	then
		echo "::error::Plantage de l'appli"
		adb logcat -d -b crash | tail -60
		return 1
	fi
	adb shell pidof "$kPackage" > /dev/null || { echo "::error::L'appli n'est plus lancée"; adb logcat -d | tail -80; return 1; }
}

adb install -r "$kApk"
adb logcat -c
adb shell am start -W -n "$kPackage/.MainActivity"
wait_for 'text="Entrer"'
check_alive
tap "Entrer"
wait_for 'text="Bienvenue"'
tap "Passer, je configurerai plus tard"
wait_for 'text="Patrimoine"'
check_alive
tap "Réglages"
wait_for 'text="Apparence"'
tap "Accueil"
wait_for 'text="Patrimoine"'
sleep 2
check_alive
echo "APK release vérifié : lancement, connexion, premier lancement, accueil et réglages sans plantage."
