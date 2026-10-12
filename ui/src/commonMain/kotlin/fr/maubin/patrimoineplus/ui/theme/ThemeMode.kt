package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/*
	Choix d'apparence de l'application (téléphone et PC).
	label : libellé affiché dans les réglages
*/
enum class ThemeMode(val label: String)
{
	SYSTEM("Système"),
	LIGHT("Clair"),
	DARK("Sombre")
}

/*
	Mode sombre effectif : le choix de l'utilisateur, ou celui du système.
*/
@Composable
fun ThemeMode.isDark(): Boolean =
	when (this)
	{
		ThemeMode.SYSTEM -> isSystemInDarkTheme()
		ThemeMode.LIGHT  -> false
		ThemeMode.DARK   -> true
	}
