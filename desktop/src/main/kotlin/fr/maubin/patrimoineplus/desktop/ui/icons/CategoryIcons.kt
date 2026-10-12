package fr.maubin.patrimoineplus.desktop.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/*
	Icônes des catégories, au trait fin comme celles de la navigation. Chaque
	catégorie enregistre la clé de son icône ; une clé inconnue retombe sur
	« dots ».
*/
object CategoryIcons
{
	// Icônes disponibles, dans l'ordre de la grille de choix.
	private val kIcons: List<Pair<String, ImageVector>> = listOf(
		"home" to LineIcons.Home,
		"cart" to lineIcon("cart", "M3 4 H5.5 L7.5 15 H18 L20 7.5 H6.4", circle(9f, 19f, 1.4f), circle(17f, 19f, 1.4f)),
		"food" to lineIcon("food", "M6 3 V9 A2.5 2.5 0 0 0 11 9 V3", "M8.5 3 V21", "M17 21 V3 C14.5 4.5 14 8 14 11 H17"),
		"coffee" to lineIcon("coffee", "M5 8 H16 V13 A5 5 0 0 1 11 18 H10 A5 5 0 0 1 5 13 Z", "M16 9.5 H17.5 A2 2 0 0 1 17.5 13.5 H16", "M4 21 H17"),
		"car" to lineIcon("car", "M3.5 15 V12 L5.5 7 H18.5 L20.5 12 V15 Z", "M3.5 12 H20.5", circle(7.5f, 17f, 1.6f), circle(16.5f, 17f, 1.6f)),
		"plane" to lineIcon("plane", "M2.5 13 L21 6 L14 21 L11.5 14 Z", "M11.5 14 L21 6"),
		"health" to lineIcon("health", "M9 4 H15 V9 H20 V15 H15 V20 H9 V15 H4 V9 H9 Z"),
		"sport" to lineIcon("sport", "M3 12 H21", "M6 8 V16", "M18 8 V16", "M3.5 10 V14", "M20.5 10 V14"),
		"ticket" to lineIcon("ticket", "M4 7 H20 V10 A2 2 0 0 0 20 14 V17 H4 V14 A2 2 0 0 0 4 10 Z", "M14 7 V17"),
		"bag" to lineIcon("bag", "M5 8 H19 L18 20 H6 Z", "M9 8 V6.5 A3 3 0 0 1 15 6.5 V8"),
		"repeat" to lineIcon("repeat", "M4 11 V9.5 A3 3 0 0 1 7 6.5 H19", "M16 3.5 L19 6.5 L16 9.5", "M20 13 V14.5 A3 3 0 0 1 17 17.5 H5", "M8 20.5 L5 17.5 L8 14.5"),
		"bolt" to lineIcon("bolt", "M13 3 L5.5 13.5 H12 L11 21 L18.5 10.5 H12 Z"),
		"phone" to lineIcon("phone", "M8 3 H16 A1.5 1.5 0 0 1 17.5 4.5 V19.5 A1.5 1.5 0 0 1 16 21 H8 A1.5 1.5 0 0 1 6.5 19.5 V4.5 A1.5 1.5 0 0 1 8 3 Z", "M11 18 H13"),
		"book" to lineIcon("book", "M4 5 C7 4 10 4.5 12 6 C14 4.5 17 4 20 5 V19 C17 18 14 18.5 12 20 C10 18.5 7 18 4 19 Z", "M12 6 V20"),
		"paw" to lineIcon("paw", circle(7f, 9f, 1.6f), circle(10.5f, 6f, 1.6f), circle(13.5f, 6f, 1.6f), circle(17f, 9f, 1.6f),
			"M12 11.5 C9 11.5 6.5 15 7.5 17.5 C8.5 19.5 10.5 18.5 12 18.5 C13.5 18.5 15.5 19.5 16.5 17.5 C17.5 15 15 11.5 12 11.5 Z"),
		"gift" to lineIcon("gift", "M4 9 H20 V12.5 H4 Z", "M5.5 12.5 V20 H18.5 V12.5", "M12 9 V20",
			"M12 9 C10 5 6.5 5.5 7.5 8 C8 9 10 9 12 9 C14 9 16 9 16.5 8 C17.5 5.5 14 5 12 9"),
		"bank" to lineIcon("bank", "M3.5 9 L12 4 L20.5 9 Z", "M5 20 H19", "M6.5 10.5 V17.5", "M10 10.5 V17.5", "M14 10.5 V17.5", "M17.5 10.5 V17.5"),
		"work" to lineIcon("work", "M4 8 H20 V19 H4 Z", "M9 8 V5.5 H15 V8", "M4 13 H20"),
		"percent" to lineIcon("percent", "M18 6 L6 18", circle(7.5f, 7.5f, 2f), circle(16.5f, 16.5f, 2f)),
		"chart" to lineIcon("chart", "M4 4 V20 H20", "M7 15 L11 11 L14 13 L19 7"),
		"undo" to lineIcon("undo", "M9 14 L4 9 L9 4", "M4 9 H15 A5 5 0 0 1 15 19 H11"),
		"plus" to lineIcon("plus", "M12 5 V19", "M5 12 H19"),
		"dots" to lineIcon("dots", circle(6f, 12f, 1.2f), circle(12f, 12f, 1.2f), circle(18f, 12f, 1.2f))
	)

	private val kByKey = kIcons.toMap()											// icônes par clé

	// Noms lus par TalkBack, par clé.
	private val kLabels = mapOf(
		"home" to "Maison", "cart" to "Caddie", "food" to "Couverts", "coffee" to "Tasse", "car" to "Voiture",
		"plane" to "Avion", "health" to "Croix médicale", "sport" to "Haltère", "ticket" to "Billet", "bag" to "Sac",
		"repeat" to "Abonnement", "bolt" to "Éclair", "phone" to "Téléphone", "book" to "Livre", "paw" to "Patte",
		"gift" to "Cadeau", "bank" to "Banque", "work" to "Mallette", "percent" to "Pourcentage", "chart" to "Courbe",
		"undo" to "Retour", "plus" to "Plus", "dots" to "Divers"
	)

	// Clés disponibles, dans l'ordre de la grille de choix.
	val keys: List<String> = kIcons.map { it.first }

	/*
		Icône d'une clé ; « dots » pour une clé inconnue.
		inKey : clé enregistrée sur la catégorie
	*/
	fun of(inKey: String): ImageVector = kByKey[inKey] ?: kByKey.getValue("dots")

	/*
		Nom lisible d'une icône, pour TalkBack.
		inKey : clé
	*/
	fun label(inKey: String): String = kLabels[inKey] ?: inKey
}
