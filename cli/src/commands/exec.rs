use anyhow::Result;
use crate::client::{DebuggerClient, StepDepth};
use crate::output::Output;
use super::ExecCommands;

pub async fn execute(cmd: ExecCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        ExecCommands::Continue { session } => {
            client.continue_execution(session).await?;
            output.message("Execution continued");
        }
        ExecCommands::Step { into, over: _, out, thread, session } => {
            let depth = if into {
                StepDepth::Into
            } else if out {
                StepDepth::Out
            } else {
                StepDepth::Over
            };
            let response = client.step(session, thread, depth).await?;
            if response.success {
                if let Some(loc) = response.location {
                    output.message(&format!(
                        "Stepped to {}:{} in {}.{}",
                        loc.source_name, loc.line_number, loc.class_name, loc.method_name
                    ));
                }
            }
        }
        ExecCommands::Suspend { thread, session } => {
            client.suspend(session, thread).await?;
            if thread.is_some() {
                output.message("Thread suspended");
            } else {
                output.message("All threads suspended");
            }
        }
        ExecCommands::Resume { thread, session } => {
            client.resume(session, thread).await?;
            if thread.is_some() {
                output.message("Thread resumed");
            } else {
                output.message("All threads resumed");
            }
        }
    }

    Ok(())
}

