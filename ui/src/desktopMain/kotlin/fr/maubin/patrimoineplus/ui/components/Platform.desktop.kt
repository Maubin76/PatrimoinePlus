package fr.maubin.patrimoineplus.ui.components

import androidx.compose.runtime.Composable

/*
	Animations des chiffres : toujours actives sur PC (pas de réglage système
	équivalent à celui d'Android).
*/
@Composable
actual fun animationsEnabled(): Boolean = true
