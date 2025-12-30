package dev.jdbg.cli.source;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.FrameInfo;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Show source code context.
 */
@Command(
    name = "source",
    description = "Show source code context"
)
public final class SourceCommand extends BaseCommand {

    @Option(names = {"--thread", "-t"}, description = "Thread ID (default: selected thread)")
    private String threadIdArg;

    @Option(names = {"--frame"}, description = "Frame index (default: selected frame)")
    private Integer frameIndexArg;

    @Option(names = {"--lines", "-n"}, description = "Number of context lines", defaultValue = "5")
    private int contextLines;

    @Option(names = {"--sourcepath", "-sp"}, description = "Source path (colon-separated)")
    private String sourcePath;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, threadIdArg);
            final int frameIndex = getFrameIndex(session, frameIndexArg);
            
            final List<FrameInfo> frames = conn.getFrames(threadId);
            if (frameIndex >= frames.size()) {
                throw new dev.jdbg.JdbgException(dev.jdbg.ErrorCodes.INVALID_FRAME_INDEX, 
                    "Frame index out of range");
            }
            
            final FrameInfo frame = frames.get(frameIndex);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("className", frame.getClassName());
            result.put("methodName", frame.getMethodName());
            result.put("sourceName", frame.getSourceName());
            result.put("lineNumber", frame.getLineNumber());
            
            // Try to find and read source
            if (frame.getSourceName() != null && frame.getLineNumber() != null) {
                final List<String> sourceLines = findAndReadSource(
                    frame.getClassName(), frame.getSourceName(), 
                    frame.getLineNumber(), contextLines);
                
                if (!sourceLines.isEmpty()) {
                    result.put("source", sourceLines);
                    result.put("currentLine", frame.getLineNumber());
                }
            }
            
            output.writeSuccess(result);
        }
        
        return 0;
    }

    private List<String> findAndReadSource(final String className, final String sourceName,
                                           final int lineNumber, final int context) {
        final List<String> lines = new ArrayList<>();
        
        // Convert class name to path
        final String classPath = className.replace('.', '/');
        final String packagePath = classPath.contains("/") 
            ? classPath.substring(0, classPath.lastIndexOf('/')) 
            : "";
        
        // Try to find source file
        final List<Path> searchPaths = new ArrayList<>();
        
        // Add sourcepath entries
        if (sourcePath != null) {
            for (final String sp : sourcePath.split(":")) {
                searchPaths.add(Path.of(sp, packagePath, sourceName));
                searchPaths.add(Path.of(sp, sourceName));
            }
        }
        
        // Add current directory
        searchPaths.add(Path.of(packagePath, sourceName));
        searchPaths.add(Path.of("src/main/java", packagePath, sourceName));
        searchPaths.add(Path.of("src", packagePath, sourceName));
        
        for (final Path path : searchPaths) {
            if (Files.exists(path)) {
                return readSourceLines(path, lineNumber, context);
            }
        }
        
        return lines;
    }

    private List<String> readSourceLines(final Path path, final int lineNumber, final int context) {
        final List<String> result = new ArrayList<>();
        final int startLine = Math.max(1, lineNumber - context);
        final int endLine = lineNumber + context;
        
        try (final BufferedReader reader = Files.newBufferedReader(path)) {
            int currentLine = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                currentLine++;
                if (currentLine >= startLine && currentLine <= endLine) {
                    final String marker = currentLine == lineNumber ? ">" : " ";
                    result.add(String.format("%s %4d: %s", marker, currentLine, line));
                }
                if (currentLine > endLine) {
                    break;
                }
            }
        } catch (final IOException e) {
            // Ignore - source not readable
        }
        
        return result;
    }
}

