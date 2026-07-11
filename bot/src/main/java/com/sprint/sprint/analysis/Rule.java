package com.sprint.sprint.analysis;

import com.sprint.sprint.domain.ChangeChunk;
import com.sprint.sprint.domain.Finding;

import java.util.List;

public interface Rule {
    List<Finding> analyze(List<ChangeChunk> chunks);
    String getName();
}
