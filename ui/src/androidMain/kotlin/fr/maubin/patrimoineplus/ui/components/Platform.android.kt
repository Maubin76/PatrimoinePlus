package fr.maubin.patrimoineplus.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
	Animations du système : réglage « échelle de durée des animations ».
*/
@Composable
actual fun animationsEnabled(): Boolean
{
	val vContext = LocalContext.current										// accès aux réglages système
	return remember(vContext) {
		Settings.Global.getFloat(vContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
	}
}
