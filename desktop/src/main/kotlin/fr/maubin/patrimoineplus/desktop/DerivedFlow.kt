package fr.maubin.patrimoineplus.desktop

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/*
	État calculé à partir d'autres états, sans portée de coroutines : sa
	valeur est toujours celle du calcul sur les valeurs courantes, et ceux
	qui l'observent sont prévenus quand le résultat change.
	inSources : états lus par le calcul
	inCompute : calcul (lit la valeur courante des sources)
*/
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class DerivedFlow<T>(
	private val inSources : List<StateFlow<*>>,
	private val inCompute : () -> T
) : StateFlow<T>
{
	// Résultat du calcul sur les valeurs courantes.
	override val value: T get() = inCompute()

	// Dernière valeur, seule rejouée.
	override val replayCache: List<T> get() = listOf(value)

	/*
		Transmet la valeur courante puis chaque changement du résultat.
		inCollector : observateur
	*/
	override suspend fun collect(inCollector: FlowCollector<T>): Nothing
	{
		combine(inSources) { inCompute() }.distinctUntilChanged().collect(inCollector)
		awaitCancellation()
	}
}
