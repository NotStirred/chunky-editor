package io.github.notstirred.chunkyeditor.state;

import java.io.IOException;
import java.nio.file.Path;

public interface State<T> {
    T position();

    void writeState(Path regionDirectory) throws IOException;

    boolean headerMatches(State<T> other);

    boolean isInternal();
}
