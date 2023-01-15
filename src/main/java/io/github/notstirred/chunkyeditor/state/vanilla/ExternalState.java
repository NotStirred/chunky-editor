package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.Editor;
import io.github.notstirred.chunkyeditor.state.State;
import se.llbit.log.Log;
import se.llbit.util.Mutable;
import se.llbit.util.annotation.Nullable;

import java.io.*;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

/**
 * A snapshot of an entire region file
 * <p>
 * "External" here specifies that some of the non-header data in the region file was changed, so we take the safe approach
 * and snapshot the whole thing for the user.
 * </p>
 */
public class ExternalState implements State {
    final int stateLength;
    @Nullable private byte[] memState;
    @Nullable private RandomAccessFile diskState;
    @Nullable private Cleaner.Cleanable diskCleaner;

    ExternalState(Path regionPath) throws IOException {
        this.memState = Files.readAllBytes(regionPath);
        this.diskState = null;
        this.stateLength = this.memState.length;
    }

    /**
     * @param toIndex A value of -1 signifies to the end of the state
     */
    synchronized byte[] getStateRegion(int fromIndex, int toIndex) {
        if (toIndex == -1) {
            toIndex = stateLength;
        }
        if (memState != null) {
            return Arrays.copyOfRange(memState, fromIndex, toIndex);
        }
        if (diskState != null) {
            byte[] out = new byte[toIndex - fromIndex];
            try {
                diskState.seek(fromIndex);
                int data = diskState.read(out);
                if (data != out.length) {
                    throw new RuntimeException(String.format("Failed to read state region. Only read %d bytes, " +
                            "expected to read from %d to %d.", data, fromIndex, toIndex));
                }
                return out;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("Attempted to access a released external state object!");
    }

    public void writeState(Path regionPath) throws IOException {
        try (FileOutputStream file = new FileOutputStream(regionPath.toFile())) {
            byte[] state = memState;
            if (state != null) {
                file.write(state);
            } else {
                file.write(getStateRegion(0, -1));
            }
        }
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public boolean headerMatches(State other) {
        if (other.isInternal()) {
            InternalState that = (InternalState) other;
            return Arrays.equals(this.getStateRegion(0, HEADER_SIZE_BYTES), 0, HEADER_SIZE_BYTES, that.state, 0, HEADER_SIZE_BYTES);
        } else {
            ExternalState that = (ExternalState) other;
            return Arrays.equals(this.getStateRegion(0, HEADER_SIZE_BYTES), that.getStateRegion(0, HEADER_SIZE_BYTES));
        }
    }

    /**
     * @return If the non-header portion of the state matches
     */
    public boolean dataMatches(State other) {
        if (other.isInternal()) {
            return false;
        }
        ExternalState that = ((ExternalState) other);
        return Arrays.equals(this.getStateRegion(HEADER_SIZE_BYTES, -1), that.getStateRegion(HEADER_SIZE_BYTES, -1));
    }

    /**
     * Get the header data from this external state as an internal state
     */
    public InternalState asInternalState() {
        return new InternalState(this);
    }

    @Override
    public int size() {
        return memState != null ? stateLength : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalState that = (ExternalState) o;
        return Arrays.equals(this.getStateRegion(0, -1), that.getStateRegion(0, -1));
    }

    @Override
    public int onDiskSize() {
        return diskState != null ? stateLength : 0;
    }

    @Override
    public synchronized void allowToDisk() {
        if (memState == null) {
            if (diskState == null) {
                throw new IllegalStateException("Attempted to access a released external state object!");
            } else {
                // Already on disk
                return;
            }
        }

        try {
            File tempFile = File.createTempFile("chunky-editor-", ".bin");
            tempFile.deleteOnExit();
            Mutable<RandomAccessFile> raf = new Mutable<>(null);
            diskCleaner = Editor.CLEANER.register(this, () -> {
                var r = raf.get();
                if (r != null) {
                    try {
                        r.close();
                    } catch (IOException e) {
                        Log.info("Failed to close random access file: " + tempFile);
                    }
                }
                if (!tempFile.delete()) {
                    Log.info("Failed to delete temporary file: " + tempFile);
                }
            });

            diskState = new RandomAccessFile(tempFile, "rw");
            raf.set(diskState);
            diskState.write(memState);
        } catch (IOException e) {
            diskState = null;
            Log.warn("Failed to commit external state to disk", e);
            return;
        }
        memState = null;
    }

    @Override
    public synchronized void release() {
        var state = diskState;
        memState = null;
        diskState = null;
        if (state == null) {
            return;
        }
        diskCleaner.clean();
    }
}

