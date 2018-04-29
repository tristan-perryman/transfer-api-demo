## Building & Running

To build and run the tests:
```
./gradlew clean build
```

To run the application:

```
./gradlew run
```

## API

The application has one endpoint that can be hit:

```
POST http://localhost:1234/transfer-money
Json Body Example:

{ 
    "sourceAccount": "12345678",
    "destinationAccount": "87654321",
    "amount": "4.55",
    "currency": "GBP"
}
```

Return 200 OK if successful. If not it will return a certain http status code and an json error code with the error.

Possible errors are:

* Insufficient Account Balance
```
Http Status Code: 422
Json Body:
{
    "errorCode": "INSUFFICIENT_ACCOUNT_BALANCE"
}
```
This means that the source account does not have a sufficient balance to make the transfer.

* Invalid Account

```
Http Status Code: 400
Json Body:
{
    "errorCode": "INVALID_ACCOUNT"
}
```
This means that one of the accounts specified are not valid. You should specify valid accounts.

* Money Overflow

```
Http Status Code: 422
Json Body:
{
    "errorCode": "MONEY_OVERFLOW"
}
```
This means that either the transfer amount itself or the transfer amount + the destination account
balance have caused the account to exceed the maximum possible value. This should not normally happen
in practice as the maximum allowed amount is very large.

* Money Too Many Decimal Places

```
Http Status Code: 400
Json Body:
{
    "errorCode": "MONEY_TOO_MANY_DECIMAL_PLACES"
}
```
The transfer amount specified has too many decimal places. Only a maximum of 10 are currently allowed.

## Assumptions & Shortcomings

* The project is backed by an in-memory MariaDB database. It is designed to properly use transactions and it should be entirely 
possible to have multiple instances of this application running behind a load balancer,  but obviously 
scalability will be limited to the maximum capacity of the SQL database. It might be possible to go
 beyond that by using some form of sharding, for example. Or find a way to convert it to NoSQL.
* The project uses a DECIMAL(65,10) datatype for storing balances. This is unlikely to ever overflow
 as it is very large. However, only 10 decimal places are allowed which should be fine for most 
 currencies, though some cryptocurrencies might require more.
* For currencies with finite units (most non-cryptocurrencies) it might be preferable to restrict 
the number of decimal places per currency. In the interests of keeping this project simple this has not been
 done though, and for some applications allowing smaller units might make sense (e.g. microtransactions
 for an electricity bill).
