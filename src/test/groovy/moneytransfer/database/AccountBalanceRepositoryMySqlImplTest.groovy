package moneytransfer.database

import io.vertx.rxjava.core.Vertx
import io.vertx.rxjava.ext.jdbc.JDBCClient
import moneytransfer.exceptions.InsufficientAccountBalanceException
import moneytransfer.exceptions.MoneyOverflowException
import moneytransfer.exceptions.MoneyTooManyDecimalPlacesException
import rx.observers.TestSubscriber
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static moneytransfer.MoneyConstants.*

class AccountBalanceRepositoryMySqlImplTest extends Specification {

    @Shared
    private Vertx vertx

    @Shared
    private AccountBalanceRepositoryMySqlImpl accountBalanceRepositoryMySql

    @Shared
    private AccountRepositoryMySqlImpl accountRepositoryMySql;

    @Shared
    private TestDBHelper testDBHelper

    def setupSpec() {
        Database.start()
        vertx = Vertx.vertx()
        JDBCClient jdbcClient = MySqlJdbcClientFactory.createMySqlJdbcClient(vertx)
        accountBalanceRepositoryMySql = new AccountBalanceRepositoryMySqlImpl(jdbcClient)
        accountRepositoryMySql = new AccountRepositoryMySqlImpl(jdbcClient)
        testDBHelper = new TestDBHelper(jdbcClient)

        accountRepositoryMySql.createTable().toBlocking().value()
        accountBalanceRepositoryMySql.createTable().toBlocking().value()
    }

    def cleanupSpec() {
        testDBHelper.dropTables().toBlocking().value()
        vertx.rxClose().toBlocking().value()
    }

    def cleanup() {
        testDBHelper.clearTables().toBlocking().value()
    }

    @Unroll
    def "creates row and sets correct balance when transferring #transferAmount from account with balance #sourceAccountBalance to account with no corresponding account_balance row" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()

