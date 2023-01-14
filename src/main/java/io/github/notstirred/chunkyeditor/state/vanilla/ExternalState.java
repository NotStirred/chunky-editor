package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.state.State;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

/**
 * Externally modified region state, such as minecraft writing to the region file
 */
public class ExternalState implements State {
    final byte[] state;

    ExternalState(byte[] state) {
        this.state = state;
    }

    public void writeState(Path regionPath) throws IOException {
        try (FileOutputStream file = new FileOutputStream(regionPath.toFile())) {
            file.write(this.state);
        }
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public boolean headerMatches(State other) {
        if (other.isInternal()) {
            InternalState internal = (InternalState) other;
            return Arrays.equals(this.state, 0, HEADER_SIZE_BYTES,
                    internal.state, 0, HEADER_SIZE_BYTES);
        } else {
            ExternalState external = (ExternalState) other;
            return Arrays.equals(this.state, 0, HEADER_SIZE_BYTES,
                    external.state, 0, HEADER_SIZE_BYTES);
        }
    }

    /**
     * @return If the non-header portion of the state matches
     */
    public boolean dataMatches(State other) {
        if (other.isInternal()) {
            return false;
        }
        ExternalState external = ((ExternalState) other);
        return Arrays.equals(this.state, HEADER_SIZE_BYTES, this.state.length,
                external.state, HEADER_SIZE_BYTES, external.state.length);
    }

    /**
     * Get the header data from this external state as an internal state
     */
    public InternalState asInternalState() {
        return new InternalState(Arrays.copyOf(this.state, HEADER_SIZE_BYTES));
    }

    @Override
    public int size() {
        return this.state.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalState that = (ExternalState) o;
        return Arrays.equals(state, that.state);
    }
}

