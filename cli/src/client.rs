use anyhow::{Context, Result};
use tonic::transport::{Channel, Endpoint, Uri};
use std::str::FromStr;

// Include the generated protobuf code
pub mod proto {
    tonic::include_proto!("jdbg");
}

pub use proto::*;

/// Create a gRPC channel to the server
pub async fn connect(server_addr: &str) -> Result<Channel> {
    let channel = if server_addr.starts_with("unix://") {
        // Unix domain socket
        let path = server_addr.strip_prefix("unix://").unwrap();
        connect_unix(path).await?
    } else {
        // TCP socket (default)
        let addr = if server_addr.starts_with("tcp://") {
            server_addr.strip_prefix("tcp://").unwrap()
        } else {
            server_addr
        };
        connect_tcp(addr).await?
    };
    
    Ok(channel)
}

async fn connect_tcp(addr: &str) -> Result<Channel> {
    let uri = format!("http://{}", addr);
    let endpoint = Endpoint::from_str(&uri)
        .context("Invalid server address")?
        .connect_timeout(std::time::Duration::from_secs(5));
    
    endpoint.connect().await.context("Failed to connect to server")
}

#[cfg(unix)]
async fn connect_unix(path: &str) -> Result<Channel> {
    use tokio::net::UnixStream;
    use tower::service_fn;
    
    let path = path.to_string();
    
    // For Unix sockets, we need a dummy URI but connect via the socket
    let channel = Endpoint::try_from("http://[::]:50051")?
        .connect_with_connector(service_fn(move |_: Uri| {
            let path = path.clone();
            async move {
                UnixStream::connect(path).await
            }
        }))
        .await
        .context("Failed to connect to Unix socket")?;
    
    Ok(channel)
}

#[cfg(not(unix))]
async fn connect_unix(_path: &str) -> Result<Channel> {
    anyhow::bail!("Unix domain sockets are not supported on this platform")
}

/// Debugger service client
pub struct DebuggerClient {
    inner: proto::debugger_service_client::DebuggerServiceClient<Channel>,
}

impl DebuggerClient {
    pub async fn connect(server_addr: &str) -> Result<Self> {
        let channel = connect(server_addr).await?;
        let inner = proto::debugger_service_client::DebuggerServiceClient::new(channel);
        Ok(Self { inner })
    }

    // Session operations
    pub async fn attach_remote(&mut self, session_id: Option<String>, host: &str, port: i32, timeout_ms: i32) -> Result<Session> {
        let request = AttachRequest {
            session_id: session_id.unwrap_or_default(),
            target: Some(attach_request::Target::Remote(RemoteTarget {
                host: host.to_string(),
                port,
                timeout_ms,
            })),
        };
        let response = self.inner.attach_session(request).await?.into_inner();
        response.session.context("No session in response")
    }

    pub async fn attach_local(&mut self, session_id: Option<String>, pid: i32, timeout_ms: i32) -> Result<Session> {
        let request = AttachRequest {
            session_id: session_id.unwrap_or_default(),
            target: Some(attach_request::Target::Local(LocalTarget {
                pid,
                timeout_ms,
            })),
        };
        let response = self.inner.attach_session(request).await?.into_inner();
        response.session.context("No session in response")
    }

    pub async fn detach_session(&mut self, session_id: Option<String>) -> Result<()> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.detach_session(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn get_session_status(&mut self, session_id: Option<String>) -> Result<Session> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.get_session_status(request).await?.into_inner();
        response.session.context("No session in response")
    }

    pub async fn list_sessions(&mut self) -> Result<(Vec<Session>, String)> {
        let response = self.inner.list_sessions(Empty {}).await?.into_inner();
        Ok((response.sessions, response.active_session_id))
    }

