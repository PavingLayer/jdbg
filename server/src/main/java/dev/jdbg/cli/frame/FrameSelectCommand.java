package dev.jdbg.cli.frame;

import dev.jdbg.cli.BaseCommand;
import dev.jdbg.jdi.JdiConnection;
import dev.jdbg.model.FrameInfo;
import dev.jdbg.model.Session;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Select a stack frame.
 */
@Command(
    name = "select",
    description = "Select a stack frame"
)
public final class FrameSelectCommand extends BaseCommand {

    @Parameters(index = "0", description = "Frame index (0 = top)")
    private int frameIndex;

    @Override
    protected int execute() {
        final Session session = getSession();
        
        try (final JdiConnection conn = connect(session)) {
            final long threadId = getThreadId(session, null);
            final List<FrameInfo> frames = conn.getFrames(threadId);
            
            if (frameIndex < 0 || frameIndex >= frames.size()) {
                throw new dev.jdbg.JdbgException(dev.jdbg.ErrorCodes.INVALID_FRAME_INDEX, 
                    "Frame index out of range: " + frameIndex + " (0-" + (frames.size() - 1) + ")");
            }
            
            // Update session
            session.setSelectedFrameIndex(frameIndex);
            storage.saveSession(session);
            
            final FrameInfo selectedFrame = frames.get(frameIndex);
            
            final Map<String, Object> result = new HashMap<>();
            result.put("selectedFrame", frameIndex);
            result.put("frame", selectedFrame);
            
            output.writeSuccess(result);
        }
        
        return 0;
    }
}

