package fr.maubin.patrimoineplus.domain.model

/*
	Type d'un compte ; fixe la manière dont son solde et sa valeur se calculent.
	label : libellé au pluriel, pour titrer un groupe de comptes
*/
enum class AccountType(val label: String)
{
	CHECKING("Comptes courants"),
	SAVINGS("Livrets"),
	BROKERAGE("Comptes-titres")
}