    pub async fn set_active_session(&mut self, session_id: &str) -> Result<()> {
        let request = SessionIdRequest {
            session_id: session_id.to_string(),
        };
        let response = self.inner.set_active_session(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    // Breakpoint operations
    pub async fn add_line_breakpoint(&mut self, session_id: Option<String>, class_name: &str, line: i32, condition: Option<String>) -> Result<Breakpoint> {
        let request = AddBreakpointRequest {
            session_id: session_id.unwrap_or_default(),
            location: Some(add_breakpoint_request::Location::Line(LineLocation {
                class_name: class_name.to_string(),
                line_number: line,
            })),
            condition: condition.unwrap_or_default(),
            enabled: true,
        };
        let response = self.inner.add_breakpoint(request).await?.into_inner();
        response.breakpoint.context("No breakpoint in response")
    }

    pub async fn add_method_breakpoint(&mut self, session_id: Option<String>, class_name: &str, method_name: &str, condition: Option<String>) -> Result<Breakpoint> {
        let request = AddBreakpointRequest {
            session_id: session_id.unwrap_or_default(),
            location: Some(add_breakpoint_request::Location::Method(MethodLocation {
                class_name: class_name.to_string(),
                method_name: method_name.to_string(),
                signature: String::new(),
            })),
            condition: condition.unwrap_or_default(),
            enabled: true,
        };
        let response = self.inner.add_breakpoint(request).await?.into_inner();
        response.breakpoint.context("No breakpoint in response")
    }

    pub async fn remove_breakpoint(&mut self, breakpoint_id: &str) -> Result<()> {
        let request = BreakpointIdRequest {
            breakpoint_id: breakpoint_id.to_string(),
        };
        let response = self.inner.remove_breakpoint(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn enable_breakpoint(&mut self, breakpoint_id: &str) -> Result<Breakpoint> {
        let request = BreakpointIdRequest {
            breakpoint_id: breakpoint_id.to_string(),
        };
        let response = self.inner.enable_breakpoint(request).await?.into_inner();
        response.breakpoint.context("No breakpoint in response")
    }

    pub async fn disable_breakpoint(&mut self, breakpoint_id: &str) -> Result<Breakpoint> {
        let request = BreakpointIdRequest {
            breakpoint_id: breakpoint_id.to_string(),
        };
        let response = self.inner.disable_breakpoint(request).await?.into_inner();
        response.breakpoint.context("No breakpoint in response")
    }

    pub async fn list_breakpoints(&mut self, session_id: Option<String>) -> Result<Vec<Breakpoint>> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.list_breakpoints(request).await?.into_inner();
        Ok(response.breakpoints)
    }

    pub async fn clear_breakpoints(&mut self, session_id: Option<String>) -> Result<()> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.clear_breakpoints(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    // Exception breakpoints
    pub async fn catch_exception(&mut self, session_id: Option<String>, exception_class: &str, caught: bool, uncaught: bool) -> Result<ExceptionBreakpoint> {
        let request = CatchExceptionRequest {
            session_id: session_id.unwrap_or_default(),
            exception_class: exception_class.to_string(),
            caught,
            uncaught,
        };
        let response = self.inner.catch_exception(request).await?.into_inner();
        response.breakpoint.context("No exception breakpoint in response")
    }

    pub async fn ignore_exception(&mut self, exception_id: &str) -> Result<()> {
        let request = ExceptionBreakpointIdRequest {
            id: exception_id.to_string(),
        };
        let response = self.inner.ignore_exception(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn list_exception_breakpoints(&mut self, session_id: Option<String>) -> Result<Vec<ExceptionBreakpoint>> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.list_exception_breakpoints(request).await?.into_inner();
        Ok(response.breakpoints)
    }

    // Execution control
    pub async fn continue_execution(&mut self, session_id: Option<String>) -> Result<()> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.r#continue(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn suspend(&mut self, session_id: Option<String>, thread_id: Option<i64>) -> Result<()> {
        let request = SuspendRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
        };
        let response = self.inner.suspend(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn resume(&mut self, session_id: Option<String>, thread_id: Option<i64>) -> Result<()> {
        let request = ResumeRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
        };
        let response = self.inner.resume(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn step(&mut self, session_id: Option<String>, thread_id: Option<i64>, depth: StepDepth) -> Result<StepResponse> {
        let request = StepRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            depth: depth.into(),
            size: StepSize::Line.into(),
        };
        let response = self.inner.step(request).await?.into_inner();
        Ok(response)
    }

    // Thread operations
    pub async fn list_threads(&mut self, session_id: Option<String>) -> Result<Vec<ThreadInfo>> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.list_threads(request).await?.into_inner();
        Ok(response.threads)
    }

    pub async fn select_thread(&mut self, session_id: Option<String>, thread_id: i64) -> Result<()> {
        let request = SelectThreadRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id,
        };
        let response = self.inner.select_thread(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn suspend_thread(&mut self, session_id: Option<String>, thread_id: i64) -> Result<()> {
        let request = ThreadRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id,
        };
        let response = self.inner.suspend_thread(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    pub async fn resume_thread(&mut self, session_id: Option<String>, thread_id: i64) -> Result<()> {
        let request = ThreadRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id,
        };
        let response = self.inner.resume_thread(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    // Frame operations
    pub async fn list_frames(&mut self, session_id: Option<String>, thread_id: Option<i64>, limit: Option<i32>) -> Result<Vec<FrameInfo>> {
        let request = FrameListRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            start_index: 0,
            count: limit.unwrap_or(0),
        };
        let response = self.inner.list_frames(request).await?.into_inner();
        Ok(response.frames)
    }

    pub async fn select_frame(&mut self, session_id: Option<String>, frame_index: i32) -> Result<()> {
        let request = SelectFrameRequest {
            session_id: session_id.unwrap_or_default(),
            frame_index,
        };
        let response = self.inner.select_frame(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    // Variable operations
    pub async fn list_variables(&mut self, session_id: Option<String>, thread_id: Option<i64>, frame_index: Option<i32>) -> Result<Vec<Variable>> {
        let request = VariableListRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            frame_index: frame_index.unwrap_or(0),
        };
        let response = self.inner.list_variables(request).await?.into_inner();
        Ok(response.variables)
    }

    pub async fn get_variable(&mut self, session_id: Option<String>, thread_id: Option<i64>, frame_index: Option<i32>, name: &str) -> Result<Variable> {
        let request = GetVariableRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            frame_index: frame_index.unwrap_or(0),
            name: name.to_string(),
            max_depth: 1,
        };
        let response = self.inner.get_variable(request).await?.into_inner();
        response.variable.context("No variable in response")
    }

    pub async fn set_variable(&mut self, session_id: Option<String>, thread_id: Option<i64>, frame_index: Option<i32>, name: &str, value: &str) -> Result<()> {
        let request = SetVariableRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            frame_index: frame_index.unwrap_or(0),
            name: name.to_string(),
            value: value.to_string(),
        };
        let response = self.inner.set_variable(request).await?.into_inner();
        if !response.success {
            if let Some(e) = response.error {
                anyhow::bail!("{}", e.message);
            }
        }
        Ok(())
    }

    // Evaluation
    pub async fn evaluate(&mut self, session_id: Option<String>, thread_id: Option<i64>, frame_index: Option<i32>, expression: &str) -> Result<EvaluateResponse> {
        let request = EvaluateRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            frame_index: frame_index.unwrap_or(0),
            expression: expression.to_string(),
        };
        let response = self.inner.evaluate(request).await?.into_inner();
        Ok(response)
    }

    // Source context
    pub async fn get_source_context(&mut self, session_id: Option<String>, thread_id: Option<i64>, frame_index: Option<i32>, context_lines: i32) -> Result<SourceContextResponse> {
        let request = SourceContextRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            frame_index: frame_index.unwrap_or(0),
            context_lines,
            source_paths: vec![],
        };
        let response = self.inner.get_source_context(request).await?.into_inner();
        Ok(response)
    }

    // Event management (non-blocking)
    pub async fn poll_events(&mut self, session_id: Option<String>, limit: i32, event_types: Vec<String>) -> Result<EventListResponse> {
        let request = PollEventsRequest {
            session_id: session_id.unwrap_or_default(),
            limit,
            event_types,
        };
        let response = self.inner.poll_events(request).await?.into_inner();
        Ok(response)
    }

    pub async fn wait_for_event(&mut self, session_id: Option<String>, timeout_ms: i32, event_types: Vec<String>) -> Result<EventListResponse> {
        let request = WaitForEventRequest {
            session_id: session_id.unwrap_or_default(),
            timeout_ms,
            event_types,
        };
        let response = self.inner.wait_for_event(request).await?.into_inner();
        Ok(response)
    }

    pub async fn clear_events(&mut self, session_id: Option<String>) -> Result<StatusResponse> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.clear_events(request).await?.into_inner();
        Ok(response)
    }

    pub async fn get_event_info(&mut self, session_id: Option<String>) -> Result<EventInfoResponse> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.get_event_info(request).await?.into_inner();
        Ok(response)
    }

    // Event subscription (streaming)
    pub async fn subscribe_events(&mut self, session_id: Option<String>) -> Result<tonic::Streaming<DebugEvent>> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.subscribe_events(request).await?;
        Ok(response.into_inner())
    }
}

