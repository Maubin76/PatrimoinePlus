package fr.maubin.patrimoineplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Profile

// Profils de la famille, fournis par l'appli (titulaire des comptes dans les listes).
val LocalProfiles = staticCompositionLocalOf<List<Profile>> { emptyList() }

/*
	Nom d'un compte avec son titulaire, pour les listes où l'on choisit un
	compte (« Compte courant · Alex ») : deux comptes de même nom se
	distinguent dès que la famille a plusieurs profils.
	inAccount : compte
*/
@Composable
fun accountLabel(inAccount: Account): String = fr.maubin.patrimoineplus.domain.calc.accountLabel(inAccount, LocalProfiles.current)
