package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;

import java.util.List;

public interface Rule {
    List<Finding> analyze(List<ChangeChunk> chunks);
    String getName();
}
