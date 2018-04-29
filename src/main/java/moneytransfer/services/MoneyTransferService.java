package moneytransfer.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import moneytransfer.database.*;
import moneytransfer.exceptions.InvalidAccountException;
import rx.Single;

import java.math.BigDecimal;

@Singleton
public class MoneyTransferService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountRepository accountRepository;

    @Inject
    public MoneyTransferService(AccountRepository accountRepository, AccountBalanceRepository accountBalanceRepository) {
        this.accountBalanceRepository = accountBalanceRepository;
        this.accountRepository = accountRepository;
    }

    public Single<Void> transferMoney(String sourceAccount, String destinationAccount, BigDecimal amount, String currency) {
        return accountRepository.doesAccountExist(sourceAccount).flatMap((sourceAccountExists) -> {
            if (!sourceAccountExists) {
                return Single.error(new InvalidAccountException());
            }

            return accountRepository.doesAccountExist(destinationAccount).flatMap((destinationAccountExists) -> {
                if (!destinationAccountExists) {
                    return Single.error(new InvalidAccountException());
                }

                return accountBalanceRepository.transferMoney(sourceAccount, destinationAccount, amount, currency);
            });
        });
    }
}
