package moneytransfer.services

import moneytransfer.database.AccountBalanceRepository
import moneytransfer.database.AccountRepository
import moneytransfer.exceptions.InvalidAccountException
import rx.Single
import rx.observers.TestSubscriber
import spock.lang.Specification

class MoneyTransferServiceTest extends Specification {

    MoneyTransferService moneyTransferService

    AccountRepository accountRepository

    AccountBalanceRepository accountBalanceRepository

    String sourceAccount = "12345678"
    String destinationAccount = "87654321"
    BigDecimal amount = new BigDecimal("5")
    String currency = "GBP"

    def setup() {
        accountRepository = Mock(AccountRepository)
        accountBalanceRepository = Mock(AccountBalanceRepository)
        moneyTransferService = new MoneyTransferService(accountRepository, accountBalanceRepository)
    }

    def "transferMoney throws InvalidAccountException if sourceAccount does not exist" () {
        given:
        accountRepository.doesAccountExist(sourceAccount) >> Single.just(false)
        accountRepository.doesAccountExist(destinationAccount) >> Single.just(true)

        TestSubscriber testSubscriber = new TestSubscriber()

        when:
        moneyTransferService.transferMoney(sourceAccount, destinationAccount, amount, currency).subscribe(testSubscriber)

        then:
        testSubscriber.awaitTerminalEvent()
        testSubscriber.getOnErrorEvents().get(0) instanceof InvalidAccountException
    }

    def "transferMoney throws InvalidAccountException if destinationAccount does not exist" () {
        given:
        accountRepository.doesAccountExist(sourceAccount) >> Single.just(true)
        accountRepository.doesAccountExist(destinationAccount) >> Single.just(false)

        TestSubscriber testSubscriber = new TestSubscriber()

        when:
        moneyTransferService.transferMoney(sourceAccount, destinationAccount, amount, currency).subscribe(testSubscriber)

        then:
        testSubscriber.awaitTerminalEvent()
        testSubscriber.getOnErrorEvents().get(0) instanceof InvalidAccountException
    }

    def "transferMoney executes accountBalanceRepository.transferMoney if both accounts exist" () {
        given:
        boolean transferMoneyExecuted = false
        accountRepository.doesAccountExist(sourceAccount) >> Single.just(true)
        accountRepository.doesAccountExist(destinationAccount) >> Single.just(true)
        accountBalanceRepository.transferMoney(sourceAccount, destinationAccount, amount, currency) >> Single.fromCallable({
            transferMoneyExecuted = true
            return null
        })

        when:
        moneyTransferService.transferMoney(sourceAccount, destinationAccount, amount, currency).toBlocking().value()

        then:
        transferMoneyExecuted
    }
}
