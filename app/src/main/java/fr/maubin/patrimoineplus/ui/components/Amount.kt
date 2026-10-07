package fr.maubin.patrimoineplus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import fr.maubin.patrimoineplus.domain.format.formatEuros

// Vrai quand le mode discret masque les montants ; fourni par la coque de l'application.
val LocalAmountsHidden = staticCompositionLocalOf { false }

private const val kRollMillis = 320										// durée du roulement d'un chiffre
private const val kMaskMillis = 180										// durée du fondu masqué / visible

/*
	Montant en euros, masqué en mode discret.
	inCents        : montant signé, en centimes
	inModifier     : modificateur appliqué au texte
	inStyle        : style typographique
	inColor        : couleur du texte ; par défaut celle du contenu
	inShowPlusSign : préfixe « + » devant les montants positifs
	inRolling      : fait rouler les chiffres qui changent, comme un afficheur
*/
@Composable
fun AmountText(
	inCents        : Long,
	inModifier     : Modifier  = Modifier,
	inStyle        : TextStyle = LocalTextStyle.current,
	inColor        : Color     = Color.Unspecified,
	inShowPlusSign : Boolean   = false,
	inRolling      : Boolean   = false
)
{
	val vHidden = LocalAmountsHidden.current								// mode discret actif
	val vColor  = if (inColor == Color.Unspecified) LocalContentColor.current else inColor	// couleur effective
	val vRoll   = inRolling && animationsEnabled()							// roulement permis par le système
	Crossfade(
		targetState   = vHidden,
		animationSpec = tween(kMaskMillis),
		modifier      = inModifier,
		label         = "masque"
	) { inHidden ->
		when
		{
			inHidden  -> MaskedAmount(inStyle)
			vRoll     -> RollingText(formatEuros(inCents, inShowPlusSign), inStyle, vColor)
			else      -> Text(
				text     = formatEuros(inCents, inShowPlusSign),
				style    = inStyle,
				color    = vColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

/*
	Texte dont chaque caractère qui change roule verticalement, comme les
	chiffres d'un afficheur. Les positions sont repérées depuis la droite,
	pour que les centimes restent en place quand le montant s'allonge.
	inText  : texte à afficher
	inStyle : style typographique
	inColor : couleur du texte
*/
@Composable
private fun RollingText(
	inText  : String,
	inStyle : TextStyle,
	inColor : Color
)
{
	Row(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = inText }) {
		inText.forEachIndexed { inIndex, inChar ->
			key(inText.length - inIndex) {
				AnimatedContent(
					targetState    = inChar,
					transitionSpec = {
						val vUp = targetState > initialState						// le chiffre augmente
						(slideInVertically(tween(kRollMillis)) { if (vUp) it else -it } + fadeIn(tween(kRollMillis)))
							.togetherWith(slideOutVertically(tween(kRollMillis)) { if (vUp) -it else it } + fadeOut(tween(kRollMillis)))
					},
					label          = "chiffre"
				) { inShown ->
					Text(text = inShown.toString(), style = inStyle, color = inColor)
				}
			}
		}
	}
}

/*
	Indique si les animations du système sont actives. Quand elles sont
	coupées (Accessibilité > Supprimer les animations), les chiffres changent
	d'un coup au lieu de rouler.
*/
@Composable
private fun animationsEnabled(): Boolean
{
	val vContext = LocalContext.current										// accès aux réglages système
	return remember(vContext) {
		Settings.Global.getFloat(vContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
	}
}

/*
	Montant masqué : cinq points ronds dessinés (la police rend la puce en
	carré), suivis du symbole €, en gris secondaire. Ne révèle ni le signe ni
	l'ordre de grandeur.
	inStyle : style typographique du montant remplacé
*/
@Composable
private fun MaskedAmount(inStyle: TextStyle)
{
	val vMuted    = MaterialTheme.colorScheme.onSurfaceVariant				// gris secondaire
	val vFontSize = if (inStyle.fontSize.isSpecified) inStyle.fontSize else 16.sp	// taille de référence
	val vDot      = with(LocalDensity.current) { (vFontSize * 0.2f).toDp() }	// diamètre d'un point
	Row(
		verticalAlignment     = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(vDot * 0.6f),
		modifier              = Modifier.semantics(mergeDescendants = true) { contentDescription = "Montant masqué" }
	) {
		repeat(5) {
			Box(Modifier.size(vDot).background(vMuted, CircleShape))
		}
		Text(text = "\u00A0€", style = inStyle, color = vMuted, maxLines = 1)
	}
}
