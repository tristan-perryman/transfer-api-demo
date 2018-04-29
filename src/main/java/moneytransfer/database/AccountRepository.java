package moneytransfer.database;

import rx.Single;

public interface AccountRepository {
    Single<Void> createTable();

    Single<Boolean> doesAccountExist(String accountId);
}
