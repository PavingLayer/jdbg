use anyhow::Result;
use tokio_stream::StreamExt;
use crate::client::DebuggerClient;
use crate::output::Output;

pub async fn execute(
    session: Option<String>,
    server: &str,
    output: &Output,
) -> Result<()> {
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

