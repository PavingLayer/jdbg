use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;
use super::SessionCommands;

pub async fn execute(cmd: SessionCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        SessionCommands::Attach { host, port, name, timeout } => {
            let session = client.attach_remote(name.clone(), &host, port, timeout, name).await?;
            output.print_session(&session);
        }
        SessionCommands::AttachPid { pid, name, timeout } => {
            let session = client.attach_local(None, pid, timeout, name).await?;
            output.print_session(&session);
        }
        SessionCommands::Detach { session, terminate: _ } => {
            client.detach_session(session).await?;
            output.message("Session detached");
        }
        SessionCommands::Info { session } => {
            let session = client.get_session_status(session).await?;
            output.print_session(&session);
        }
        SessionCommands::List => {
            let (sessions, active_id) = client.list_sessions().await?;
            output.print_sessions(&sessions, &active_id);
        }
        SessionCommands::Select { session } => {
            client.set_active_session(&session).await?;
            output.message(&format!("Active session: {}", session));
        }
        SessionCommands::Rename { new_name, session } => {
            client.rename_session(session, &new_name).await?;
            output.message(&format!("Session renamed to: {}", new_name));
        }
    }

    Ok(())
}

