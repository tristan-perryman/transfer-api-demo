package moneytransfer

import io.vertx.rxjava.core.Vertx
import io.vertx.rxjava.ext.jdbc.JDBCClient
import moneytransfer.database.Database
import moneytransfer.database.MySqlJdbcClientFactory
import moneytransfer.database.TestDBHelper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static io.restassured.RestAssured.given
import static moneytransfer.MoneyConstants.MAX_MONEY_VALUE
import static moneytransfer.MoneyConstants.MIN_NONZERO_MONEY_VALUE
import static org.hamcrest.Matchers.is

class MoneyTransferAPITest extends Specification {

    @Shared
    private Vertx vertx;

    @Shared
    private TestDBHelper testDBHelper

    def setupSpec() {
        Database.start()
        vertx = Vertx.vertx();
        vertx.rxDeployVerticle(MainVerticle.class.getName()).toBlocking().value();
        JDBCClient jdbcClient = MySqlJdbcClientFactory.createMySqlJdbcClient(vertx)
        testDBHelper = new TestDBHelper(jdbcClient)
    }

    def cleanupSpec() {
        testDBHelper.dropTables().toBlocking().value()
        vertx.rxClose().toBlocking().value()
    }

    def cleanup() {
        testDBHelper.clearTables().toBlocking().value()

    }

    @Unroll
    def "successfully transfers #transferAmount from account with balance #sourceAccountBalance to account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()
        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "${transferAmount}",
                        "currency": "${currency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(200)
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(expectedFinalSourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(expectedFinalDestinationAccountBalance)
        where:
        sourceAccountBalance                | destinationAccountBalance           | transferAmount | expectedFinalSourceAccountBalance | expectedFinalDestinationAccountBalance
        "10.24"                             | "0"                                 | "10.24"        | "0"                               | "10.24"
        "10.25"                             | "0"                                 | "10.24"        | "0.01"                            | "10.24"
        "1262621612621643843232378.3434"    | "0"                                 | "0.0001"       | "1262621612621643843232378.3433"  | "0.0001"
        "1262621612621643843232378.3434"    | "0.0001"                            | "0.0001"       | "1262621612621643843232378.3433"                | "0.0002"
        "10.24"                             | "5.30"                              | "10.24"        | "0"                                             | "15.54"
        "10.25"                             | "5.30"                              | "10.24"        | "0.01"                                          | "15.54"
    }

    def "should only update balance for requested currency when transferring money from account with multiple currencies to account with multiple currencies" () {
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

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "3",
                        "currency": "${transferCurrency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(200)
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

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "3",
                        "currency": "${transferCurrency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(200)
        testDBHelper.getAccountBalance(sourceAccount, transferCurrency).toBlocking().value() == new BigDecimal("1")
        testDBHelper.getAccountBalance(destinationAccount, transferCurrency).toBlocking().value() == new BigDecimal("5")
        testDBHelper.getAccountBalance(otherAccount, transferCurrency).toBlocking().value() == new BigDecimal("8")
    }


    @Unroll
    def "should return INSUFFICIENT_ACCOUNT_BALANCE error and not update balance when transferring #transferAmount from account with insufficient balance of #sourceAccountBalance to another account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "${transferAmount}",
                        "currency": "${currency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(422).body("errorCode", is("INSUFFICIENT_ACCOUNT_BALANCE"))
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
    def "should return MONEY_TOO_MANY_DECIMAL_PLACES error and not update balance when transferring #transferAmount of money that has too many decimal places from account with balance of #sourceAccountBalance to another account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "${transferAmount}",
                        "currency": "${currency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("MONEY_TOO_MANY_DECIMAL_PLACES"))
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(sourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(destinationAccountBalance)
        where:
        sourceAccountBalance   | destinationAccountBalance  | transferAmount
        "1"                    | "0"                        | "0.00000000001"
        "1"                    | "0"                        | "0.10000000001"
    }

    @Unroll
    def "should return MONEY_OVERFLOW error and not update balance when transferring #transferAmount of money which results in an overflow from account with balance of #sourceAccountBalance to another account with balance of #destinationAccountBalance" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal(sourceAccountBalance)).toBlocking().value()
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal(destinationAccountBalance)).toBlocking().value()

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "${transferAmount}",
                        "currency": "${currency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(422)
                .body("errorCode", is("MONEY_OVERFLOW"))
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal(sourceAccountBalance)
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal(destinationAccountBalance)
        where:
        sourceAccountBalance    | destinationAccountBalance  | transferAmount
        MAX_MONEY_VALUE         | MIN_NONZERO_MONEY_VALUE    | MAX_MONEY_VALUE
        MIN_NONZERO_MONEY_VALUE | MAX_MONEY_VALUE            | MIN_NONZERO_MONEY_VALUE
    }

    def "should return INVALID_ACCOUNT error and should not affect source balance when transferring account balance to non-existing account" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(sourceAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(sourceAccount, currency, new BigDecimal("5")).toBlocking().value()

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "2",
                        "currency": "${currency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("INVALID_ACCOUNT"))
        testDBHelper.getAccountBalance(sourceAccount, currency).toBlocking().value() == new BigDecimal("5")
    }

    def "should return INVALID_ACCOUNT error and should not affect destination balance when transferring account balance from non-existing account" () {
        given:
        def sourceAccount = "12345678"
        def destinationAccount = "87654321"
        def currency = "GBP"
        testDBHelper.insertAccount(destinationAccount).toBlocking().value()
        testDBHelper.insertAccountBalance(destinationAccount, currency, new BigDecimal("5")).toBlocking().value()

        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "${sourceAccount}",
                        "destinationAccount": "${destinationAccount}",
                        "amount": "2",
                        "currency": "${currency}"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("INVALID_ACCOUNT"))
        testDBHelper.getAccountBalance(destinationAccount, currency).toBlocking().value() == new BigDecimal("5")
    }

    def "should return 400 Bad Request error if json is malformed" () {
        given:
        def request = given().contentType("application/json")
                .body("""
                    {
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("BAD_REQUEST"))
    }

    def "should return 400 Bad Request error if amount field is not a valid number" () {
        given:
        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "12345678",
                        "destinationAccount": "87654321",
                        "amount": "notanumber",
                        "currency": "GBP"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("BAD_REQUEST"))
    }

    def "should return 400 Bad Request error if sourceAccount field is missing" () {
        given:
        def request = given().contentType("application/json")
                .body("""
                    { 
                        "destinationAccount": "87654321",
                        "amount": "2",
                        "currency": "GBP"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("BAD_REQUEST"))
    }

    def "should return 400 Bad Request error if destinationAccount field is missing" () {
        given:
        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "12345678",
                        "amount": "2",
                        "currency": "GBP"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("BAD_REQUEST"))
    }

    def "should return 400 Bad Request error if amount field is missing" () {
        given:
        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "12345678",
                        "destinationAccount": "87654321",
                        "currency": "GBP"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("BAD_REQUEST"))
    }

    def "should return 400 Bad Request error if currency field is missing" () {
        given:
        def request = given().contentType("application/json")
                .body("""
                    { 
                        "sourceAccount": "12345678",
                        "destinationAccount": "87654321",
                        "amount": "2"
                     }
                    """)

        when:
        def response = request.when()
                .post("http://localhost:1234/transfer-money")

        then:
        response.then()
                .statusCode(400)
                .body("errorCode", is("BAD_REQUEST"))
    }
}
