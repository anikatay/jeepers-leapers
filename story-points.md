BR-01 — Build registration/login with Spring Security (JWT + bcrypt) against a PostgreSQL users table, exposing /register and /login endpoints with a matching Angular form.

BR-02 — Add a JWT-claims-based Spring Security filter that scopes all queries to the current client ID, tested against a few seeded dummy clients.

BR-03 — Set short JWT expiry and build a Redis-backed revocation store checked on every request, with a /logout endpoint to revoke tokens.

BR-04 — Build an Angular order form posting to a new Spring Boot /orders endpoint that writes to a PostgreSQL orders table, using a hardcoded instrument list.

BR-05 — Write a Spring Boot service that validates orders against seeded cash/holdings data using SELECT ... FOR UPDATE locking, rejecting invalid orders with a clear error.

BR-06 — Persist accepted orders to PostgreSQL and publish an OrderAccepted Kafka event in the same flow, ensuring the DB write survives even if the Kafka publish fails.

BR-07 — Set up a Spring WebSocket/Redis Pub/Sub channel broadcasting order status to an Angular RxJS subscriber, tested with fake status transitions from a test endpoint.

BR-08 — Build an execution worker that fills/rejects orders using a Redis-cached price, tested against a mock price feed populated on a timer.

BR-09 — Write a single @Transactional method updating holdings, cash, and ledger together on fill, verified with a test that forces a mid-transaction failure.

BR-10 — Create a PostgreSQL view aggregating holdings/cash exposed via a /portfolio endpoint and Angular page, built against seeded sample data.

BR-11 — Build a paginated, indexed orders/fills query exposed via /blotter and rendered as an Angular data table, using seeded sample records.

BR-12 — Build a market data service ingesting UK/US/India equities, FX, and crypto quotes (real or mocked) into a Redis cache, exposed via /quotes/{symbol}.

BR-13 — Add an Angular ticker component that shows live prices from the Redis cache on the order form before submission.

BR-14 — Create append-only PostgreSQL audit tables and a shared logging service for orders/pricing/balances, with Docker persistent storage verified via a restart test.

BR-15 — Thread a correlation ID through order events and build an /audit/{correlationId} endpoint plus Angular view to display them in order, tested against seeded multi-stage records.

BR-16 — Stream synthetic trading events via Kafka into a separate PostgreSQL reporting DB structured for aggregation by instrument, period, and segment.

BR-17 — Build an Angular BI dashboard querying the reporting DB for volume, active instruments, and client trends using seeded/synthetic data.

BR-18 — Build a guard service flagging high-slippage orders from mocked price history, paired with an Angular confirmation modal and a short business-justification write-up
