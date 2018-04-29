package moneytransfer;

import com.google.inject.AbstractModule;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import moneytransfer.database.*;

public class MainModule extends AbstractModule {

    private final Vertx vertx;

    MainModule(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    protected void configure() {
        bind(Vertx.class).toInstance(vertx);

        JDBCClient jdbcClient = MySqlJdbcClientFactory.createMySqlJdbcClient(vertx);
        bind(JDBCClient.class).toInstance(jdbcClient);

        bind(AccountRepository.class).to(AccountRepositoryMySqlImpl.class);
        bind(AccountBalanceRepository.class).to(AccountBalanceRepositoryMySqlImpl.class);
    }
}
