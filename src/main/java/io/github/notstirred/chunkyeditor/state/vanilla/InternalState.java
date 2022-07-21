package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.State;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public class InternalState implements State<VanillaRegionPos> {
    private final VanillaRegionPos pos;
    /** The entire header for this state */
    final byte[] state;

    InternalState(VanillaRegionPos pos, byte[] state) {
        this.pos = pos;
        this.state = state;
    }

    private static InternalState create(VanillaRegionPos pos, Path worldDirectory) {
        Path regionPath = worldDirectory.resolve("region").resolve(pos.fileName());
        return null;
    }

    public void writeState(Path worldDirectory) throws IOException {
        Path regionPath = worldDirectory.resolve("region").resolve(pos.fileName());
        try (FileOutputStream file = new FileOutputStream(regionPath.toFile(), false)) {
            file.write(this.state);
        }
    }

    @Override
    public VanillaRegionPos position() {
        return this.pos;
    }
}