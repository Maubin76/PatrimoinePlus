package fr.maubin.patrimoineplus.ui.report

import fr.maubin.patrimoineplus.data.report.Align
import fr.maubin.patrimoineplus.data.report.PdfDocument
import fr.maubin.patrimoineplus.data.report.PdfPage
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.domain.calc.shareOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kLongDay  = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// date en toutes lettres
private val kMonth    = DateTimeFormatter.ofPattern("MMMM", Locale.FRANCE)			// nom du mois
private const val kLeft   = 56f														// marge gauche
private const val kRight  = 539f														// marge droite
private const val kBottom = 780f														// bas de la zone utile
private const val kLine   = 17f														// hauteur d'une ligne de tableau

/*
	Mise en page d'un bilan annuel sur des pages A4 : en-tête, sections avec
	titre, tableaux à colonnes alignées à droite, saut de page automatique et
	pied de page numéroté.
	inReport : bilan
	inToday  : date d'établissement
*/
private class ReportLayout(
	private val inReport : AnnualReport,
	private val inToday  : LocalDate
)
{
	val fDoc  = PdfDocument()														// document
	var fPage : PdfPage = fDoc.newPage()											// page courante
	var fY    = 64f																	// ordonnée courante

	/*
		Réserve de la place ; passe à la page suivante si elle manque.
		inNeeded : hauteur nécessaire
	*/
	fun room(inNeeded: Float)
	{
		if (fY + inNeeded > kBottom)
		{
			fPage = fDoc.newPage()
			fY    = 64f
			fPage.text(kLeft, fY, "Bilan ${inReport.year} (suite)", inSize = 9f, inGrey = 0.45f)
			fY += 22f
		}
	}

	/*
		Titre de section, suivi d'un filet.
		inTitle : titre
	*/
	fun section(inTitle: String)
	{
		room(60f)
		fY += 16f
		fPage.text(kLeft, fY, inTitle, inSize = 13f, inBold = true)
		fY += 7f
		fPage.line(kLeft, kRight, fY, inGrey = 0.2f, inWidth = 0.8f)
		fY += 15f
	}

	/*
		Ligne de tableau : libellé à gauche, valeurs alignées à droite sur des
		colonnes données.
		inLabel   : libellé
		inValues  : valeurs, de gauche à droite
		inColumns : bord droit de chaque colonne de valeur
		inBold    : ligne en gras (total)
		inGrey    : couleur du texte
	*/
	fun row(
		inLabel   : String,
		inValues  : List<String>,
		inColumns : List<Float>,
		inBold    : Boolean = false,
		inGrey    : Float   = 0f
	)
	{
		room(kLine)
		fPage.text(kLeft, fY, inLabel, inBold = inBold, inGrey = inGrey)
		inValues.forEachIndexed { inIndex, inValue -> fPage.text(inColumns[inIndex], fY, inValue, inBold = inBold, inGrey = inGrey, inAlign = Align.RIGHT) }
		fY += kLine
	}

	/*
		En-tête de colonnes, en gris.
		inLabel   : première colonne
		inHeads   : autres colonnes
		inColumns : bord droit de chaque colonne
	*/
	fun head(
		inLabel   : String,
		inHeads   : List<String>,
		inColumns : List<Float>
	)
	{
		row(inLabel, inHeads, inColumns, inGrey = 0.45f)
		fPage.line(kLeft, kRight, fY - kLine + 5f)
	}

	/*
		Paragraphe court en gris.
		inText : texte
	*/
	fun note(inText: String)
	{
		room(kLine)
		fPage.text(kLeft, fY, inText, inSize = 9f, inGrey = 0.45f)
		fY += kLine
	}

	/*
		Pied de chaque page : date d'établissement et numéro de page.
	*/
	fun footers()
	{
		val vDay = inToday.format(kLongDay)											// date d'établissement
		fDoc.pages.forEachIndexed { inIndex, inPage ->
			inPage.line(kLeft, kRight, 806f)
			inPage.text(kLeft, 820f, "Établi le $vDay à partir des opérations saisies dans PatrimoinePlus. Montants en euros.", inSize = 8f, inGrey = 0.45f)
			inPage.text(kRight, 820f, "${inIndex + 1} / ${fDoc.pageCount}", inSize = 8f, inGrey = 0.45f, inAlign = Align.RIGHT)
		}
	}
}

