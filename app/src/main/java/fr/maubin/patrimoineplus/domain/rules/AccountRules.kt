package fr.maubin.patrimoineplus.domain.rules

import fr.maubin.patrimoineplus.domain.model.Category

/*
	Violation d'une règle de saisie d'un compte ou d'une catégorie.
	message : explication pour l'utilisateur
*/
sealed class SetupError(val message: String)
{
	// Le nom est vide.
	data object BlankName : SetupError("Le nom est obligatoire.")

	// La part détenue sort de 1 à 100 %.
	data object InvalidShare : SetupError("La part détenue doit être comprise entre 1 et 100 %.")

	// Une catégorie ne peut être réaffectée qu'à une autre catégorie de même nature.
	data object KindMismatch : SetupError("Choisissez une catégorie de même nature (dépense ou revenu).")

	// Une catégorie ne peut pas être réaffectée à elle-même.
	data object SameCategory : SetupError("Choisissez une autre catégorie.")

	// La catégorie est utilisée : il faut choisir une catégorie de remplacement.
	data object CategoryInUse : SetupError("Cette catégorie est utilisée : choisissez une catégorie de remplacement.")

	// Élément introuvable (supprimé entre-temps).
	data object NotFound : SetupError("Élément introuvable.")
}

const val kMinShare = 1															// part détenue minimale, en %
const val kMaxShare = 100														// part détenue maximale, en %

/*
	Vérifie les champs d'un compte ; liste vide = valide.
	inName  : nom
	inShare : part détenue, en %
*/
fun validateAccount(
	inName  : String,
	inShare : Int
): List<SetupError> =
	buildList {
		if (inName.isBlank())
		{
			add(SetupError.BlankName)
		}
		if (inShare !in kMinShare..kMaxShare)
		{
			add(SetupError.InvalidShare)
		}
	}

/*
	Vérifie qu'une catégorie peut être réaffectée à une autre avant suppression.
	inFrom : catégorie supprimée
	inTo   : catégorie de remplacement
*/
fun validateReassignment(
	inFrom : Category,
	inTo   : Category
): List<SetupError> =
	buildList {
		if (inFrom.id == inTo.id)
		{
			add(SetupError.SameCategory)
		}
		if (inFrom.kind != inTo.kind)
		{
			add(SetupError.KindMismatch)
		}
	}
