package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.State;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

/**
 * Externally modified region state, such as minecraft writing to the region file
 */
public class ExternalState implements State<VanillaRegionPos> {
    private final VanillaRegionPos pos;
    final byte[] state;

    ExternalState(VanillaRegionPos pos, byte[] state) {
        this.pos = pos;
        this.state = state;
    }

    public void writeState(Path regionDirectory) throws IOException {
        Path regionPath = regionDirectory.resolve(pos.fileName());
        try (FileOutputStream file = new FileOutputStream(regionPath.toFile())) {
            file.write(this.state);
        }
    }

    @Override
    public VanillaRegionPos position() {
        return this.pos;
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public boolean headerMatches(State<VanillaRegionPos> other) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalState that = (ExternalState) o;
        return Objects.equals(pos, that.pos) && Arrays.equals(state, that.state);
    }

    /**
     * @return If the non-header portion of the state matches
     */
    public boolean dataMatches(State<VanillaRegionPos> other) {
        if (other.isInternal()) {
            return false;
        }
        ExternalState external = ((ExternalState) other);
        return Arrays.equals(this.state, HEADER_SIZE_BYTES, this.state.length,
                external.state, HEADER_SIZE_BYTES, external.state.length);
    }
}

