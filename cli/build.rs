fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure()
        .build_server(false)  // We only need the client
        .type_attribute(".", "#[derive(serde::Serialize, serde::Deserialize)]")
        .compile(
            &["../proto/jdbg.proto"],
            &["../proto"],
        )?;
    
    // Tell cargo to rerun if proto file changes
    println!("cargo:rerun-if-changed=../proto/jdbg.proto");
    Ok(())
}

