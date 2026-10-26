package fr.maubin.patrimoineplus.domain.rules

import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.Ownership

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

	// Propriétaires d'un compte invalides.
	data class Owners(val detail: String) : SetupError("Propriétaires : $detail")

	// Nom de profil déjà pris.
	data object ProfileTaken : SetupError("Un profil porte déjà ce nom.")

	// Le dernier profil ne peut pas être supprimé.
	data object LastProfile : SetupError("Il faut garder au moins un profil.")

	// Une catégorie ne peut être réaffectée qu'à une autre catégorie de même nature.
	data object KindMismatch : SetupError("Choisissez une catégorie de même nature (dépense ou revenu).")

	// Une catégorie ne peut pas être réaffectée à elle-même.
	data object SameCategory : SetupError("Choisissez une autre catégorie.")

	// La catégorie est utilisée : il faut choisir une catégorie de remplacement.
	data object CategoryInUse : SetupError("Cette catégorie est utilisée : choisissez une catégorie de remplacement.")

	// Le type d'un compte qui a des opérations ne peut plus changer.
	data object TypeLocked : SetupError("Le type ne peut plus changer : ce compte a des opérations.")

	// Un crédit ou une opération récurrente utilise le compte : son type ne change plus.
	data object TypeUsed : SetupError("Le type ne peut plus changer : un crédit ou une opération récurrente utilise ce compte.")

	// Un compte de biens n'a pas de solde initial.
	data object PropertyOpening : SetupError("Un compte Immobilier ou Collection n’a pas de solde initial : ajoutez ses biens un par un.")

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

/*
	Vérifie les propriétaires d'un compte : au moins un, chacun connu et une
	seule fois, part de 1 à 100 %, total au plus 100 %.
	inOwners   : propriétaires
	inProfiles : identifiants des profils connus
	Retour : message d'erreur ; null si valide
*/
fun ownersError(
	inOwners   : List<Ownership>,
	inProfiles : Set<String>
): String? =
	when
	{
		inOwners.isEmpty()                                           -> "aucun propriétaire."
		inOwners.any { it.profileId !in inProfiles }                 -> "propriétaire inconnu."
		inOwners.map { it.profileId }.toSet().size != inOwners.size  -> "propriétaire en double."
		inOwners.any { it.percent !in kMinShare..kMaxShare }         -> "part hors de 1 à 100 %."
		inOwners.sumOf { it.percent } > kMaxShare                    -> "les parts dépassent 100 % au total."
		else                                                         -> null
	}

