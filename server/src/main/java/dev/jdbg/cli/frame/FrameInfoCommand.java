package dev.jdbg.cli.frame;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.FrameInfo;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * Get information about a stack frame.
 */
@Command(
    name = "info",
    description = "Get frame information"
)
public final class FrameInfoCommand extends BaseCommand {

    @Option(names = {"--thread", "-t"}, description = "Thread ID (default: selected thread)")
    private String threadIdArg;

    @Option(names = {"--frame"}, description = "Frame index (default: selected frame)")
    private Integer frameIndexArg;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, threadIdArg);
            final int frameIndex = getFrameIndex(session, frameIndexArg);
            
            final List<FrameInfo> frames = conn.getFrames(threadId);
            
            if (frameIndex < 0 || frameIndex >= frames.size()) {
                throw new dev.jdbg.JdbgException(dev.jdbg.ErrorCodes.INVALID_FRAME_INDEX, 
                    "Frame index out of range: " + frameIndex);
            }
            
            output.writeSuccess(frames.get(frameIndex));
        }
        
        return 0;
    }
}

