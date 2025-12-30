use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;
use super::VarCommands;

pub async fn execute(cmd: VarCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        VarCommands::List { thread, frame, session } => {
            let variables = client.list_variables(session, thread, frame).await?;
            output.print_variables(&variables);
        }
        VarCommands::Get { name, thread, frame, session } => {
            let variable = client.get_variable(session, thread, frame, &name).await?;
            output.print_variables(&[variable]);
        }
        VarCommands::Set { name, value, thread, frame, session } => {
            client.set_variable(session, thread, frame, &name, &value).await?;
            output.message(&format!("{} = {}", name, value));
        }
    }

    Ok(())
}

