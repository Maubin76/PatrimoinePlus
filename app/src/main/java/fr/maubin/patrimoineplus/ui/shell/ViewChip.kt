package fr.maubin.patrimoineplus.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.model.Profile
import fr.maubin.patrimoineplus.ui.components.FloatingSurface
import fr.maubin.patrimoineplus.ui.icons.LineIcons

// Sélecteur de vue de la barre du haut, fourni par l'appli ; rien par défaut (aperçus, captures).
val LocalViewChip = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/*
	Sélecteur de vue du téléphone : pilule avec l'initiale du profil affiché
	(icône de la famille pour la vue Famille) ; un toucher ouvre la liste
	des profils, puis la famille. Rien à partir d'un seul profil.
	inProfiles : profils
	inProfile  : profil affiché ; null pour la famille
	inOnView   : change de vue
*/
@Composable
fun ViewChip(
	inProfiles : List<Profile>,
	inProfile  : String?,
	inOnView   : (String?) -> Unit
)
{
	if (inProfiles.size < 2) return
	var vOpen by remember { mutableStateOf(false) }								// liste ouverte
	val vName = inProfiles.firstOrNull { it.id == inProfile }?.name ?: "Famille"	// vue affichée
	Box {
		FloatingSurface(RoundedCornerShape(50)) {
			Row(
				modifier          = Modifier
					.clip(RoundedCornerShape(50))
					.clickable(onClickLabel = "Changer de vue") { vOpen = true }
					.semantics { contentDescription = "Vue : $vName" }
					.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				ProfileBadge(vName, inProfile == null)
				Spacer(Modifier.width(8.dp))
				Text(vName, style = MaterialTheme.typography.labelLarge)
			}
		}
		DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inProfiles.forEach { inEntry ->
				DropdownMenuItem(text = { Text(inEntry.name) }, leadingIcon = { ProfileBadge(inEntry.name, false) }, onClick = { vOpen = false; inOnView(inEntry.id) })
			}
			HorizontalDivider()
			DropdownMenuItem(text = { Text("Famille : tous les comptes") }, leadingIcon = { ProfileBadge("Famille", true) }, onClick = { vOpen = false; inOnView(null) })
		}
	}
}

/*
	Pastille ronde à l'encre de la coque : initiale d'un profil, ou icône de
	la famille.
	inName   : nom
	inFamily : vue de la famille
*/
@Composable
fun ProfileBadge(
	inName   : String,
	inFamily : Boolean
)
{
	Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
		if (inFamily) Icon(LineIcons.Family, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
		else Text(inName.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
	}
}
