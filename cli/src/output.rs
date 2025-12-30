use clap::ValueEnum;
use colored::Colorize;
use serde::Serialize;
use tabled::{Table, Tabled};

#[derive(Debug, Clone, Copy, ValueEnum, Default)]
pub enum OutputFormat {
    #[default]
    Text,
    Json,
}

pub struct Output {
    format: OutputFormat,
    verbose: bool,
}

impl Output {
    pub fn new(format: OutputFormat, verbose: bool) -> Self {
        Self { format, verbose }
    }

    pub fn success<T: Serialize + ?Sized>(&self, data: &T) {
        match self.format {
            OutputFormat::Json => {
                let wrapper = serde_json::json!({
                    "data": data,
                    "success": true
                });
                println!("{}", serde_json::to_string_pretty(&wrapper).unwrap());
            }
            OutputFormat::Text => {
                // Text output is handled by specific methods
            }
        }
    }

    pub fn error(&self, message: &str) {
        match self.format {
            OutputFormat::Json => {
                let wrapper = serde_json::json!({
                    "error": {
                        "message": message
                    },
                    "success": false
                });
                eprintln!("{}", serde_json::to_string_pretty(&wrapper).unwrap());
            }
            OutputFormat::Text => {
                eprintln!("{}: {}", "Error".red().bold(), message);
            }
        }
    }

    pub fn error_with_code(&self, code: &str, message: &str) {
        match self.format {
            OutputFormat::Json => {
                let wrapper = serde_json::json!({
                    "error": {
                        "code": code,
                        "message": message
                    },
                    "success": false
                });
                eprintln!("{}", serde_json::to_string_pretty(&wrapper).unwrap());
            }
            OutputFormat::Text => {
                eprintln!("{} [{}]: {}", "Error".red().bold(), code.yellow(), message);
            }
        }
    }

    pub fn message(&self, msg: &str) {
        if matches!(self.format, OutputFormat::Text) {
            println!("{}", msg);
        }
    }

    pub fn verbose(&self, msg: &str) {
        if self.verbose {
            eprintln!("{}: {}", "Debug".dimmed(), msg);
        }
    }

    pub fn is_json(&self) -> bool {
        matches!(self.format, OutputFormat::Json)
    }

    pub fn is_text(&self) -> bool {
        matches!(self.format, OutputFormat::Text)
    }

