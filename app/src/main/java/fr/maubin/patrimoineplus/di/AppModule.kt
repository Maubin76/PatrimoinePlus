package fr.maubin.patrimoineplus.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/*
	Dépendances transverses de l'application.
*/
@Module
@InstallIn(SingletonComponent::class)
object AppModule
{
	/*
		Horloge système : injectée pour que la logique ne lise jamais la date
		courante directement, ce qui la rend testable.
	*/
	@Provides
	@Singleton
	fun provideClock(): Clock = Clock.systemDefaultZone()
}
