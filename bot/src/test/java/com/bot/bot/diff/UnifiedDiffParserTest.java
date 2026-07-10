package com.bot.bot.diff;

import com.bot.bot.domain.ChangeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UnifiedDiffParserTest {

    @Test
    void parsesSingleFileSingleHunkDiff() {
        String diff = """
                diff --git a/src/Main.java b/src/Main.java
                --- a/src/Main.java
                +++ b/src/Main.java
                @@ -1,4 +1,4 @@
                 public class Main {
                -    void oldMethod() {}
                +    void newMethod() {}
                 }
                """;

        UnifiedDiffParser parser = new UnifiedDiffParser();
        List<ChangeChunk> chunks = parser.parse(diff);

        assertEquals(1, chunks.size());
        ChangeChunk chunk = chunks.get(0);
        assertEquals("src/Main.java", chunk.getFilePath());
        assertEquals("java", chunk.getFileType());
        assertEquals(1, chunk.getStartLine());
        assertFalse(chunk.getAddedLines().isEmpty());
        assertFalse(chunk.getRemovedLines().isEmpty());
        assertNotNull(chunk.getContext());
    }
}

