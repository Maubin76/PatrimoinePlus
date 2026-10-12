package fr.maubin.patrimoineplus.ui.components

import androidx.compose.runtime.Composable

/*
	Indique si les animations du système sont actives. Quand elles sont
	coupées (Android : Accessibilité > Supprimer les animations), les chiffres
	changent d'un coup au lieu de rouler. Toujours vrai sur PC.
*/
@Composable
expect fun animationsEnabled(): Boolean
