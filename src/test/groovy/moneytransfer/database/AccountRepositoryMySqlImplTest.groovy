package moneytransfer.database

import io.vertx.rxjava.core.Vertx
import io.vertx.rxjava.ext.jdbc.JDBCClient
import spock.lang.Shared
import spock.lang.Specification

class AccountRepositoryMySqlImplTest extends Specification {

    @Shared
    private Vertx vertx

    @Shared
    private JDBCClient jdbcClient

    @Shared
    private AccountRepositoryMySqlImpl accountRepositoryMySql

    @Shared
    private TestDBHelper testDBHelper

    def setupSpec() {
        Database.start()
        vertx = Vertx.vertx()
        jdbcClient = MySqlJdbcClientFactory.createMySqlJdbcClient(vertx)
        accountRepositoryMySql = new AccountRepositoryMySqlImpl(jdbcClient)
        testDBHelper = new TestDBHelper(jdbcClient)

        accountRepositoryMySql.createTable().toBlocking().value()
    }

    def cleanupSpec() {
        testDBHelper.dropTables().toBlocking().value()
        vertx.rxClose().toBlocking().value()
    }

    def cleanup() {
        testDBHelper.clearTables().toBlocking().value()
    }

    def "Account does not exist"() {
        when:
        def doesAccountExist = accountRepositoryMySql.doesAccountExist("12345678").toBlocking().value()

        then:
        !doesAccountExist
    }

    def "Account does exist"() {
        given:
        String accountId = "12345678"
        testDBHelper.insertAccount(accountId).toBlocking().value()

        when:
        def doesAccountExist = accountRepositoryMySql.doesAccountExist(accountId).toBlocking().value()

        then:
        doesAccountExist
    }
}
