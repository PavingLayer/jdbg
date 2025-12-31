mod client;
mod commands;
mod completion;
mod config;
mod output;

use anyhow::Result;
use clap::{CommandFactory, Parser, Subcommand};
use clap_complete::Shell;

use commands::*;

#[derive(Parser)]
#[command(name = "jdbg")]
#[command(author, version, about = "Non-interactive, scriptable Java debugger CLI")]
#[command(propagate_version = true)]
pub struct Cli {
    /// Server address (tcp://host:port or unix:///path/to/socket)
    #[arg(long, env = "JDBG_SERVER", default_value = "tcp://127.0.0.1:5005")]
    pub server: String,

    /// Output format
    #[arg(long, short = 'f', value_enum, default_value = "text")]
    pub format: output::OutputFormat,

    /// Output in JSON format (shorthand for -f json)
    #[arg(long, conflicts_with = "format")]
    pub json: bool,

    /// Enable verbose output
    #[arg(long, short = 'v')]
    pub verbose: bool,

    #[command(subcommand)]
    pub command: Commands,
}

#[derive(Subcommand)]
pub enum Commands {
    /// Show status overview (JVM state, suspended threads, breakpoints)
    Status {
        /// Session ID or name (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Manage debugging sessions
    Session {
        #[command(subcommand)]
        command: SessionCommands,
    },

    /// Manage breakpoints
    #[command(alias = "bp")]
    Breakpoint {
        #[command(subcommand)]
        command: BreakpointCommands,
    },

    /// Execution control
    Exec {
        #[command(subcommand)]
        command: ExecCommands,
    },

    /// Thread management
    Thread {
        #[command(subcommand)]
        command: ThreadCommands,
    },

    /// Stack frame management
    Frame {
        #[command(subcommand)]
        command: FrameCommands,
    },

    /// Variable inspection
    Var {
        #[command(subcommand)]
        command: VarCommands,
    },

    /// Evaluate an expression
    Eval {
        /// Expression to evaluate
        expression: String,

        /// Thread ID (default: selected thread)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Frame index (default: selected frame)
        #[arg(long)]
        frame: Option<i32>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Exception breakpoints
    Exception {
        #[command(subcommand)]
        command: ExceptionCommands,
    },

    /// Show source code context
    Source {
        /// Thread ID (default: selected thread)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Frame index (default: selected frame)
        #[arg(long)]
        frame: Option<i32>,

        /// Number of context lines
        #[arg(long, short = 'n', default_value = "5")]
        lines: i32,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Debug events (breakpoint hits, exceptions, etc.)
    Events {
        #[command(subcommand)]
        command: EventsCommands,
    },

    /// Generate shell completions
    Completions {
        /// Shell to generate completions for
        #[arg(value_enum)]
        shell: Shell,
    },

    /// Server management
    Server {
        #[command(subcommand)]
        command: ServerCommands,
    },
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli = Cli::parse();

    // Handle completions command specially - doesn't need server connection
    if let Commands::Completions { shell } = &cli.command {
        let mut cmd = Cli::command();
        clap_complete::generate(*shell, &mut cmd, "jdbg", &mut std::io::stdout());
        return Ok(());
    }

    // Create output handler
    let format = if cli.json {
        output::OutputFormat::Json
    } else {
        cli.format
    };
    let output = output::Output::new(format, cli.verbose);

    // Execute the command
    let result = match cli.command {
        Commands::Status { session } => status::execute(session, &cli.server, &output).await,
        Commands::Session { command } => session::execute(command, &cli.server, &output).await,
        Commands::Breakpoint { command } => breakpoint::execute(command, &cli.server, &output).await,
        Commands::Exec { command } => exec::execute(command, &cli.server, &output).await,
        Commands::Thread { command } => thread::execute(command, &cli.server, &output).await,
        Commands::Frame { command } => frame::execute(command, &cli.server, &output).await,
        Commands::Var { command } => var::execute(command, &cli.server, &output).await,
        Commands::Eval { expression, thread, frame, session } => {
            eval::execute(&expression, thread, frame, session, &cli.server, &output).await
        }
        Commands::Exception { command } => exception::execute(command, &cli.server, &output).await,
        Commands::Source { thread, frame, lines, session } => {
            source::execute(thread, frame, lines, session, &cli.server, &output).await
        }
        Commands::Events { command } => events::execute(command, &cli.server, &output).await,
        Commands::Server { command } => server::execute(command, &output).await,
        Commands::Completions { .. } => unreachable!(),
    };

    if let Err(e) = result {
        output.error(&e.to_string());
        std::process::exit(1);
    }

    Ok(())
}
