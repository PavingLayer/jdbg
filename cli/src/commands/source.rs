use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;

pub async fn execute(
    thread: Option<i64>,
    frame: Option<i32>,
    lines: i32,
    session: Option<String>,
    server: &str,
    output: &Output,
) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let ctx = client.get_source_context(session, thread, frame, lines).await?;
    output.print_source_context(&ctx);
    Ok(())
}

