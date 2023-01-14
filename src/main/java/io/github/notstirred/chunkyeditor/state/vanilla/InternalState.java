package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.state.State;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

/**
 * A snapshot of the HEADER (first sector, without timestamps) (4096 bytes) of a region file.
 * <p>
 * "Internal" here specifies that just the header was changed, which is in practice only ever done by non-minecraft tools
 * such as us!
 * </p>
 */
public class InternalState implements State {
    /** The entire header for this state */
    final byte[] state;

    InternalState(byte[] state) {
        this.state = state;
    }

    public void writeState(Path regionPath) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "rw")) {
            file.write(this.state);
        }
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public boolean headerMatches(State other) {
        if (other.isInternal()) {
            InternalState internal = (InternalState) other;
            return Arrays.equals(this.state, internal.state);
        } else {
            ExternalState external = (ExternalState) other;
            return Arrays.equals(this.state, 0, HEADER_SIZE_BYTES,
                    external.state, 0, HEADER_SIZE_BYTES);
        }
    }

    @Override
    public int size() {
        return this.state.length;
    }
}