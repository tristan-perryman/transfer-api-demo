package moneytransfer.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MoneyTransfer {
    private String sourceAccount;
    private String destinationAccount;
    private String amount;
    private String currency;

    @JsonCreator
    public MoneyTransfer(@JsonProperty(value="sourceAccount", required=true) String sourceAccount,
                         @JsonProperty(value="destinationAccount", required=true) String destinationAccount,
                         @JsonProperty(value="amount", required=true) String amount,
                         @JsonProperty(value="currency", required=true) String currency) {
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public String getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
