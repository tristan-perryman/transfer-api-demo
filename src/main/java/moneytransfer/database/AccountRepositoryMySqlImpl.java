package moneytransfer.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.json.JsonArray;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import rx.Single;

@Singleton
public class AccountRepositoryMySqlImpl implements AccountRepository {

    private final JDBCClient jdbcClient;

    @Inject
    public AccountRepositoryMySqlImpl(JDBCClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Single<Void> createTable() {
        String createStatement = "CREATE TABLE account ( account_id varchar(255), PRIMARY KEY (account_id) )";
        return jdbcClient.rxGetConnection().flatMap(sqlConnection ->
            sqlConnection.rxUpdate(createStatement)
                .doAfterTerminate(sqlConnection::close))
            .map(updateResult -> null);
    }

    @Override
    public Single<Boolean> doesAccountExist(String accountId) {
        String query = "select 1 from account where account_id = ?";
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(accountId);
        return jdbcClient.rxGetConnection().flatMap(sqlConnection ->
            sqlConnection.rxQueryWithParams(query, jsonArray)
                .doAfterTerminate(sqlConnection::close))
            .map(resultSet -> resultSet.getNumRows() > 0);
    }
}
