package fr.maubin.patrimoineplus.ui.home

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.Wealth
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.monthChange
import fr.maubin.patrimoineplus.domain.calc.wholePercents
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.calc.wealthSeries
import fr.maubin.patrimoineplus.domain.model.AccountType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.domain.calc.netValues
import fr.maubin.patrimoineplus.domain.calc.financedAccounts
import fr.maubin.patrimoineplus.domain.model.Loan

private const val kHomeSeriesMonths = 12									// mois de la courbe de l'accueil
private val kDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// format de la date de calcul

/*
	Ligne d'un compte dans l'accueil.
	name         : nom du compte
	detail       : type, établissement, part détenue si elle n'est pas de 100 %
	valueCents   : valeur entière nette du compte (crédit : somme due, positive)
	countedCents : valeur comptée dans le patrimoine net (part détenue appliquée)
*/
data class HomeAccountLine(
	val name         : String,
	val detail       : String,
	val valueCents   : Long,
	val countedCents : Long = valueCents
)

/*
	Groupe de comptes d'un même type.
	type    : type des comptes du groupe
	lines   : comptes du groupe, dans l'ordre d'affichage
	percent : part du groupe dans le patrimoine, en % entiers (les parts des
	          groupes font 100 au total)
*/
data class HomeGroup(
	val type    : AccountType,
	val lines   : List<HomeAccountLine>,
	val percent : Int = 0
)
{
	// Sous-total compté dans le patrimoine net, en centimes (crédits : négatif ou nul).
	val subtotalCents: Long get() = lines.sumOf { it.countedCents }

	// Vrai pour le groupe des crédits : sommes dues, affichées en positif.
	val owed: Boolean get() = type == AccountType.LOAN
}

/*
	État de l'écran d'accueil.
	asOf        : date à laquelle le patrimoine est calculé, formatée
	groups      : groupes de comptes non vides, dans l'ordre des types
	series      : patrimoine en fin de mois, douze derniers mois au plus
	monthChange : variation depuis la fin du mois précédent ; null le premier mois
	grossCents  : patrimoine brut : valeurs comptées hors crédits
	debtCents   : crédits : capital restant dû compté, en positif (somme due)
*/
data class HomeUiState(
	val asOf        : String,
	val groups      : List<HomeGroup>,
	val series      : List<SeriesPoint> = emptyList(),
	val monthChange : Long?             = null,
	val grossCents  : Long              = 0L,
	val debtCents   : Long              = 0L
)
{
	// Patrimoine total (net), en centimes : somme des valeurs comptées.
	val totalCents: Long get() = groups.sumOf { it.subtotalCents }

	// Groupes d'avoirs (hors crédits), nets des crédits liés aux biens.
	val assetGroups: List<HomeGroup> get() = groups.filterNot { it.owed }

	// Crédits restant à déduire du total (sans bien financé), en positif.
	val unlinkedDebtCents: Long get() = -groups.filter { it.owed }.sumOf { it.subtotalCents }

	// Vrai tant qu'aucun compte n'existe.
	val isEmpty: Boolean get() = groups.isEmpty()
}


/*
	Met en forme le patrimoine calculé, en net : groupes par type (un compte
	archivé n'apparaît que s'il a encore une valeur), comptes de biens moins
	les crédits liés à leurs biens, crédits en positif ; parts des avoirs
	(crédits exclus) ; évolution sur douze mois au plus et variation du mois.
	inWealth : patrimoine calculé
	inSeries : patrimoine mois par mois depuis la première opération
	inLinks  : compte de biens financé par chaque compte Crédit
*/
fun toHomeUiState(
	inWealth : Wealth,
	inSeries : List<SeriesPoint>    = emptyList(),
	inLinks  : Map<String, String>  = emptyMap()
): HomeUiState
{
	val vNets     = netValues(inWealth.accounts, inLinks)						// valeurs nettes
	val vGroups   = AccountType.entries.mapNotNull { inType ->
		val vLines = inWealth.accounts
			.filter { it.account.type == inType && (!it.account.archived || it.valueCents != 0L) }
			.map { inValue ->
				val vNet = vNets.getValue(inValue.account.id)						// valeur nette
				HomeAccountLine(inValue.account.name, accountDetail(inValue.account), vNet.valueCents, vNet.countedCents)
			}																		// lignes du type
		if (vLines.isEmpty()) null else HomeGroup(inType, vLines)
	}																				// groupes non vides
	val vPercents = wholePercents(vGroups.map { if (it.owed) 0L else it.subtotalCents.coerceAtLeast(0L) })	// parts entières des avoirs
	return HomeUiState(
		series      = inSeries.takeLast(kHomeSeriesMonths),
		monthChange = monthChange(inSeries),
		asOf        = inWealth.date.format(kDateFormat),
		groups      = vGroups.mapIndexed { inIndex, inGroup -> inGroup.copy(percent = vPercents[inIndex]) },
		grossCents  = inWealth.grossCents,
		debtCents   = -inWealth.debtCents
	)
}

/*
	Ligne de détail d'un compte : type, établissement, part détenue, archivage.
	inAccount : compte
*/
fun accountDetail(inAccount: fr.maubin.patrimoineplus.domain.model.Account): String =
	listOfNotNull(
		inAccount.type.label,
		inAccount.institution,
		if (inAccount.sharePercent != 100) "${inAccount.sharePercent}\u202F%" else null,
		if (inAccount.archived) "archivé" else null
	).joinToString(" · ")

/*
	État complet de l'accueil à partir des données : patrimoine du jour,
	évolution mois par mois et variation du mois, en net des crédits.
	Partagé par les applis.
	inAccounts     : comptes
	inTransactions : opérations
	inPrices       : cours relevés
	inToday        : date de calcul
	inLoans        : crédits (biens financés)
*/
fun buildHomeState(
	inAccounts     : List<fr.maubin.patrimoineplus.domain.model.Account>,
	inTransactions : List<fr.maubin.patrimoineplus.domain.model.Transaction>,
	inPrices       : List<fr.maubin.patrimoineplus.domain.model.AssetPrice>,
	inToday        : LocalDate,
	inLoans        : List<Loan> = emptyList()
): HomeUiState
{
	val vLedger = Ledger(inTransactions)										// grand livre
	val vSeries = wealthSeries(inAccounts, vLedger, inPrices, inToday)		// patrimoine mois par mois
	return toHomeUiState(wealthAt(inAccounts, vLedger, inToday, inPrices), vSeries, financedAccounts(inLoans, inTransactions, inAccounts, inToday))
}

/*
	Date de calcul affichée (« 9 octobre 2026 »).
	inDate : date
*/
fun homeDate(inDate: LocalDate): String = inDate.format(kDateFormat)
