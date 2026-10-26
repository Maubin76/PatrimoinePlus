package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.ThemeEntity
import fr.maubin.patrimoineplus.domain.calc.hexOf
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import fr.maubin.patrimoineplus.domain.calc.themeError
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/*
	Thèmes de couleurs créés par l'utilisateur, sur le téléphone : lecture,
	création ou modification (mêmes règles que sur le PC), suppression. Ils
	voyagent avec le fichier d'échange.
	inDb : base locale
*/
@Singleton
class ThemeRepository @Inject constructor(
	private val inDb : PatrimoineDatabase
)
{
	private val fDao = inDb.backupDao()											// thèmes en bloc

	/*
		Thèmes, suivis.
	*/
	fun observeThemes(): Flow<List<CustomTheme>> =
		fDao.observeThemes().map { inList -> inList.map { CustomTheme(it.id, it.name, it.colors.split(";")) } }

	/*
		Crée ou modifie un thème : nom libre et non vide, cinq couleurs
		lisibles (enregistrées « #RRGGBB »).
		inId     : thème modifié ; null pour en créer un
		inName   : nom
		inColors : couleurs tapées, dans l'ordre des rôles
		Retour : identifiant du thème, ou refus
	*/
	suspend fun save(
		inId     : String?,
		inName   : String,
		inColors : List<String>
	): Result<String> =
		inDb.withTransaction {
			val vThemes = fDao.themes()														// thèmes existants
			themeError(inName, inColors, vThemes.filter { it.id != inId }.map { it.name })?.let { return@withTransaction Result.failure(IllegalArgumentException(it)) }
			val vColors = inColors.joinToString(";") { hexOf(parseHexColor(it)!!) }		// couleurs normalisées
			val vOld    = vThemes.firstOrNull { it.id == inId }								// thème modifié
			val vTheme  = ThemeEntity(vOld?.id ?: UUID.randomUUID().toString(), inName.trim(), vColors, vOld?.sortOrder ?: ((vThemes.maxOfOrNull { it.sortOrder } ?: -1) + 1))	// thème enregistré
			fDao.insertThemes(listOf(vTheme))
			Result.success(vTheme.id)
		}

	/*
		Supprime un thème.
		inId : thème
	*/
	suspend fun delete(inId: String) = fDao.deleteTheme(inId)
}
