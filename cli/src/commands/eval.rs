use anyhow::Result;
use crate::client::DebuggerClient;
use crate::output::Output;

pub async fn execute(
    expression: &str,
    thread: Option<i64>,
    frame: Option<i32>,
    session: Option<String>,
    server: &str,
    output: &Output,
) -> Result<()> {
    let mut client = DebuggerClient::connect(server).await?;
    let response = client.evaluate(session, thread, frame, expression).await?;

    if output.is_json() {
        output.success(&serde_json::json!({
            "expression": expression,
            "result": response.result,
            "type": response.r#type
        }));
    } else {
        println!("{} = {} ({})", expression, response.result, response.r#type);
    }

    Ok(())
}

