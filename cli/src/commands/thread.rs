use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;
use super::ThreadCommands;

pub async fn execute(cmd: ThreadCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        ThreadCommands::List { suspended_only, session } => {
            let mut threads = client.list_threads(session).await?;
            if suspended_only {
                threads.retain(|t| t.suspended);
            }
            output.print_threads(&threads);
        }
        ThreadCommands::Select { thread_id, session } => {
            client.select_thread(session, thread_id).await?;
            output.message(&format!("Selected thread {}", thread_id));
        }
        ThreadCommands::Suspend { thread_id, session } => {
            client.suspend_thread(session, thread_id).await?;
            output.message(&format!("Thread {} suspended", thread_id));
        }
        ThreadCommands::Resume { thread_id, session } => {
            client.resume_thread(session, thread_id).await?;
            output.message(&format!("Thread {} resumed", thread_id));
        }
        ThreadCommands::Info { thread, session } => {
            let threads = client.list_threads(session).await?;
            if let Some(thread_id) = thread {
                if let Some(t) = threads.iter().find(|t| t.id == thread_id) {
                    output.print_threads(&[t.clone()]);
                } else {
                    output.error(&format!("Thread {} not found", thread_id));
                }
            } else {
                output.error("No thread specified and no thread selected");
            }
        }
    }

    Ok(())
}

