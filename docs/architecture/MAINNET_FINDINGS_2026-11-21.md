# Mainnet findings — 2026-11-21

Observed directly against Solana mainnet (Helius RPC) while building
Building 03. Every claim below is backed by a fixture in
`packages/solana-adapters/tests/fixtures` or by the live runs summarised at
the end.

## The Pump.fun program has changed substantially

| Finding | Consequence for the bot |
|---|---|
| On-chain IDL (`docs/api/pump/idl-onchain-2026-11-21.json`) has 47 instructions, 28 events; logs show `BuyExactQuoteInV3`, `SellV3`, `BondingCurveV3` that the IDL does not list | The **deployed program is newer than its published IDL**. Decoders must be forward-compatible and verified against real transactions, not just the IDL |
| `TradeEvent` carries **16 bytes** beyond the IDL layout | Decoder records `unknown_trailing_bytes`; a test fails if this drifts, flagging layout changes |
| Curves can be **quoted in tokens other than SOL** (USDC is whitelisted in Global; ~19% of sampled trades used other Pump tokens as quote) | On those curves `sol_amount` / `*_sol_reserves` are **0**; real values are in `quote_amount` / `*_quote_reserves`. The bot holds SOL and must skip non-SOL curves |
| Fees: protocol 95 bps + **per-curve creator fee** (default 5, observed 30 and 100, max 100) + optional cashback | Fees must be read per trade/curve. Each component is rounded **up** separately |
| **Mayhem mode** curves start with ~9.3 SOL virtual reserves instead of 30 | Launch constants are not universal; price from live reserves only |
| New mints use **Token-2022** | Mint-extension checks (transfer hook, transfer fee, permanent delegate, default frozen) are mandatory before buying |
| Mainnet carries **v1 transactions** | RPC calls must pass `maxSupportedTransactionVersion: 1` |

## Exact curve formulas (reproduced to the base unit on 10 real trades)

* `buy_exact_quote_in` with curve input `s`: `tokens = floor(vt·(s−1)/(vs+s−1))`
* `buy` (exact tokens `t`): `s = ceil(vs·t/(vt−t))`
* buyout of remaining supply: `s = ceil(vs·t/(vt−t))`, tokens capped at real reserves
* sell `t`: `gross = floor(vs·t/(vt+t))`, fees deducted from `gross`
* fees: `Σ ceil(amount · bps_i / 10 000)`; charged on top for buys
* first buy also pays ~0.00204 SOL rent for the token account (refundable on close) — material for small positions

## Event delivery

* Events are emitted **both** as `Program data:` logs and via `emit_cpi` inner instructions.
* Validators truncate logs: **~1.5–2% of successful Pump.fun transactions** in live runs. In fixtures, truncation removed a creator's first-buy TradeEvent and the **only** `CompletePumpAmmMigrationEvent` of a migration transaction.
* ⇒ Logs-only discovery (`logsSubscribe`) silently misses events. The service backfills truncated transactions with `getTransaction`; the block source reads inner instructions directly.

## Load and infrastructure

* ~140 transactions per slot reference the program (~350/s); **~80% fail** (bot competition).
* Helius (plan in use) rejects JSON-RPC batch requests (429) and rate-limits individual calls; both configured endpoints share one key, so "failover" adds no capacity.
* Block source measured (90 s runs, release build):

| Setting | 429s | Slot lag p50 / p95 | Block-time lag p50 / p95 | Coverage |
|---|---|---|---|---|
| unpaced | ~45% of requests | 22 / 53 slots | 5.7 / 12.9 s | gaps |
| 8 req/s | ~28% | 4 / 12 | 3.2 / 4.1 s | complete |
| **5 req/s (default)** | **0** | **5 / 12** | **3.4 / 4.2 s** | **complete, 0 decode errors** |

Block-time has 1 s resolution, so lag figures are ±1 s.

**Conclusion:** the HTTP block source is reliable but ~3–4 s behind — fine for
monitoring, research and paper trading, **not competitive for sniping**.
Sub-second discovery needs a Yellowstone Geyser stream (or at minimum a
paid-tier WebSocket), co-located with the RPC provider.
