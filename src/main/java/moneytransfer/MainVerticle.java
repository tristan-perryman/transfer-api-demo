package moneytransfer;

import ch.vorburger.exec.ManagedProcessException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import io.vertx.core.Future;
import io.vertx.rxjava.core.AbstractVerticle;
import moneytransfer.database.AccountBalanceRepository;
import moneytransfer.database.AccountRepository;

public class MainVerticle extends AbstractVerticle {

    @Inject
    AccountRepository accountRepository;

    @Inject
    AccountBalanceRepository accountBalanceRepository;

    @Inject
    MainRouter mainRouter;

    @Override
    public void start(Future<Void> future) {
        Guice.createInjector(new MainModule(vertx)).injectMembers(this);
        accountRepository.createTable()
            .flatMap((__) -> accountBalanceRepository.createTable())
            .subscribe((__) -> {
                vertx
                    .createHttpServer()
                    .requestHandler(mainRouter.router()::accept)
                    .listen(1234, "localhost");
                future.complete();
            });
    }
}