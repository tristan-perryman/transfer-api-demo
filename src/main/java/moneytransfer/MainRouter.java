package moneytransfer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import moneytransfer.handlers.MoneyTransferHandler;

import static io.vertx.core.http.HttpMethod.POST;

@Singleton
public class MainRouter {

    @Inject
    Vertx vertx;

    @Inject
    MoneyTransferHandler moneyTransferHandler;


    Router router() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route(POST, "/transfer-money").handler(moneyTransferHandler);
        return router;
    }
}
