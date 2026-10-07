package fr.maubin.patrimoineplus.data.settings

/*
	Choix d'apparence de l'application.
	label : libellé affiché dans les réglages
*/
enum class ThemeMode(val label: String)
{
	SYSTEM("Système"),
	LIGHT("Clair"),
	DARK("Sombre")
}
