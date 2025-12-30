pub mod session;
pub mod breakpoint;
pub mod exec;
pub mod thread;
pub mod frame;
pub mod var;
pub mod eval;
pub mod exception;
pub mod source;
pub mod events;
pub mod server;

use clap::Subcommand;

#[derive(Subcommand)]
pub enum SessionCommands {
    /// Attach to a remote JVM
    Attach {
        /// Remote host
        #[arg(long, short = 'H', default_value = "localhost")]
        host: String,

        /// Debug port
        #[arg(long, short = 'p')]
        port: i32,

        /// Session ID (auto-generated if not specified)
        #[arg(long, short = 'n')]
        name: Option<String>,

        /// Connection timeout in milliseconds
        #[arg(long, default_value = "5000")]
        timeout: i32,
    },

    /// Attach to a local JVM by PID
    AttachPid {
        /// Process ID
        pid: i32,

        /// Session ID (auto-generated if not specified)
        #[arg(long, short = 'n')]
        name: Option<String>,

        /// Connection timeout in milliseconds
        #[arg(long, default_value = "5000")]
        timeout: i32,
    },

    /// Detach from a session
    Detach {
        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,

        /// Terminate the target JVM
        #[arg(long, short = 't')]
        terminate: bool,
    },

    /// Get session status
    Status {
        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// List all sessions
    List,

    /// Select the active session
    Select {
        /// Session ID to select
        session_id: String,
    },
}

#[derive(Subcommand)]
pub enum BreakpointCommands {
    /// Add a breakpoint
    Add {
        /// Fully qualified class name
        #[arg(long, short = 'c')]
        class: String,

        /// Line number (for line breakpoint)
        #[arg(long, short = 'l')]
        line: Option<i32>,

        /// Method name (for method breakpoint)
        #[arg(long, short = 'm')]
        method: Option<String>,

        /// Conditional expression
        #[arg(long)]
        condition: Option<String>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Remove a breakpoint
    #[command(alias = "rm")]
    Remove {
        /// Breakpoint ID
        breakpoint_id: String,
    },

    /// Enable a breakpoint
    Enable {
        /// Breakpoint ID
        breakpoint_id: String,
    },

    /// Disable a breakpoint
    Disable {
        /// Breakpoint ID
        breakpoint_id: String,
    },

    /// List all breakpoints
    #[command(alias = "ls")]
    List {
        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Clear all breakpoints
    Clear {
        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum ExecCommands {
    /// Continue execution
    #[command(alias = "c")]
    Continue {
        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Step execution
    Step {
        /// Step into method calls
        #[arg(long, short = 'i', group = "depth")]
        into: bool,

        /// Step over method calls (default)
        #[arg(long, short = 'o', group = "depth")]
        over: bool,

        /// Step out of current method
        #[arg(long, short = 'u', group = "depth")]
        out: bool,

        /// Thread ID (default: selected thread)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Suspend execution
    Suspend {
        /// Thread ID (default: all threads)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Resume execution
    Resume {
        /// Thread ID (default: all threads)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum ThreadCommands {
    /// List all threads
    #[command(alias = "ls")]
    List {
        /// Show only suspended threads
        #[arg(long)]
        suspended_only: bool,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Select a thread
    Select {
        /// Thread ID
        thread_id: i64,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Suspend a thread
    Suspend {
        /// Thread ID
        thread_id: i64,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Resume a thread
    Resume {
        /// Thread ID
        thread_id: i64,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Get thread info
    Info {
        /// Thread ID (default: selected thread)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum FrameCommands {
    /// List stack frames (backtrace)
    #[command(alias = "ls", alias = "bt")]
    List {
        /// Thread ID (default: selected thread)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Maximum number of frames
        #[arg(long, short = 'n')]
        limit: Option<i32>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Select a stack frame
    Select {
        /// Frame index (0 = top)
        frame_index: i32,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Get frame info
    Info {
        /// Frame index (default: selected frame)
        #[arg(long)]
        frame: Option<i32>,

        /// Thread ID (default: selected thread)
        #[arg(long, short = 't')]
        thread: Option<i64>,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum VarCommands {
    /// List variables in scope
    #[command(alias = "ls")]
    List {
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

    /// Get a variable value
    Get {
        /// Variable name
        name: String,

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

    /// Set a variable value
    Set {
        /// Variable name
        name: String,

        /// New value
        value: String,

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
}

#[derive(Subcommand)]
pub enum ExceptionCommands {
    /// Break on exceptions of specified type
    Catch {
        /// Exception class name
        exception_class: String,

        /// Break on caught exceptions
        #[arg(long, default_value = "true")]
        caught: bool,

        /// Break on uncaught exceptions
        #[arg(long, default_value = "true")]
        uncaught: bool,

        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },

    /// Stop breaking on specified exception
    Ignore {
        /// Exception breakpoint ID or class name
        id_or_class: String,
    },

    /// List exception breakpoints
    #[command(alias = "ls")]
    List {
        /// Session ID (default: active session)
        #[arg(long, short = 's')]
        session: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum ServerCommands {
    /// Start the JDBG daemon server
    Start {
        /// Listen address (tcp://host:port or unix:///path/to/socket)
        #[arg(long, default_value = "tcp://127.0.0.1:5005")]
        listen: String,

        /// Run in foreground (don't daemonize)
        #[arg(long)]
        foreground: bool,
    },

    /// Stop the JDBG daemon server
    Stop,

    /// Check server status
    Status,
}

