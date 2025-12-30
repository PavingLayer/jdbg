// Shell completion support
// This module provides dynamic completions by querying the JDBG server

use crate::client::CompletionClient;

/// Get completions for session IDs
pub async fn complete_sessions(server: &str) -> Vec<String> {
    if let Ok(mut client) = CompletionClient::connect(server).await {
        if let Ok(items) = client.complete_sessions().await {
            return items.into_iter().map(|i| i.value).collect();
        }
    }
    Vec::new()
}

/// Get completions for class names
pub async fn complete_classes(server: &str, prefix: &str) -> Vec<String> {
    if let Ok(mut client) = CompletionClient::connect(server).await {
        if let Ok(items) = client.complete_classes(None, prefix, 50).await {
            return items.into_iter().map(|i| i.value).collect();
        }
    }
    Vec::new()
}

/// Get completions for method names
pub async fn complete_methods(server: &str, class_name: &str, prefix: &str) -> Vec<String> {
    if let Ok(mut client) = CompletionClient::connect(server).await {
        if let Ok(items) = client.complete_methods(None, class_name, prefix).await {
            return items.into_iter().map(|i| i.value).collect();
        }
    }
    Vec::new()
}

/// Get completions for thread IDs
pub async fn complete_threads(server: &str) -> Vec<String> {
    if let Ok(mut client) = CompletionClient::connect(server).await {
        if let Ok(items) = client.complete_threads(None).await {
            return items.into_iter().map(|i| i.value).collect();
        }
    }
    Vec::new()
}

/// Get completions for variable names
pub async fn complete_variables(server: &str, prefix: &str) -> Vec<String> {
    if let Ok(mut client) = CompletionClient::connect(server).await {
        if let Ok(items) = client.complete_variables(None, None, None, prefix).await {
            return items.into_iter().map(|i| i.value).collect();
        }
    }
    Vec::new()
}

/// Get completions for breakpoint IDs
pub async fn complete_breakpoints(server: &str) -> Vec<String> {
    if let Ok(mut client) = CompletionClient::connect(server).await {
        if let Ok(items) = client.complete_breakpoints(None).await {
            return items.into_iter().map(|i| i.value).collect();
        }
    }
    Vec::new()
}

