# API Design

## Orders
|Method|URL|Description|Status Code|
|------|---|-----------|-----------|
|`GET`|`/orders`||200|
|`GET`|`/orders/{id}`||200, 404|
|`GET`|`/orders/{id}/fee`||200, 404|
|`POST`|`/orders`|||
|`PATCH`|`/orders/{id}/status`|||
|`DELETE`|`/orders/{id}`|||


## Trades
|Method|URL|Description|Status Code|Notes|
|------|---|-----------|-----------|--|
|`GET`|`/trades/{batch_size=150}`|Gets all existing trades (admin, analyst) TODO: later|200|
|`GET`|`/trades/{user_id}`|Getting all trades per user|200|
|`GET`|`/trades/{user_id}/{trade_id}`|Get one trade|200, 404|Need to verify user is authorized to get trade|
|`POST`|`/trades`|Create a new trade||
|`PATCH`|`/trade/{id}/status`|Update a trade's status (Pending -> Fail/Success)||


## Holdings
|Method|URL|Description|Status Code|
|------|---|-----------|-----------|
|`GET`|`/holdings`|For admin only|200|
|`GET`|`/holdings/{account_id}`|Get all holdings of an account|200, 404|
|`POST`|`/holdings`|||
|`PATCH`|`/holdings/{account_id}/{instrument_id}/{quantity}`|||


## Users
|Method|URL|Description|Status Code|
|------|---|-----------|-----------|
|`GET`|`/users`|Get all users (admin)|200|
|`GET`|`/users/{user_id}`|Get specific user|200, 404|
|`GET`|`/users/{user_id}/pii`||200, 404|
|`POST`|`/users`|Create user||
|`PATCH`|`/users/{user_id}/status`|||
|`DELETE`|`/users/{user_id}`|||


## Accounts
|Method|URL|Description|Status Code|
|------|---|-----------|-----------|
|`GET`|`/accounts`|Get all accounts (admin)|200|
|`GET`|`/accounts/{account_id}`|Get specific account|200, 404|
|`POST`|`/accounts`|Create account||
|`PATCH`|`/accounts/{account_id}/status`|||
|`DELETE`|`/accounts/{account_id}`|||


## Instruments
|Method|URL|Description|Status Code|
|------|---|-----------|-----------|
|`GET`|`/instruments/{batch_size-150}`|Get a batch of instruments|200|
|`GET`|`/instruments/{instrument_id}`|Get specific instrument|200, 404|
|`POST`|`/instruments`|Create instrument||
|`PATCH`|`/instruments/{instrument_id}`|||
|`DELETE`|`/instruments/{instrument_id}`|||


## Exchanges
## Instruments
|Method|URL|Description|Status Code|
|------|---|-----------|-----------|
|`GET`|`/exchanges`|Get all exchanges|
|`GET`|`/exchanges/{exchange_id}`|Get specific exchange|200, 404|
|`POST`|`/exchange`|Create exchange (admin)||
|`PATCH`|`/exchange/{exchange_id}`|||
|`DELETE`|`/exchange/{exchange_id}`|||