package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.state.State;
import io.github.notstirred.chunkyeditor.util.ByteBufferUtil;

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

    InternalState(Path regionPath) throws IOException {
        byte[] data = new byte[HEADER_SIZE_BYTES];
        try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "r")) {
            file.readFully(data);
        }
        this.state = data;
    }

    InternalState(ExternalState externalState) {
        byte[] state = new byte[HEADER_SIZE_BYTES];
        externalState.state.get(state);
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
            ExternalState that = (ExternalState) other;
            return ByteBufferUtil.equalsRegion(that.state, this.state, 0, HEADER_SIZE_BYTES);
        }
    }

    @Override
    public int size() {
        return this.state.length;
    }
}