    // Session formatting
    pub fn print_session(&self, session: &crate::client::Session) {
        if self.is_json() {
            self.success(session);
            return;
        }

        println!("{}: {}", "Session".green().bold(), session.id.cyan());
        println!("  {}: {:?}", "Type".dimmed(), session.r#type());
        println!("  {}: {:?}", "State".dimmed(), session.state());
        if !session.host.is_empty() {
            println!("  {}: {}", "Host".dimmed(), session.host);
        }
        if session.port > 0 {
            println!("  {}: {}", "Port".dimmed(), session.port);
        }
        if session.pid > 0 {
            println!("  {}: {}", "PID".dimmed(), session.pid);
        }
        if !session.vm_name.is_empty() {
            println!("  {}: {} {}", "VM".dimmed(), session.vm_name, session.vm_version);
        }
    }

    pub fn print_sessions(&self, sessions: &[crate::client::Session], active_id: &str) {
        if self.is_json() {
            self.success(&serde_json::json!({
                "sessions": sessions,
                "activeSession": active_id
            }));
            return;
        }

        if sessions.is_empty() {
            println!("{}", "No sessions".dimmed());
            return;
        }

        println!("{}:", "Sessions".green().bold());
        for s in sessions {
            let marker = if s.id == active_id { "*" } else { " " };
            let state = format!("{:?}", s.state());
            println!(
                "  {} {} [{}] {:?}",
                marker.green(),
                s.id.cyan(),
                state.yellow(),
                s.r#type()
            );
        }
    }

    // Thread formatting
    pub fn print_threads(&self, threads: &[crate::client::ThreadInfo]) {
        if self.is_json() {
            self.success(threads);
            return;
        }

        if threads.is_empty() {
            println!("{}", "No threads".dimmed());
            return;
        }

        println!("{}:", "Threads".green().bold());
        for t in threads {
            let status = format!("{:?}", t.status());
            let suspended = if t.suspended { " (suspended)".yellow() } else { "".normal() };
            println!(
                "  {} \"{}\" {}{}",
                format!("{}", t.id).cyan(),
                t.name,
                status.dimmed(),
                suspended
            );
        }
    }

    // Frame formatting
    pub fn print_frames(&self, frames: &[crate::client::FrameInfo]) {
        if self.is_json() {
            self.success(frames);
            return;
        }

        if frames.is_empty() {
            println!("{}", "No frames".dimmed());
            return;
        }

        println!("{}:", "Stack frames".green().bold());
        for f in frames {
            let location = if !f.source_name.is_empty() && f.line_number > 0 {
                format!("({}:{})", f.source_name, f.line_number)
            } else if f.is_native {
                "(Native Method)".to_string()
            } else {
                String::new()
            };
            println!(
                "  #{} {}.{} {}",
                format!("{}", f.index).cyan(),
                f.class_name.dimmed(),
                f.method_name,
                location.dimmed()
            );
        }
    }

    // Variable formatting
    pub fn print_variables(&self, vars: &[crate::client::Variable]) {
        if self.is_json() {
            self.success(vars);
            return;
        }

        if vars.is_empty() {
            println!("{}", "No variables".dimmed());
            return;
        }

        println!("{}:", "Variables".green().bold());
        for v in vars {
            let kind = format!("{:?}", v.kind());
            println!(
                "  {} {} = {} {}",
                v.r#type.dimmed(),
                v.name.cyan(),
                v.value,
                format!("[{}]", kind).dimmed()
            );
        }
    }

    // Breakpoint formatting
    pub fn print_breakpoint(&self, bp: &crate::client::Breakpoint) {
        if self.is_json() {
            self.success(bp);
            return;
        }

        let status = if bp.enabled { "+".green() } else { "-".red() };
        println!(
            "{} {} [{}] {} (hits: {})",
            "Breakpoint".green().bold(),
            bp.id.cyan(),
            status,
            bp.location_string,
            bp.hit_count
        );
    }

    pub fn print_breakpoints(&self, breakpoints: &[crate::client::Breakpoint]) {
        if self.is_json() {
            self.success(breakpoints);
            return;
        }

        if breakpoints.is_empty() {
            println!("{}", "No breakpoints".dimmed());
            return;
        }

        println!("{}:", "Breakpoints".green().bold());
        for bp in breakpoints {
            let status = if bp.enabled { "+".green() } else { "-".red() };
            println!("  [{}] {} {}", status, bp.id.cyan(), bp.location_string);
        }
    }

    // Source context formatting
    pub fn print_source_context(&self, ctx: &crate::client::SourceContextResponse) {
        if self.is_json() {
            self.success(ctx);
            return;
        }

        if !ctx.source_name.is_empty() {
            println!(
                "{}: {}:{} - {}.{}",
                "Location".green().bold(),
                ctx.source_name,
                ctx.current_line,
                ctx.class_name.dimmed(),
                ctx.method_name
            );
        }

        for line in &ctx.lines {
            let marker = if line.is_current { ">" } else { " " };
            let bp_marker = if line.has_breakpoint { "*" } else { " " };
            let line_num = format!("{:4}", line.line_number);

            if line.is_current {
                println!(
                    "{}{} {} {}",
                    marker.green().bold(),
                    bp_marker.red(),
                    line_num.cyan(),
                    line.content.bold()
                );
            } else {
                println!("{}{} {} {}", marker, bp_marker.red(), line_num.dimmed(), line.content);
            }
        }
    }

    // Event formatting
    pub fn print_event(&self, event: &crate::client::DebugEvent) {
        if self.is_json() {
            self.success(event);
            return;
        }

        use crate::client::debug_event::Event;
        match &event.event {
            Some(Event::BreakpointHit(e)) => {
                if let Some(loc) = &e.location {
                    println!(
                        "{} {} at {}:{} in thread {}",
                        "Breakpoint hit".yellow().bold(),
                        e.breakpoint_id.cyan(),
                        loc.source_name,
                        loc.line_number,
                        loc.thread_name
                    );
                }
            }
            Some(Event::StepCompleted(e)) => {
                if let Some(loc) = &e.location {
                    println!(
                        "{} at {}:{} in {}",
                        "Step completed".green().bold(),
                        loc.source_name,
                        loc.line_number,
                        loc.method_name
                    );
                }
            }
            Some(Event::Exception(e)) => {
                println!(
                    "{}: {} - {}",
                    "Exception".red().bold(),
                    e.exception_class,
                    e.message
                );
            }
            Some(Event::ThreadStart(e)) => {
                if let Some(t) = &e.thread {
                    println!("{}: {} \"{}\"", "Thread started".blue(), t.id, t.name);
                }
            }
            Some(Event::ThreadDeath(e)) => {
                println!("{}: {} \"{}\"", "Thread died".blue().dimmed(), e.thread_id, e.thread_name);
            }
            Some(Event::VmDeath(e)) => {
                println!("{} (exit code: {})", "VM terminated".red().bold(), e.exit_code);
            }
            Some(Event::VmDisconnect(e)) => {
                println!("{}: {}", "VM disconnected".red().bold(), e.reason);
            }
            None => {}
        }
    }
}

