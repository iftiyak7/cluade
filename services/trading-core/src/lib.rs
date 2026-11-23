//! trading-core: the latency-critical decision pipeline in one process
//! (see docs/architecture/ARCHITECTURE.md §2). Currently paper execution
//! only; live execution (buy/sell executors, wallet signer) is not built.

pub mod engine;
pub mod paper;
pub mod replay;
pub mod store;
