package moneytransfer.database;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import rx.Single;

import java.math.BigDecimal;

public class TestDBHelper {

    private final JDBCClient jdbcClient;

    public TestDBHelper(JDBCClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Single<Void> dropTables() {
        return executeUpdate("DROP TABLE account_balance")
            .onErrorResumeNext((__) -> Single.just(null))
            .flatMap((__) ->
                executeUpdate("DROP TABLE account"));
    }

    public Single<Void> clearTables() {
        return executeUpdate("DELETE FROM account_balance")
            .onErrorResumeNext((__) -> Single.just(null))
            .flatMap((__) ->
                executeUpdate("DELETE FROM account"));
    }

    public Single<Void> insertAccount(String accountId) {
        String insertAccountStatement = "INSERT INTO account ( account_id ) VALUES ( ? )";
        return executeUpdate(insertAccountStatement, accountId);
    }

    public Single<Void> insertAccountBalance(String accountId, String currency, BigDecimal balance) {
        String insertAccountBalanceStatement = "INSERT INTO account_balance ( account_id, currency, balance ) VALUES ( ?, ?, CAST(? AS DECIMAL(65,10)) )";
        return executeUpdate(insertAccountBalanceStatement, accountId, currency, balance.toString());
    }

    public Single<BigDecimal> getAccountBalance(String accountId, String currency) {
        String getAccountBalanceQuery = "SELECT CAST(balance AS char(255)) from account_balance where account_id = ? AND currency = ?";
        return executeQuery(getAccountBalanceQuery, accountId, currency)
            .map((resultSet) -> resultSet.getResults().get(0).getString(0))
            .map(BigDecimal::new);
    }

    private Single<Void> executeUpdate(String statement, Object... params) {
        JsonArray jsonParams = new JsonArray();
        for (Object param : params) {
            jsonParams.add(param);
        }
        return jdbcClient.rxGetConnection().flatMap(sqlConnection ->
            sqlConnection.rxUpdateWithParams(statement, jsonParams)
                .doAfterTerminate(sqlConnection::close)).map(updateResult -> null);
    }

    private Single<ResultSet> executeQuery(String statement, Object... params) {
        JsonArray jsonParams = new JsonArray();
        for (Object param : params) {
            jsonParams.add(param);
        }
        return jdbcClient.rxGetConnection().flatMap(sqlConnection ->
            sqlConnection.rxQueryWithParams(statement, jsonParams)
                .doAfterTerminate(sqlConnection::close));
    }
}
