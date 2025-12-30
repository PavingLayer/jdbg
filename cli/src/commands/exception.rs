use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;
use super::ExceptionCommands;

pub async fn execute(cmd: ExceptionCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        ExceptionCommands::Catch { exception_class, caught, uncaught, session } => {
            let bp = client.catch_exception(session, &exception_class, caught, uncaught).await?;
            if output.is_json() {
                output.success(&bp);
            } else {
                output.message(&format!(
                    "Exception breakpoint added: {} (caught: {}, uncaught: {})",
                    exception_class, caught, uncaught
                ));
            }
        }
        ExceptionCommands::Ignore { id_or_class } => {
            client.ignore_exception(&id_or_class).await?;
            output.message(&format!("Exception breakpoint removed: {}", id_or_class));
        }
        ExceptionCommands::List { session } => {
            let breakpoints = client.list_exception_breakpoints(session).await?;
            if output.is_json() {
                output.success(&breakpoints);
            } else {
                if breakpoints.is_empty() {
                    output.message("No exception breakpoints");
                } else {
                    println!("Exception breakpoints:");
                    for bp in breakpoints {
                        let flags = format!(
                            "caught: {}, uncaught: {}",
                            bp.caught, bp.uncaught
                        );
                        println!("  {} {} ({})", bp.id, bp.exception_class, flags);
                    }
                }
            }
        }
    }

    Ok(())
}

