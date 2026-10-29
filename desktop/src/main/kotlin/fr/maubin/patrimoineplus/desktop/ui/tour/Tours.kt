package fr.maubin.patrimoineplus.desktop.ui.tour

import fr.maubin.patrimoineplus.desktop.Screen

const val kTourAccount = "ACCOUNT"														// visite de la vue d'un compte

/*
	Visites guidées de l'appli PC, page par page : au lancement, le tableau
	de bord (avec la coque : rail, saisie, mode discret) ; à la première
	visite de chaque autre page, la sienne.
*/
object Tours
{
	/*
		Nom d'une visite, pour les réglages.
		inKey : visite (nom d'un écran, ou kTourAccount)
	*/
	fun label(inKey: String): String =
		Screen.entries.firstOrNull { it.name == inKey }?.label ?: "Vue d'un compte"

	// Visites proposées dans les réglages, dans l'ordre du rail puis la vue d'un compte.
	val keys: List<String> = Screen.entries.map { it.name } + kTourAccount

	/*
		Étapes d'une visite.
		inKey : visite (nom d'un écran, ou kTourAccount)
	*/
	fun steps(inKey: String): List<TourStep> =
		when (inKey)
		{
			Screen.DEVICES.name    -> listOf(
				TourStep("devices.this", "Ce PC sur le réseau", "Son nom, tel que le téléphone le verra, et le réseau auquel il est relié : la synchronisation ne fonctionnera que sur le réseau de la maison."),
				TourStep("devices.sync", "Synchronisation avec le téléphone", "Appairez le téléphone une fois avec le QR code : il se synchronise ensuite tout seul, mais seulement sur le Wi-Fi de la maison."),
				TourStep("devices.history", "Historique", "Chaque envoi de modifications y laisse une ligne : quel appareil, quand, et combien d'éléments ajoutés, modifiés ou supprimés."),
				TourStep("devices.diagnostic", "Diagnostic réseau", "Avant la synchronisation, vérifiez que le téléphone trouve ce PC : lancez le diagnostic ici, puis celui du téléphone dans ses Réglages.")
			)
			Screen.DASHBOARD.name  -> listOf(
				TourStep("dashboard.hero", "Votre patrimoine net", "Tous vos comptes et biens, crédits déduits, calculés à partir de vos opérations. La variation du mois est juste dessous."),
				TourStep("dashboard.charts", "Évolution et répartition", "La courbe suit votre patrimoine mois après mois ; la répartition montre son poids par type de compte. L'icône en haut à droite bascule entre barre et cercle."),
				TourStep("dashboard.accounts", "Vos comptes", "Une tuile par compte, avec sa valeur, sa courbe et son aperçu. Un clic ouvre le compte ; « Réorganiser » change leur ordre."),
				TourStep("shell.newop", "Saisir une opération", "Dépense, revenu, virement, ordre de bourse ou mensualité : le panneau de saisie s'ouvre à droite. Raccourci : Ctrl+N."),
				TourStep("shell.privacy", "Recherche et mode discret", "La loupe cherche dans toutes vos opérations (Ctrl+F) ; l'œil masque les montants quand quelqu'un regarde votre écran."),
				TourStep("shell.rail", "Les autres pages", "Objectifs, recherche, opérations, budget, projection, bilan et réglages. Chacune a sa visite guidée à votre première venue.")
			)
			Screen.GOALS.name      -> listOf(
				TourStep("goals.global", "Objectifs globaux", "Des cibles à moyen terme suivies sur vos comptes : matelas de sécurité, livret plein, apport pour un achat… avec le rythme pour les atteindre."),
				TourStep("goals.new", "Nouvel objectif", "Choisissez le type (patrimoine, compte, épargne mensuelle, tâche…), la cible et l'échéance."),
				TourStep("goals.month", "Objectifs du mois", "Fixés depuis le budget : une dépense maximale par thème et l'épargne prévue, suivies automatiquement ; les mois passés restent consultables.")
			)
			Screen.SEARCH.name     -> listOf(
				TourStep("search.filters", "Chercher", "Libellé, notes, catégorie, compte ou montant, avec des filtres par compte, catégorie, dates et montants."),
				TourStep("search.results", "Résultats", "Un clic ouvre une opération ; les cases, Ctrl+clic ou Maj+clic en sélectionnent plusieurs pour changer leur catégorie ou les supprimer.")
			)
			Screen.OPERATIONS.name -> listOf(
				TourStep("ops.batch", "Saisie rapide", "Plusieurs dépenses et revenus d'affilée, au clavier : Tab passe à la case suivante, Entrée ajoute une ligne, Ctrl+Entrée enregistre tout."),
				TourStep("ops.recurring", "Récurrences", "Salaire, loyer, abonnements, mensualités : chaque échéance crée l'opération toute seule, à sa date.")
			)
			Screen.BUDGET.name     -> listOf(
				TourStep("budget.figures", "Votre mois type", "Entrées, dépenses, investissements et ce qui reste, tels que vous les prévoyez."),
				TourStep("budget.goals", "Objectifs du mois", "Transformez le budget en objectifs pour ce mois ou le suivant ; leur suivi se fait dans la page Objectifs."),
				TourStep("budget.cashflow", "Cashflow", "Le schéma montre où va l'argent : des entrées vers les investissements, chaque thème de dépense et le reste."),
				TourStep("budget.lines", "Vos lignes", "Ajoutez une ligne avec « + » ; les dépenses sont rangées par thème, avec leur total.")
			)
			Screen.PROJECTION.name -> listOf(
				TourStep("projection.inputs", "Vos hypothèses", "Durée, rendement par type de placement, inflation, épargne mensuelle : modifiez-les, la projection suit."),
				TourStep("projection.result", "Votre patrimoine demain", "L'estimation centrale et sa fourchette, année par année, crédits remboursés selon leur tableau.")
			)
			Screen.REPORT.name     -> listOf(
				TourStep("report.tabs", "Deux bilans", "Mois par mois : dépenses et revenus par catégorie sur douze mois. Bilan annuel : l'année entière, avec son export PDF."),
				TourStep("report.content", "Le détail", "Dans le tableau mois par mois, un clic sur un montant ouvre ses opérations dans la recherche.")
			)
			Screen.SETTINGS.name   -> listOf(
				TourStep("settings.data", "Vos données", "Le dossier où ce PC range vos données, ou la démonstration ; rien ne sort de la maison, le téléphone se synchronise par le Wi-Fi familial."),
				TourStep("settings.appearance", "Apparence", "Clair ou sombre, thème de couleurs, effet verre."),
				TourStep("settings.profiles", "Profils", "Chaque membre de la famille suit ses comptes ; la vue Famille les rassemble tous."),
				TourStep("settings.tours", "Tutoriels", "Revoyez la visite d'une page, ou réactivez-les toutes.")
			)
			kTourAccount           -> listOf(
				TourStep("account.header", "Le compte", "Son solde au jour et ses chiffres du mois ; « Modifier le compte » change son nom, son type ou ses propriétaires."),
				TourStep("account.month", "Mois par mois", "Les flèches changent de mois : chiffres, dépenses par catégorie et opérations suivent."),
				TourStep("account.operations", "Opérations", "Un clic modifie une opération ; les relevés de la banque permettent de vérifier le solde calculé.")
			)
			else                   -> emptyList()
		}
}
