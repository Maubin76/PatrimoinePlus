package fr.maubin.patrimoineplus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/*
	Point d'entrée de l'application : initialise le graphe d'injection Hilt.
*/
@HiltAndroidApp
class PatrimoineApplication : Application()
