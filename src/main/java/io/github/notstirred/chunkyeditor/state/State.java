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
     * @return The size of the state on disk.
     */
    default int onDiskSize() {
        return 0;
    }

    /**
     * Hint to allow the state to be written to disk. This should be called after
     * it is certain this state object is not transient and may stick around for a while.
     */
    default void allowToDisk() {
    }

    /**
     * Allow the state to release any resources. This state object must not be used after this method is called.
     */
    default void release() {
    }
}
