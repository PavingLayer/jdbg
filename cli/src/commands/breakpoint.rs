use anyhow::{bail, Result};
use crate::client::DebuggerClient;
use crate::output::Output;
use super::BreakpointCommands;

pub async fn execute(cmd: BreakpointCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        BreakpointCommands::Add { class, line, method, condition, session } => {
            let bp = if let Some(line_num) = line {
                client.add_line_breakpoint(session, &class, line_num, condition).await?
            } else if let Some(method_name) = method {
                client.add_method_breakpoint(session, &class, &method_name, condition).await?
            } else {
                bail!("Either --line or --method must be specified");
            };
            output.print_breakpoint(&bp);
        }
        BreakpointCommands::Remove { breakpoint_id } => {
            client.remove_breakpoint(&breakpoint_id).await?;
            output.message(&format!("Breakpoint {} removed", breakpoint_id));
        }
        BreakpointCommands::Enable { breakpoint_id } => {
            let bp = client.enable_breakpoint(&breakpoint_id).await?;
            output.print_breakpoint(&bp);
        }
        BreakpointCommands::Disable { breakpoint_id } => {
            let bp = client.disable_breakpoint(&breakpoint_id).await?;
            output.print_breakpoint(&bp);
        }
        BreakpointCommands::List { session } => {
            let breakpoints = client.list_breakpoints(session).await?;
            output.print_breakpoints(&breakpoints);
        }
        BreakpointCommands::Clear { session } => {
            client.clear_breakpoints(session).await?;
            output.message("All breakpoints cleared");
        }
    }

    Ok(())
}