/*
	Bilan annuel au format PDF (A4), prêt à imprimer : patrimoine au début et à
	la fin, comptes, revenus et dépenses par catégorie, placements (intérêts,
	dividendes, plus-values réalisées par titre), patrimoine fin de mois.
	inReport : bilan calculé
	inToday  : date d'établissement
*/
fun annualReportPdf(
	inReport : AnnualReport,
	inToday  : LocalDate
): ByteArray
{
	val vLayout = ReportLayout(inReport, inToday)									// mise en page
	val vEuros  = { inCents: Long -> formatEuros(inCents) }						// montant
	val vSigned = { inCents: Long -> formatEuros(inCents, true) }				// montant signé
	with(vLayout) {
		fPage.text(kLeft, fY, "Bilan ${inReport.year}", inSize = 24f, inBold = true)
		fY += 20f
		fPage.text(kLeft, fY, "PatrimoinePlus · du 1er janvier au ${inReport.to.format(kLongDay)}" + if (inReport.partial) " (année en cours)" else "", inSize = 10f, inGrey = 0.45f)
		fY += 14f

		section("Patrimoine")
		val vOne = listOf(kRight)													// une colonne de valeur
		row("Au ${inReport.from.minusDays(1).format(kLongDay)}", listOf(vEuros(inReport.wealthStart)), vOne)
		row("Au ${inReport.to.format(kLongDay)}", listOf(vEuros(inReport.wealthEnd)), vOne)
		row("Variation", listOf(vSigned(inReport.wealthChange)), vOne, inBold = true)
		note("Comptes joints comptés pour la part détenue ; compte Bourse valorisé au dernier cours connu.")

		section("Comptes")
		val vAccCols = listOf(330f, 435f, kRight)									// début, fin, variation
		head("Compte", listOf("Début", "Fin", "Variation"), vAccCols)
		inReport.accounts.forEach { inAcc ->
			row(
				"${inAcc.account.name} (${inAcc.account.type.label}${if (inAcc.share != 100) ", ${inAcc.share} %" else ""}${if (inAcc.account.archived) ", archivé" else ""})",
				listOf(vEuros(inAcc.start), vEuros(inAcc.end), vSigned(inAcc.end - inAcc.start)),
				vAccCols
			)
		}
		if (inReport.accounts.any { it.share != 100 }) note("Valeurs entières des comptes ; le patrimoine n’en compte que la part détenue (${inReport.accounts.filter { it.share != 100 }.joinToString { "${it.account.name} : ${vEuros(shareOf(it.end, it.share))}" }}).")

		section("Revenus et dépenses")
		row("Revenus", listOf(vEuros(inReport.totalIncomes)), vOne)
		row("Dépenses", listOf(vEuros(inReport.totalExpenses)), vOne)
		row("Épargne (revenus − dépenses)", listOf(vSigned(inReport.totalIncomes - inReport.totalExpenses)), vOne, inBold = true)
		inReport.savingsRate?.let { row("Taux d’épargne", listOf(formatPermille(it).removePrefix("+")), vOne) }
		val vCatCols = listOf(435f, kRight)											// montant, part
		if (inReport.incomes.isNotEmpty())
		{
			fY += 8f
			head("Revenus par catégorie", listOf("Montant", "Part"), vCatCols)
			inReport.incomes.forEach { row(it.category.name, listOf(vEuros(it.cents), percent(it.cents, inReport.totalIncomes)), vCatCols) }
		}
		if (inReport.expenses.isNotEmpty())
		{
			fY += 8f
			head("Dépenses par catégorie", listOf("Montant", "Part"), vCatCols)
			inReport.expenses.forEach { row(it.category.name, listOf(vEuros(it.cents), percent(it.cents, inReport.totalExpenses)), vCatCols) }
		}
		note("Opérations ordinaires de tous les comptes ; virements entre comptes et ordres de bourse exclus ; remboursements déduits.")

		section("Placements")
		row("Intérêts et revenus des comptes d’épargne", listOf(vEuros(inReport.interest)), vOne)
		row("Dividendes et revenus des comptes Bourse", listOf(vEuros(inReport.dividends)), vOne)
		row("Plus-values réalisées", listOf(vSigned(inReport.totalRealized)), vOne, inBold = true)
		inReport.realized.forEach { row("    ${it.assetName}", listOf(vSigned(it.cents)), vOne, inGrey = 0.3f) }
		if (inReport.realized.isEmpty()) note("Aucune vente de titre sur la période.")

		section("Patrimoine en fin de mois")
		val vMax = inReport.months.maxOfOrNull { it.valueCents }?.coerceAtLeast(1L) ?: 1L	// plus grande valeur
		inReport.months.forEach { inPoint ->
			room(kLine)
			fPage.text(kLeft, fY, inPoint.month.format(kMonth).replaceFirstChar { it.uppercase() })
			val vBar = 230f * inPoint.valueCents.coerceAtLeast(0L) / vMax				// longueur de la barre
			fPage.rect(140f, fY - 8f, vBar, 9f, 0.75f)
			fPage.text(kRight, fY, vEuros(inPoint.valueCents), inAlign = Align.RIGHT)
			fY += kLine
		}
	}
	vLayout.footers()
	return vLayout.fDoc.toBytes("Bilan ${inReport.year} - PatrimoinePlus")
}

/*
	Part d'un montant dans un total, en pour cent entiers (« 12 % »).
	inCents : montant
	inTotal : total
*/
private fun percent(
	inCents : Long,
	inTotal : Long
): String = if (inTotal <= 0) "" else "${Math.round(inCents * 100.0 / inTotal)} %"
