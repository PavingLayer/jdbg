use anyhow::Result;
use tokio_stream::StreamExt;

use crate::client::DebuggerClient;
use crate::output::Output;
use super::EventsCommands;

pub async fn execute(cmd: EventsCommands, server: &str, output: &Output) -> Result<()> {
    match cmd {
        EventsCommands::Poll { limit, types, session } => {
            poll_events(limit, types, session, server, output).await
        }
        EventsCommands::Wait { timeout, types, session } => {
            wait_for_event(timeout, types, session, server, output).await
        }
        EventsCommands::Clear { session } => {
            clear_events(session, server, output).await
        }
        EventsCommands::Info { session } => {
            get_event_info(session, server, output).await
        }
        EventsCommands::Subscribe { session } => {
            subscribe_events(session, server, output).await
        }
    }
}

async fn poll_events(
    limit: i32,
    types: Vec<String>,
    session: Option<String>,
    server: &str,
    output: &Output,
) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let response = client.poll_events(session, limit, types).await?;

    if let Some(err) = response.error {
        output.error(&err.message);
        return Ok(());
    }

    if response.events.is_empty() {
        output.message("No events in buffer");
    } else {
        for event in &response.events {
            output.print_event(event);
        }
        if response.remaining_count > 0 {
            output.verbose(&format!("{} more events in buffer", response.remaining_count));
        }
    }

    Ok(())
}

async fn wait_for_event(
    timeout: i32,
    types: Vec<String>,
    session: Option<String>,
    server: &str,
    output: &Output,
) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let response = client.wait_for_event(session, timeout, types).await?;

    if let Some(err) = response.error {
        output.error(&err.message);
        return Ok(());
    }

    if response.events.is_empty() {
        output.message("No event received (timeout)");
    } else {
        for event in &response.events {
            output.print_event(event);
        }
    }

    Ok(())
}

async fn clear_events(session: Option<String>, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let response = client.clear_events(session).await?;

    if response.success {
        output.message("Event buffer cleared");
    } else if let Some(err) = response.error {
        output.error(&err.message);
    }

    Ok(())
}

async fn get_event_info(session: Option<String>, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let response = client.get_event_info(session).await?;

    if let Some(err) = response.error {
        output.error(&err.message);
        return Ok(());
    }

    output.message(&format!("Buffered events: {}", response.buffered_count));
    output.message(&format!("Buffer capacity: {}", response.buffer_capacity));
    
    if response.events_dropped {
        output.message("Warning: Events were dropped due to buffer overflow");
    }

    if response.buffered_count > 0 {
        if response.oldest_event_timestamp > 0 {
            output.message(&format!("Oldest event: {}ms ago", 
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_millis() as i64 - response.oldest_event_timestamp)
                    .unwrap_or(0)
            ));
        }
        if response.newest_event_timestamp > 0 {
            output.message(&format!("Newest event: {}ms ago",
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_millis() as i64 - response.newest_event_timestamp)
                    .unwrap_or(0)
            ));
        }
    }

    Ok(())
}

async fn subscribe_events(session: Option<String>, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let mut stream = client.subscribe_events(session).await?;

    output.message("Subscribed to debug events. Press Ctrl+C to stop.");

    while let Some(event) = stream.next().await {
        match event {
            Ok(e) => output.print_event(&e),
            Err(e) => {
                output.error(&format!("Stream error: {}", e));
                break;
            }
        }
    }

    Ok(())
}
