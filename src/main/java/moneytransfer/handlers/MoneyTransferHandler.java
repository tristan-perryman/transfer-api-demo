package moneytransfer.handlers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.rxjava.ext.web.RoutingContext;
import moneytransfer.exceptions.InsufficientAccountBalanceException;
import moneytransfer.exceptions.InvalidAccountException;
import moneytransfer.exceptions.MoneyOverflowException;
import moneytransfer.exceptions.MoneyTooManyDecimalPlacesException;
import moneytransfer.models.ErrorCode;
import moneytransfer.models.ErrorResult;
import moneytransfer.models.MoneyTransfer;
import moneytransfer.services.MoneyTransferService;

import java.math.BigDecimal;

@Singleton
public class MoneyTransferHandler implements Handler<RoutingContext> {

    private final MoneyTransferService moneyTransferService;

    @Inject
    public MoneyTransferHandler(MoneyTransferService moneyTransferService) {
        this.moneyTransferService = moneyTransferService;
    }

    public void handle(RoutingContext routingContext) {

        MoneyTransfer moneyTransfer;
        BigDecimal amount;
        try {
            moneyTransfer = Json.decodeValue(routingContext.getBodyAsString(), MoneyTransfer.class);
            amount = new BigDecimal(moneyTransfer.getAmount());
        } catch (DecodeException | NumberFormatException ex) {
            ErrorResult errorResult = new ErrorResult(ErrorCode.BAD_REQUEST);
            respondWithError(routingContext, 400, errorResult);
            return;
        }

        moneyTransferService
            .transferMoney(moneyTransfer.getSourceAccount(), moneyTransfer.getDestinationAccount(), amount, moneyTransfer.getCurrency())
            .subscribe((__) -> {
                routingContext.response().setStatusCode(200).end();
            }, (throwable) -> {
                handleError(routingContext, throwable);
            });
    }

    private void handleError(RoutingContext routingContext, Throwable throwable) {
        if (throwable instanceof InsufficientAccountBalanceException) {
            ErrorResult errorResult = new ErrorResult(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
            respondWithError(routingContext, 422, errorResult);
        } else if (throwable instanceof InvalidAccountException) {
            ErrorResult errorResult = new ErrorResult(ErrorCode.INVALID_ACCOUNT);
            respondWithError(routingContext, 400, errorResult);
        } else if (throwable instanceof MoneyTooManyDecimalPlacesException) {
            ErrorResult errorResult = new ErrorResult(ErrorCode.MONEY_TOO_MANY_DECIMAL_PLACES);
            respondWithError(routingContext, 400, errorResult);
        } else if (throwable instanceof MoneyOverflowException) {
            ErrorResult errorResult = new ErrorResult(ErrorCode.MONEY_OVERFLOW);
            respondWithError(routingContext, 422, errorResult);
        } else {
            ErrorResult errorResult = new ErrorResult(ErrorCode.INTERNAL_SERVER_ERROR);
            respondWithError(routingContext, 500, errorResult);
        }
    }

    private void respondWithError(RoutingContext routingContext, int status, ErrorResult errorResult) {
        routingContext.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(Json.encodePrettily(errorResult));
    }

}