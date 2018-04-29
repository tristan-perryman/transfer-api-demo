package moneytransfer.database;

import rx.Single;

import java.math.BigDecimal;

public interface AccountBalanceRepository {

    Single<Void> createTable();

    Single<Void> transferMoney(String sourceAccount, String destinationAccount, BigDecimal amount, String currency);
}
