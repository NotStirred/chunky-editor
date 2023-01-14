package io.github.notstirred.chunkyeditor.state;

import java.io.IOException;
import java.nio.file.Path;

public interface State {
    void writeState(Path regionDirectory) throws IOException;

    boolean headerMatches(State other);

    boolean isInternal();

    int size();
}
