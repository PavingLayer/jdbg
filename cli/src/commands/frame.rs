use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;
use super::FrameCommands;

pub async fn execute(cmd: FrameCommands, server: &str, output: &Output) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;

    match cmd {
        FrameCommands::List { thread, limit, session } => {
            let frames = client.list_frames(session, thread, limit).await?;
            output.print_frames(&frames);
        }
        FrameCommands::Select { frame_index, session } => {
            client.select_frame(session, frame_index).await?;
            output.message(&format!("Selected frame #{}", frame_index));
        }
        FrameCommands::Info { frame, thread, session } => {
            let frames = client.list_frames(session, thread, Some(100)).await?;
            let idx = frame.unwrap_or(0) as usize;
            if idx < frames.len() {
                output.print_frames(&[frames[idx].clone()]);
            } else {
                output.error(&format!("Frame {} not found", idx));
            }
        }
    }

    Ok(())
}

