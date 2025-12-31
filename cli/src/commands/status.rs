use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;

pub async fn execute(session: Option<String>, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    
    let status = client.get_status(session).await?;
    output.print_status(&status);
    
    Ok(())
}

