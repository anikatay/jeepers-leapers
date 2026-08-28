PS-101 — Client registration and sign-in
As a customer, I want to register for the platform and sign in securely, so that I can access my account with confidence it's protected.
Acceptance criteria:

- User can register with a valid email and password
- Passwords are stored hashed (bcrypt), never in plaintext
- Sign-in returns a JWT on valid credentials
- Invalid credentials are rejected with a clear error message

PS-102 — Restrict clients to their own data
As a customer, I want to only view and act on my own positions, cash, and order history, so that my data is never exposed to or altered by another client.
Acceptance criteria:

- Every API call is scoped to the authenticated client's ID
- Requesting another client's data returns a 403/404
- Verified against multiple seeded test clients

PS-103 — Time-limited and revocable sessions
As a customer, I want my signed-in session to expire and be revocable, so that a compromised credential has limited exposure.
Acceptance criteria:

- JWTs expire after a configurable time window
- Logout revokes the active token immediately
- Expired or revoked tokens are rejected on the next request

PS-104 — Submit buy/sell order
As a customer, I want to submit an order to buy or sell a supported instrument, so that I can act on my investment decisions.
Acceptance criteria:

- Order form captures instrument, side, quantity, and order type
- Submitting the form creates an order with status "Submitted"
- Client receives on-screen confirmation of submission

PS-105 — Validate orders against trading rules
As a customer, I want my order checked against trading rules before it's accepted, so that invalid orders are never processed.
Acceptance criteria:

- Orders are rejected if cash/holdings are insufficient
- Orders are rejected if the instrument isn't tradable
- Concurrent orders against the same balance don't cause a race condition

PS-106 — Record accepted order as a firm commitment
As a customer, I want my accepted order recorded as a firm commitment, so that my intent to trade is never lost, even if execution fails later.
Acceptance criteria:

- Order is durably saved with status "Accepted" before execution starts
- An OrderAccepted event is published to Kafka on acceptance
- Order record persists even if the Kafka publish fails

PS-107 — Real-time order status updates
As a customer, I want to see my order status change in real time, so that I don't have to manually refresh the page.
Acceptance criteria:

- Status updates (submitted, accepted, filled, rejected) push to the UI automatically
- No page refresh is required to see the latest status
- Verified end-to-end using simulated status transitions

PS-108 — Price and execute orders against a live quote
As a customer, I want my order priced against a current market quote at execution, so that I get a fair and accurate trade outcome.
Acceptance criteria:

- Execution reads the current cached price at time of fill
- Order is filled or rejected based on that price
- Fill/reject outcome is recorded against the order

PS-109 — Atomic settlement of holdings, cash, and ledger
As a customer, I want my holdings, cash balance, and trade record to update together when my order fills, so that my account is always accurate and consistent.
Acceptance criteria:

- Holdings, cash, and ledger update in a single transaction
- A failure at any point rolls back all three, with no partial update
- Verified with a test that forces a mid-transaction failure

PS-110 — View current holdings and cash balance
As a customer, I want to see my current holdings and cash balance, so that I know my portfolio position at any time.
Acceptance criteria:

- Portfolio screen shows current holdings and cash
- Values reflect the client's latest executed trade
- Data loads correctly against seeded sample holdings

PS-111 — View order and fill history (blotter)
As a customer, I want to see a chronological history of my orders and fills, so that I can review my trading activity.
Acceptance criteria:

- Blotter lists orders/fills newest first
- Supports filtering by date range and instrument
- Large histories are paginated

PS-112 — Multi-asset-class market data feeds
As a customer, I want live quotes available for equities (UK/US/India), FX, and crypto, so that I can trade across all supported asset classes.
Acceptance criteria:

- /quotes/{symbol} returns a current price for each supported asset class
- Quotes refresh in near real time
- Works with mock or live feed sources

PS-113 — Pre-trade indicative pricing
As a customer, I want to see an indicative price before I submit an order, so that I can decide with confidence before committing.
Acceptance criteria:

- Live price is shown on the order form before submission
- Price updates automatically as the market moves
- Displayed price matches the underlying quote source

PS-114 — Permanent audit trail for orders, pricing, and balances
As a customer, I want every accepted order, pricing decision, and balance change permanently recorded, so that my history is never lost, even after a system restart.
Acceptance criteria:

- Every relevant event is written to an append-only audit table
- Audit records survive a system/container restart
- Records cannot be edited or deleted after creation

PS-115 — Reconstruct full trade lifecycle for audit
As a customer, I want the full lifecycle of any of my trades to be reconstructable, so that disputes or audits can be resolved without relying on memory.
Acceptance criteria:

- Each order has a correlation ID linking placement, pricing, and settlement events
- Audit view displays all stages for a given correlation ID, in order
- Verified against seeded multi-stage sample records

PS-116 — OLAP analytics on trading activity
As a business stakeholder, I want trading activity analyzed by instrument, period, and segment, so that I can understand performance without impacting live trading.
Acceptance criteria:

- Trading events stream into a separate reporting database
- Reporting queries don't affect live trading performance
- Aggregates can be sliced by instrument, period, and segment

PS-117 — Operational BI dashboard
As a business stakeholder, I want to see key trading insights like volume, active instruments, and client trends, so that I can monitor platform health.
Acceptance criteria:

- Dashboard displays trading volume, active instruments, and client trends
- Data is sourced from the reporting database
- Values match known sample/test data

PS-118 — Pre-trade slippage and volatility guard
As a customer, I want to be warned about likely slippage or high volatility before my order executes, so that I avoid an unexpectedly poor fill.
Acceptance criteria:

- System flags orders likely to experience significant slippage before execution
- Client sees a warning modal requiring explicit confirmation to proceed
- Business justification for the feature is documented in the ticket
