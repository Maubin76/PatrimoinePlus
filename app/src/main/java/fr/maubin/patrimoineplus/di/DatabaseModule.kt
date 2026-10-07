package fr.maubin.patrimoineplus.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import javax.inject.Singleton

/*
	Fournit la base locale Room, unique source de données de l'application.
*/
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule
{
	/*
		Base locale, fichier « patrimoine.db ». Aucune migration destructrice :
		une évolution de schéma sans migration fait échouer l'ouverture plutôt
		que d'effacer les données.
		inContext : contexte de l'application
	*/
	@Provides
	@Singleton
	fun provideDatabase(@ApplicationContext inContext: Context): PatrimoineDatabase =
		Room.databaseBuilder(inContext, PatrimoineDatabase::class.java, PatrimoineDatabase.kFileName).build()
}
