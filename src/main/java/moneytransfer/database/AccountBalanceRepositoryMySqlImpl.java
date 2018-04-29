package moneytransfer.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation;
import io.vertx.core.json.JsonArray;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.sql.SQLConnection;
import moneytransfer.exceptions.InsufficientAccountBalanceException;
import moneytransfer.exceptions.MoneyOverflowException;
import moneytransfer.exceptions.MoneyTooManyDecimalPlacesException;
import rx.Single;

import java.math.BigDecimal;

@Singleton
public class AccountBalanceRepositoryMySqlImpl implements AccountBalanceRepository {

    private final JDBCClient jdbcClient;

    private static int MONEY_SCALE = 10;
    private static int MONEY_PRECISION = 65;

    private static final String MONEY_DATATYPE = "DECIMAL(" + MONEY_PRECISION + "," + MONEY_SCALE + ")";

    @Inject
    public AccountBalanceRepositoryMySqlImpl(JDBCClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Single<Void> createTable() {
        String createStatement = "CREATE TABLE account_balance ( account_id varchar(255), " +
                                                                "currency varchar(255), " +
                                                                "balance " + MONEY_DATATYPE + ", " +
                                                                "PRIMARY KEY (account_id, currency), " +
                                                                "FOREIGN KEY (account_id) REFERENCES account(account_id))";
        return jdbcClient.rxGetConnection().flatMap(sqlConnection ->
            sqlConnection.rxUpdate(createStatement)
                .doAfterTerminate(sqlConnection::close)).map(updateResult -> null);
    }

    @Override
    public Single<Void> transferMoney(String sourceAccount, String destinationAccount, BigDecimal amount, String currency) {

        if (amount.scale() > MONEY_SCALE) {
            return Single.error(new MoneyTooManyDecimalPlacesException());
        }

        return jdbcClient.rxGetConnection().flatMap(sqlConnection ->
            sqlConnection.rxSetAutoCommit(false)
                .flatMap((__) ->
                    subtractAmountFromSourceAccountBalance(sqlConnection, sourceAccount, currency, amount))
                .flatMap((__) ->
                    addAmountToDestinationAccountBalance(sqlConnection, destinationAccount, currency, amount))
                .flatMap((__) -> sqlConnection.rxCommit())
                .onErrorResumeNext((throwable) -> sqlConnection.rxRollback()
                    .flatMap((__) -> {
                        if (throwable instanceof MysqlDataTruncation) {
                            return Single.<Void>error(new MoneyOverflowException(throwable));
                        }
                        return Single.error(throwable);
                    }))
                .doAfterTerminate(sqlConnection::close));
    }

    private Single<Void> addAmountToDestinationAccountBalance(SQLConnection sqlConnection, String destinationAccount, String currency, BigDecimal amount) {
        String upsertBalanceStatement = "INSERT INTO account_balance ( account_id, currency, balance ) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE balance=balance+CAST(? AS " + MONEY_DATATYPE + ")";
        JsonArray params = new JsonArray();
        params.add(destinationAccount);
        params.add(currency);
        params.add(amount.toString());
        params.add(amount.toString());
        return sqlConnection.rxUpdateWithParams(upsertBalanceStatement, params)
            .map((updateResult) -> null);
    }

    private Single<Void> subtractAmountFromSourceAccountBalance(SQLConnection sqlConnection, String sourceAccount, String currency, BigDecimal amount) {
        String subtractBalanceStatement = "UPDATE account_balance set balance=balance-CAST(? AS " + MONEY_DATATYPE + ") WHERE balance-CAST(? AS " + MONEY_DATATYPE + ") >= 0 AND account_id = ? AND currency = ?";
        JsonArray params = new JsonArray();
        params.add(amount.toString());
        params.add(amount.toString());
        params.add(sourceAccount);
        params.add(currency);
        return sqlConnection.rxUpdateWithParams(subtractBalanceStatement, params)
            .flatMap((updateResult) -> updateResult.getUpdated() == 1 ? Single.just(null) : Single.error(new InsufficientAccountBalanceException()));
    }
}
