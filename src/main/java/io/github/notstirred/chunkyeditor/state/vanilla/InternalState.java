package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.State;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

public class InternalState implements State<VanillaRegionPos> {
    private final VanillaRegionPos pos;
    /** The entire header for this state */
    final byte[] state;

    InternalState(VanillaRegionPos pos, byte[] state) {
        this.pos = pos;
        this.state = state;
    }

    public void writeState(Path regionDirectory) throws IOException {
        Path regionPath = regionDirectory.resolve(pos.fileName());
        try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "rw")) {
            file.write(this.state);
        }
    }

    @Override
    public VanillaRegionPos position() {
        return this.pos;
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public boolean headerMatches(State<VanillaRegionPos> other) {
        if (other.isInternal()) {
            InternalState internal = (InternalState) other;
            return Arrays.equals(this.state, internal.state);
        } else {
            ExternalState external = (ExternalState) other;
            return Arrays.equals(this.state, 0, HEADER_SIZE_BYTES,
                    external.state, 0, HEADER_SIZE_BYTES);
        }
    }
}