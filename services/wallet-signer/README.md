# services/wallet-signer

Rust. Isolated signing service. Holds the trading key (encrypted at rest), signs only whitelisted Pump.fun instructions, enforces per-tx and per-day spend caps independently of risk-engine. No public network exposure. Status: not started.