        when:
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal(transferAmount), currency).toBlocking().value()

        then:
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(expectedFinalSourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(expectedFinalDestinationAccountBalance)
        where:
        sourceAccountBalance              | transferAmount | expectedFinalSourceAccountBalance   | expectedFinalDestinationAccountBalance
        "10.24"                           | "10.24"        | "0"                                 | "10.24"
        "10.25"                           | "10.24"        | "0.01"                              | "10.24"
        "1262621612621643843232378.3434"  | "0.0001"       | "1262621612621643843232378.3433"    | "0.0001"
    }

    @Unroll
    def "sets correct balance when transferring #transferAmount from account with balance #sourceAccountBalance to account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        when:
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal(transferAmount), currency).toBlocking().value()

        then:
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(expectedFinalSourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(expectedFinalDestinationAccountBalance)
        where:
        sourceAccountBalance                    | destinationAccountBalance   | transferAmount                               | expectedFinalSourceAccountBalance      | expectedFinalDestinationAccountBalance
        "10.24"                                 | "0"                         | "10.24"                                      | "0"                                    | "10.24"
        "10.25"                                 | "0"                         | "10.24"                                      | "0.01"                                 | "10.24"
        "1262621612621643843232378.3430000004"  | "0"                         | "0.0000000001"                               | "1262621612621643843232378.3430000003" | "0.0000000001"
        "1262621612621643843232378.3430000004"  | "0.0000000001"              | "0.0000000001"                               | "1262621612621643843232378.3430000003" | "0.0000000002"
        "10.24"                                 | "5.30"                      | "10.24"                                      | "0"                                    | "15.54"
        "10.25"                                 | "5.30"                      | "10.24"                                      | "0.01"                                 | "15.54"
        "101"                                   | "0"                         | "100.5000000004"                             | "0.4999999996"                         | "100.5000000004"
        MAX_MONEY_VALUE                         | "0"                         | MAX_MONEY_VALUE_MINUS_MIN_NONZERO_VALUE      | MIN_NONZERO_MONEY_VALUE                | MAX_MONEY_VALUE_MINUS_MIN_NONZERO_VALUE
        MAX_MONEY_VALUE_MINUS_MIN_NONZERO_VALUE | MIN_NONZERO_MONEY_VALUE     | MAX_MONEY_VALUE_MINUS_MIN_NONZERO_VALUE      | "0"                                    | MAX_MONEY_VALUE
    }

    def "should only update balance for requested currency when transferring from account with multiple currencies to account with multiple currencies" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def transferCurrency = "GBP"
        def otherCurrency = "USD"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, transferCurrency, new BigDecimal("4")).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, otherCurrency, new BigDecimal("6")).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, transferCurrency, new BigDecimal("2")).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, otherCurrency, new BigDecimal("8")).toBlocking().value()

        when:
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal("3"), transferCurrency).toBlocking().value()

        then:
        testDBHelper.getAccountBalance(sourceAccount, transferCurrency).toBlocking().value() == new BigDecimal("1")
        testDBHelper.getAccountBalance(destinationAccount, transferCurrency).toBlocking().value() == new BigDecimal("5")
        testDBHelper.getAccountBalance(sourceAccount, otherCurrency).toBlocking().value() == new BigDecimal("6")
        testDBHelper.getAccountBalance(destinationAccount, otherCurrency).toBlocking().value() == new BigDecimal("8")
    }

    def "should not update balance on other accounts when transferring money between two accounts" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def otherAccount = "22554411"
        def transferCurrency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, transferCurrency, new BigDecimal("4")).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, transferCurrency, new BigDecimal("2")).toBlocking().value()

        testDBHelper.insertAccount(otherAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(otherAccount, transferCurrency, new BigDecimal("8")).toBlocking().value()

        when:
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal("3"), transferCurrency).toBlocking().value()

        then:
        testDBHelper.getAccountBalance(sourceAccount, transferCurrency).toBlocking().value() == new BigDecimal("1")
        testDBHelper.getAccountBalance(destinationAccount, transferCurrency).toBlocking().value() == new BigDecimal("5")
        testDBHelper.getAccountBalance(otherAccount, transferCurrency).toBlocking().value() == new BigDecimal("8")
    }

    @Unroll
    def "should throw InsufficientAccountBalanceException and not update balance when transferring #transferAmount from account with insufficient balance of #sourceAccountBalance to another account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        when:
        TestSubscriber testSubscriber = new TestSubscriber()
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal(transferAmount), currency).subscribe(testSubscriber)
        testSubscriber.awaitTerminalEvent()

        then:
        testSubscriber.getOnErrorEvents().get(0) instanceof InsufficientAccountBalanceException
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(sourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(destinationAccountBalance)
        where:
        sourceAccountBalance                | destinationAccountBalance           | transferAmount
        "0"                                 | "0"                                 | "1.45"
        "0"                                 | "1.45"                              | "1.45"
        "1.1"                               | "0"                                 | "1.45"
        "1262621612621643843232378.3434"    | "0"                                 | "1262621612621643843232378.3435"
    }

    @Unroll
    def "should throw MoneyTooManyDecimalPlacesException and not update balance when transferring #transferAmount of money that has too many decimal places from account with balance of #sourceAccountBalance to another account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        when:
        TestSubscriber testSubscriber = new TestSubscriber()
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal(transferAmount), currency).subscribe(testSubscriber)
        testSubscriber.awaitTerminalEvent()

        then:
        testSubscriber.getOnErrorEvents().get(0) instanceof MoneyTooManyDecimalPlacesException
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(sourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(destinationAccountBalance)
        where:
        sourceAccountBalance  | destinationAccountBalance  | transferAmount
        "1"                   | "0"                        | "0.00000000001"
        "1"                   | "0"                        | "0.10000000001"
    }

    @Unroll
    def "should throw MoneyOverflowException and not update balance when transferring #transferAmount of money which results in an overflow from account with balance of #sourceAccountBalance to another account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        when:
        TestSubscriber testSubscriber = new TestSubscriber()
        accountBalanceRepositoryMySql.transferMoney(sourceAccount, destinationAccount, new BigDecimal(transferAmount), currency).subscribe(testSubscriber)
        testSubscriber.awaitTerminalEvent()

        then:
        testSubscriber.getOnErrorEvents().get(0) instanceof MoneyOverflowException
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(sourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(destinationAccountBalance)
        where:
        sourceAccountBalance    | destinationAccountBalance  | transferAmount
        MAX_MONEY_VALUE         | MIN_NONZERO_MONEY_VALUE    | MAX_MONEY_VALUE
        MIN_NONZERO_MONEY_VALUE | MAX_MONEY_VALUE            | MIN_NONZERO_MONEY_VALUE
    }

}
