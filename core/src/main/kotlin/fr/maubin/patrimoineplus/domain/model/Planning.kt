package fr.maubin.patrimoineplus.domain.model

import java.time.LocalDate

/*
	Fréquence d'une opération récurrente.
	label  : libellé affiché
	months : nombre de mois entre deux échéances
*/
enum class Frequency(
	val label  : String,
	val months : Int
)
{
	MONTHLY("Chaque mois", 1),
	QUARTERLY("Chaque trimestre", 3),
	YEARLY("Chaque année", 12)
}

/*
	Opération récurrente : modèle d'une dépense, d'un revenu ou d'un
	virement créé automatiquement à chaque échéance. Les échéances partent de
	la date de début, de mois en mois selon la fréquence (un 31 devient le
	dernier jour des mois plus courts).
	id          : identifiant (UUID)
	label       : libellé des opérations créées
	accountId   : compte (de départ pour un virement)
	toAccountId : compte d'arrivée d'un virement ; null pour une dépense ou un revenu
	categoryId  : catégorie d'une dépense ou d'un revenu ; null pour un virement
	amountCents : montant ; signé pour une dépense ou un revenu (négatif = dépense), positif pour un virement
	frequency   : fréquence
	start       : première échéance
	end         : dernière échéance possible ; null sans fin
	last        : dernière échéance déjà créée ; null avant la première (une
	              opération supprimée n'est jamais recréée)
	notes       : notes des opérations créées
*/
data class Recurrence(
	val id          : String,
	val label       : String,
	val accountId   : String,
	val toAccountId : String?,
	val categoryId  : String?,
	val amountCents : Long,
	val frequency   : Frequency,
	val start       : LocalDate,
	val end         : LocalDate? = null,
	val last        : LocalDate? = null,
	val notes       : String?    = null
)

/*
	Crédit amortissable, à mensualités constantes, rattaché à un compte de
	type Crédit (solde négatif : capital restant dû). Chaque échéance crée une
	mensualité (LOAN_PAYMENT) : le compte payeur est débité du total, le
	compte Crédit crédité du capital ; intérêts et assurance sont une dépense.
	id          : identifiant (UUID)
	accountId   : compte Crédit
	principal   : montant emprunté, en centimes
	rateBp      : taux annuel nominal, en points de base (345 = 3,45 %)
	months      : nombre de mensualités
	firstDue    : date de la première mensualité
	insurance   : assurance mensuelle, en centimes (0 sans assurance)
	payFrom     : compte payeur des mensualités
	categoryId  : catégorie de dépense des intérêts et de l'assurance
	last        : dernière mensualité déjà créée (ou payée avant le suivi dans
	              l'appli) ; null avant la première
	propertyId  : bien financé (achat d'un compte Immobilier ou Collection) ;
	              null pour un crédit sans bien (auto, prêt personnel)
*/
data class Loan(
	val id         : String,
	val accountId  : String,
	val principal  : Long,
	val rateBp     : Int,
	val months     : Int,
	val firstDue   : LocalDate,
	val insurance  : Long,
	val payFrom    : String,
	val categoryId : String,
	val last       : LocalDate? = null,
	val propertyId : String?    = null
)
