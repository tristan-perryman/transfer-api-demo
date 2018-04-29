package moneytransfer.database;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.jdbc.JDBCClient;

public class MySqlJdbcClientFactory {
    public static JDBCClient createMySqlJdbcClient(Vertx vertx) {
        JsonObject connectionConfig = new JsonObject();
        connectionConfig.put("url", "jdbc:mysql://localhost:3306/test?serverTimezone=UTC");
        connectionConfig.put("user", "root");
        connectionConfig.put("driver_class", "com.mysql.cj.jdbc.Driver");
        return JDBCClient.createShared(vertx,connectionConfig);
    }
}