/// Completion service client
pub struct CompletionClient {
    inner: proto::completion_service_client::CompletionServiceClient<Channel>,
}

impl CompletionClient {
    pub async fn connect(server_addr: &str) -> Result<Self> {
        let channel = connect(server_addr).await?;
        let inner = proto::completion_service_client::CompletionServiceClient::new(channel);
        Ok(Self { inner })
    }

    pub async fn complete_sessions(&mut self) -> Result<Vec<CompletionItem>> {
        let response = self.inner.complete_sessions(Empty {}).await?.into_inner();
        Ok(response.items)
    }

    pub async fn complete_classes(&mut self, session_id: Option<String>, prefix: &str, limit: i32) -> Result<Vec<CompletionItem>> {
        let request = ClassCompletionRequest {
            session_id: session_id.unwrap_or_default(),
            prefix: prefix.to_string(),
            limit,
        };
        let response = self.inner.complete_classes(request).await?.into_inner();
        Ok(response.items)
    }

    pub async fn complete_methods(&mut self, session_id: Option<String>, class_name: &str, prefix: &str) -> Result<Vec<CompletionItem>> {
        let request = MethodCompletionRequest {
            session_id: session_id.unwrap_or_default(),
            class_name: class_name.to_string(),
            prefix: prefix.to_string(),
        };
        let response = self.inner.complete_methods(request).await?.into_inner();
        Ok(response.items)
    }

    pub async fn complete_threads(&mut self, session_id: Option<String>) -> Result<Vec<CompletionItem>> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.complete_threads(request).await?.into_inner();
        Ok(response.items)
    }

    pub async fn complete_variables(&mut self, session_id: Option<String>, thread_id: Option<i64>, frame_index: Option<i32>, prefix: &str) -> Result<Vec<CompletionItem>> {
        let request = VariableCompletionRequest {
            session_id: session_id.unwrap_or_default(),
            thread_id: thread_id.unwrap_or(0),
            frame_index: frame_index.unwrap_or(0),
            prefix: prefix.to_string(),
        };
        let response = self.inner.complete_variables(request).await?.into_inner();
        Ok(response.items)
    }

    pub async fn complete_breakpoints(&mut self, session_id: Option<String>) -> Result<Vec<CompletionItem>> {
        let request = SessionIdRequest {
            session_id: session_id.unwrap_or_default(),
        };
        let response = self.inner.complete_breakpoints(request).await?.into_inner();
        Ok(response.items)
    }
}

