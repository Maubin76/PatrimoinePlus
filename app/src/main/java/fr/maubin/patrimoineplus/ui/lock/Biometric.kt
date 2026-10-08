package fr.maubin.patrimoineplus.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val kAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL		// empreinte, ou code du téléphone en repli

/*
	Vrai si le téléphone peut authentifier (empreinte ou code configuré).
	inActivity : activité courante
*/
fun canAuthenticate(inActivity: FragmentActivity): Boolean =
	BiometricManager.from(inActivity).canAuthenticate(kAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS

/*
	Demande une authentification système (empreinte, ou code du téléphone).
	inActivity  : activité courante
	inTitle     : titre de la demande
	inOnSuccess : authentification réussie
	inOnFailure : abandon ou erreur, avec le message du système
*/
fun authenticate(
	inActivity  : FragmentActivity,
	inTitle     : String,
	inOnSuccess : () -> Unit,
	inOnFailure : (String) -> Unit = {}
)
{
	val vPrompt = BiometricPrompt(
		inActivity,
		ContextCompat.getMainExecutor(inActivity),
		object : BiometricPrompt.AuthenticationCallback()
		{
			/*
				Authentification réussie.
				inResult : résultat
			*/
			override fun onAuthenticationSucceeded(inResult: BiometricPrompt.AuthenticationResult)
			{
				inOnSuccess()
			}

			/*
				Abandon ou erreur définitive.
				inCode    : code d'erreur
				inMessage : message du système
			*/
			override fun onAuthenticationError(inCode: Int, inMessage: CharSequence)
			{
				inOnFailure(inMessage.toString())
			}
		}
	)																			// demande système
	vPrompt.authenticate(
		BiometricPrompt.PromptInfo.Builder()
			.setTitle(inTitle)
			.setSubtitle("Empreinte ou code du téléphone")
			.setAllowedAuthenticators(kAuthenticators)
			.build()
	)
}
