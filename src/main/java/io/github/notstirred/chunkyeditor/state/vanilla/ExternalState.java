package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.state.State;
import io.github.notstirred.chunkyeditor.util.ByteBufferUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

/**
 * A snapshot of an entire region file
 * <p>
 * "External" here specifies that some of the non-header data in the region file was changed, so we take the safe approach
 * and snapshot the whole thing for the user.
 * </p>
 */
public class ExternalState implements State {
    ByteBuffer state;

    ExternalState(Path regionPath) throws IOException {
        this.state = ByteBuffer.wrap(Files.readAllBytes(regionPath));
    }

    public void writeState(Path regionPath) throws IOException {
        try (FileOutputStream file = new FileOutputStream(regionPath.toFile())) {
            if (this.state.hasArray()) {
                file.write(this.state.array());
            } else {
                var channel = Channels.newChannel(file);
                this.state.clear();  // This just resets the position
                channel.write(this.state);
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
            return ByteBufferUtil.equalsRegion(this.state, that.state, 0, HEADER_SIZE_BYTES);
        } else {
            ExternalState that = (ExternalState) other;
            return ByteBufferUtil.equalsRegion(this.state, that.state, 0, HEADER_SIZE_BYTES);
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
        return ByteBufferUtil.equalsRegion(this.state, that.state, HEADER_SIZE_BYTES, this.state.capacity());
    }

    /**
     * Get the header data from this external state as an internal state
     */
    public InternalState asInternalState() {
        return new InternalState(this);
    }

    @Override
    public int size() {
        if (!(this.state instanceof MappedByteBuffer)) {
            return this.state.capacity();
        } else {
            return 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalState that = (ExternalState) o;
        return ByteBufferUtil.equals(this.state, that.state);
    }

    @Override
    public int onDiskSize() {
        if (this.state instanceof MappedByteBuffer) {
            return this.state.capacity();
        } else {
            return 0;
        }
    }

    @Override
    public void allowToDisk() {
        try {
            File tempFile = File.createTempFile("chunky-editor-", ".bin");
            tempFile.deleteOnExit();
            var raFile = new RandomAccessFile(tempFile, "rw");
            MappedByteBuffer buffer = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, this.state.capacity());

            buffer.put(this.state);
            this.state = buffer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

