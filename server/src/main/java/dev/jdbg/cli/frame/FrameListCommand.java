package dev.jdbg.cli.frame;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.FrameInfo;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * List stack frames.
 */
@Command(
    name = "list",
    aliases = {"ls", "bt", "backtrace"},
    description = "List stack frames (backtrace)"
)
public final class FrameListCommand extends BaseCommand {

    @Option(names = {"--thread", "-t"}, description = "Thread ID (default: selected thread)")
    private String threadIdArg;

    @Option(names = {"--limit", "-n"}, description = "Maximum number of frames to show")
    private Integer limit;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, threadIdArg);
            List<FrameInfo> frames = conn.getFrames(threadId);
            
            if (limit != null && limit > 0 && frames.size() > limit) {
                frames = frames.subList(0, limit);
            }
            
            output.writeSuccess(frames);
        }
        
        return 0;
    }
}

