package fr.maubin.patrimoineplus.domain.model

import java.util.UUID

/*
	Catalogue de départ proposé au premier lancement (étape 3) et utilisé par
	les données de démonstration. Ce ne sont que des propositions : tout se
	modifie ou se supprime ensuite dans l'application.
*/
object DefaultCatalog
{
	// Catégories de dépense proposées : nom et clé d'icône.
	private val kExpenses = listOf(
		"Logement" to "home",
		"Alimentation" to "cart",
		"Transport" to "car",
		"Santé" to "health",
		"Loisirs" to "ticket",
		"Shopping" to "bag",
		"Abonnements" to "repeat",
		"Impôts" to "bank",
		"Cadeaux" to "gift",
		"Divers" to "dots"
	)

	// Catégories de revenu proposées : nom et clé d'icône.
	private val kIncomes = listOf(
		"Salaire" to "work",
		"Intérêts" to "percent",
		"Dividendes" to "chart",
		"Remboursements" to "undo",
		"Autres revenus" to "plus"
	)

	/*
		Construit les catégories proposées, avec de nouveaux identifiants.
	*/
	fun categories(): List<Category> =
		kExpenses.mapIndexed { inIndex, (inName, inIcon) ->
			Category(UUID.randomUUID().toString(), inName, inIcon, CategoryKind.EXPENSE, inIndex)
		} + kIncomes.mapIndexed { inIndex, (inName, inIcon) ->
			Category(UUID.randomUUID().toString(), inName, inIcon, CategoryKind.INCOME, inIndex)
		}
}
