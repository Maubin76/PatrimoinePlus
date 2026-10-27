package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Captures de la vitrine (README) : démonstration en thème Coloré, effet
	verre, mode sombre, palette par défaut ; tableau de bord (avec
	l'assistant) et budget (schéma de cashflow). Copiées dans `doc/images/` à la main
	quand l'interface change : `vitrine-pc-*.png`.
*/
class ShowcaseCapturesTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Rend l'appli dans un PNG, une fois le lancement et les transitions finis.
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(inApp: DesktopController, inName: String)
	{
		val vScene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		var vTime  = 0L																	// instant courant (ms)
		while (vTime < 2_000L) { vScene.render(vTime * 1_000_000L); vTime += 16 }
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(vTime * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Tableau de bord et budget de la vitrine.
	*/
	@Test
	fun showcase()
	{
		val vApp  = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply {
			openDemo(); setAppearance(ThemeMode.DARK); setGlass(true); setColorTheme(ColorTheme.COLORFUL); setAssistant(true); stopAllTours()
		}																				// démonstration, apparence de la vitrine
		capture(vApp, "vitrine-pc-tableau-de-bord")
		val vData = vApp.workspace.value.data											// données
		val vCat  = { inName: String -> vData.categories.single { it.name == inName }.id }	// catégorie par nom
		vApp.setBudget(Budget(listOf(
			BudgetLine("1", BudgetSection.INCOME, "Salaire", 320_000, categoryId = vCat("Salaire")),
			BudgetLine("2", BudgetSection.EXPENSE, "Loyer", 95_000, theme = ExpenseTheme.HOUSING),
			BudgetLine("3", BudgetSection.EXPENSE, "Courses", 45_000, theme = ExpenseTheme.DAILY),
			BudgetLine("4", BudgetSection.EXPENSE, "Transport", 12_000, theme = ExpenseTheme.TRANSPORT),
			BudgetLine("5", BudgetSection.EXPENSE, "Sorties", 15_000, theme = ExpenseTheme.PLEASURE),
			BudgetLine("9", BudgetSection.EXPENSE, "Abonnements", 6_000, theme = ExpenseTheme.SUBSCRIPTIONS),
			BudgetLine("8", BudgetSection.EXPENSE, "Crédit auto", 20_000, theme = ExpenseTheme.CREDIT),
			BudgetLine("6", BudgetSection.INVESTMENT, "Livret A", 50_000, accountId = vData.accounts.first { it.name == "Livret A" }.id),
			BudgetLine("7", BudgetSection.INVESTMENT, "PEA", 40_000, accountId = vData.accounts.first { it.name == "PEA" }.id)
		)))
		vApp.setAssistant(false)														// budget : l'assistant cacherait des étiquettes du schéma
		vApp.show(Screen.BUDGET)
		capture(vApp, "vitrine-pc-budget")
	}
}
