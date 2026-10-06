package fr.maubin.patrimoineplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.maubin.patrimoineplus.ui.PatrimoineApp
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme

/*
	Activité unique : héberge toute l'interface Compose.
*/
@AndroidEntryPoint
class MainActivity : ComponentActivity()
{
	/*
		Affiche l'interface en plein écran (bord à bord) avec le thème de l'application.
	*/
	override fun onCreate(inSavedInstanceState: Bundle?)
	{
		super.onCreate(inSavedInstanceState)
		enableEdgeToEdge()
		setContent {
			PatrimoineTheme {
				PatrimoineApp()
			}
		}
	}
}
