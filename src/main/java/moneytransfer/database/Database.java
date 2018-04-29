package moneytransfer.database;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;

public class Database {

    private static DB db;

    public static void start() throws ManagedProcessException {
        if (db == null) {
            db = DB.newEmbeddedDB(3306);
            db.start();
        }
    }
}
