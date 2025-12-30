//! JDBG Client Library
//!
//! This module exposes the gRPC client for use in tests and external integrations.

pub mod client;

pub use client::DebuggerClient;
pub use client::CompletionClient;
pub use client::proto::*;

