package info.blockchain.balance

sealed class AccountReference(
    val cryptoCurrency: CryptoCurrency,
    val label: String
) {
    data class BitcoinLike(
        private val _cryptoCurrency: CryptoCurrency,
        private val _label: String,
        val xpub: String
    ) : AccountReference(_cryptoCurrency, _label)

    data class Ethereum(
        private val _label: String,
        val address: String
    ) : AccountReference(CryptoCurrency.ETHER, _label)

    data class Xlm(
        private val _label: String,
        val accountId: String
    ) : AccountReference(CryptoCurrency.XLM, _label)

    data class Pax(
        private val _label: String,
        val ethAddress: String,
        val apiCode: String
    ) : AccountReference(CryptoCurrency.PAX, _label)

    data class Stx(
        private val _label: String,
        val address: String
    ) : AccountReference(CryptoCurrency.STX, _label)
}

enum class AccountType {
    Spendable,
    ColdStorage,
    WatchOnly
}

typealias AccountReferenceList = List<AccountReference>

data class Account(val reference: AccountReference, val type: AccountType)

fun AccountReference.toAccount(type: AccountType): Account = Account(this, type)