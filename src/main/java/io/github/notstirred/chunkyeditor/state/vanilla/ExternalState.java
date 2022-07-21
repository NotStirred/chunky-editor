package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.State;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Externally modified region state, such as minecraft writing to the region file
 */
public class ExternalState implements State<VanillaRegionPos> {
    private final VanillaRegionPos pos;

    private final byte[] state;

    ExternalState(VanillaRegionPos pos, byte[] state) {
        this.pos = pos;
        this.state = state;
    }

    private static ExternalState create(VanillaRegionPos pos, Path worldDirectory) throws IOException {
        Path regionPath = worldDirectory.resolve("region").resolve(pos.fileName());
        byte[] data = new byte[VanillaStateTracker.HEADER_SIZE_BYTES];
        try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "r")) {
            file.readFully(data);
        }
        return new ExternalState(pos, data);
    }

    public void writeState(Path worldDirectory) throws IOException {
        Path regionPath = worldDirectory.resolve("region").resolve(pos.fileName());
        try (FileOutputStream file = new FileOutputStream(regionPath.toFile())) {
            file.write(this.state);
        }
    }

    @Override
    public VanillaRegionPos position() {
        return null;
    }
}

