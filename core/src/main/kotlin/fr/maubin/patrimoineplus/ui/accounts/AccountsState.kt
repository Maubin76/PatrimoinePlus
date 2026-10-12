package fr.maubin.patrimoineplus.ui.accounts

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.accountValue
import fr.maubin.patrimoineplus.domain.calc.wholePercents
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate

/*
	Compte affiché dans la liste.
	account    : compte
	valueCents : valeur entière aujourd'hui
	operations : nombre d'opérations, hors solde initial
	weight     : part du compte dans le patrimoine (part détenue appliquée) ; null s'il n'y compte pas (valeur nulle ou négative)
*/
data class AccountItem(
	val account    : Account,
	val valueCents : Long,
	val operations : Int,
	val weight     : AccountWeight? = null
)

/*
	Part d'un compte dans le patrimoine.
	percent : part entière en pour cent (les parts de tous les comptes font 100)
	rank    : rang de valeur comptée (0 : le plus gros), qui fixe sa teinte
	counted : valeur comptée dans le patrimoine, en centimes (largeur dans la barre)
*/
data class AccountWeight(
	val percent : Int,
	val rank    : Int,
	val counted : Long
)

/*
	État de l'écran des comptes.
	loaded   : vrai une fois la base lue
	active   : comptes actifs, dans l'ordre d'affichage
	archived : comptes archivés
*/
data class AccountsUiState(
	val loaded   : Boolean           = false,
	val active   : List<AccountItem> = emptyList(),
	val archived : List<AccountItem> = emptyList()
)

/*
	Saisie du formulaire de compte, telle que tapée.
	name        : nom
	type        : type
	institution : établissement
	share       : part détenue, en % (texte)
	opening     : solde initial (texte) ; vide = aucun
	openingDate : date du solde initial
*/
data class AccountForm(
	val name        : String      = "",
	val type        : AccountType = AccountType.CHECKING,
	val institution : String      = "",
	val share       : String      = "100",
	val opening     : String      = "",
	val openingDate : LocalDate
)

/*
	Erreurs du formulaire de compte, par champ.
	name    : erreur sur le nom
	share   : erreur sur la part détenue
	opening : erreur sur le solde initial
	general : erreur globale
*/
data class AccountFormErrors(
	val name    : String? = null,
	val share   : String? = null,
	val opening : String? = null,
	val general : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = name == null && share == null && opening == null && general == null
}


/*
	Part de chaque compte dans le patrimoine : seules les valeurs comptées
	positives entrent dans le total ; rangées de la plus grosse à la plus
	petite, parts entières qui font 100.
	inCounted : valeur comptée (part détenue appliquée) par compte
*/
fun accountWeights(inCounted: Map<String, Long>): Map<String, AccountWeight>
{
	val vPositive = inCounted.entries.filter { it.value > 0 }.sortedByDescending { it.value }	// comptes qui comptent
	val vPercents = wholePercents(vPositive.map { it.value })						// parts entières
	return vPositive.mapIndexed { inIndex, inEntry -> inEntry.key to AccountWeight(vPercents[inIndex], inIndex, inEntry.value) }.toMap()
}

/*
	État de la liste des comptes à partir des données : valeur du jour,
	nombre d'opérations (hors solde initial), part dans le patrimoine ; actifs
	et archivés séparés. Partagé par les applis.
	inAccounts     : comptes
	inTransactions : opérations
	inPrices       : cours relevés
	inToday        : date de valorisation
*/
fun buildAccountsState(
	inAccounts     : List<Account>,
	inTransactions : List<fr.maubin.patrimoineplus.domain.model.Transaction>,
	inPrices       : List<fr.maubin.patrimoineplus.domain.model.AssetPrice>,
	inToday        : LocalDate
): AccountsUiState
{
	val vLedger  = Ledger(inTransactions)										// grand livre
	val vValues  = inAccounts.associateWith { accountValue(it, vLedger, inToday, inPrices) }	// valeur de chaque compte
	val vWeights = accountWeights(vValues.values.associate { it.account.id to it.countedCents })	// part de chaque compte
	val vItems   = inAccounts.map { inAccount ->
		AccountItem(
			account    = inAccount,
			valueCents = vValues.getValue(inAccount).valueCents,
			operations = inTransactions.count { inTx -> inTx.kind != TransactionKind.OPENING && inTx.postings.any { it.accountId == inAccount.id } },
			weight     = vWeights[inAccount.id]
		)
	}																			// comptes valorisés
	return AccountsUiState(true, vItems.filterNot { it.account.archived }, vItems.filter { it.account.archived })
}
