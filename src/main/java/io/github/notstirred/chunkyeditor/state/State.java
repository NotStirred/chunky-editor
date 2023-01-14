package io.github.notstirred.chunkyeditor.state;

import java.io.IOException;
import java.nio.file.Path;

public interface State {
    /**
     * Tells the state to write itself to the specified region path.
     *
     * @param regionPath The path of the region file to write to
     */
    void writeState(Path regionPath) throws IOException;

    /**
     * @param other The other state to compare against
     * @return Whether the data within the region header matches the other state.
     */
    boolean headerMatches(State other);

    /**
     * @return Whether the state is an internal state (Modifies only the region header).
     */
    boolean isInternal();

    /**
     * @return The size of the state in-memory
     */
    int size();

    /**
     * @return The size of the state on disk. (Currently unused)
     */
    default int onDiskSize() {
        return 0;
    }
}
