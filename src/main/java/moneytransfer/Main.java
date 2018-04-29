package moneytransfer;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import io.vertx.rxjava.core.Vertx;

public class Main {

    public static void main(String[] args) throws ManagedProcessException {
        DB db = DB.newEmbeddedDB(3306);
        db.start();
        Vertx.vertx().deployVerticle(MainVerticle.class.getName());
    }
}
