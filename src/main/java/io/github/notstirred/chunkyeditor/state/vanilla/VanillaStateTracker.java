package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.Accessor;
import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.minecraft.WorldLock;
import io.github.notstirred.chunkyeditor.state.State;
import javafx.application.Platform;
import se.llbit.chunky.world.Chunk;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.EmptyChunk;
import se.llbit.chunky.world.World;
import se.llbit.chunky.world.region.MCRegion;
import se.llbit.chunky.world.region.Region;
import se.llbit.log.Log;
import se.llbit.util.annotation.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Before any changes are made to the world, it should be checked against the current state to verify nothing has changed
 * If there are changes
 */
public class VanillaStateTracker {
    private static final int NO_STATE = -1;
    protected static final int HEADER_SIZE_BYTES = 4096;

    private final Path regionDirectory;

    private final List<Map<VanillaRegionPos, State<VanillaRegionPos>>> states = new ArrayList<>();
    private int currentStateIdx = NO_STATE;

    private final World world;
    private final WorldLock worldLock;

    public VanillaStateTracker(World world, WorldLock worldLock) throws FileNotFoundException {
        this.regionDirectory = world.getWorldDirectory().toPath().resolve("region");
        this.world = world;
        this.worldLock = worldLock;
    }

    private InternalState internalStateForRegion(VanillaRegionPos regionPos) throws IOException {
        Path regionPath = this.regionDirectory.resolve(regionPos.fileName());

        byte[] data = new byte[HEADER_SIZE_BYTES];
        try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "r")) {
            file.readFully(data);
        }
        return new InternalState(regionPos, data);
    }
    private ExternalState externalStateForRegion(VanillaRegionPos regionPos) throws IOException {
        Path regionPath = this.regionDirectory.resolve(regionPos.fileName());

        byte[] data = Files.readAllBytes(regionPath);
        return new ExternalState(regionPos, data);
    }

    /**
     * Can return the current state
     * @return Null if no previous external can be found for the region
     */
    @Nullable
    private ExternalState findPreviousExternalForRegion(VanillaRegionPos regionPos) {
        List<Map<VanillaRegionPos, State<VanillaRegionPos>>> states = this.states;
        for (int i = currentStateIdx; i >= 0; i--) {
            State<VanillaRegionPos> state = states.get(i).get(regionPos);
            if (state != null) {
                if (!state.isInternal()) {
                    return (ExternalState) state;
                }
            }
        }
        return null;
    }

    /**
     * @return Null if no previous state can be found for the region
     */
    @Nullable
    private State<VanillaRegionPos> findPreviousForRegion(VanillaRegionPos regionPos) {
        List<Map<VanillaRegionPos, State<VanillaRegionPos>>> states = this.states;
        for (int i = currentStateIdx; i >= 0; i--) {
            State<VanillaRegionPos> state = states.get(i).get(regionPos);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    /**
     * @return Null if no changes since the current snapshot
     */
    @Nullable
    private Map<VanillaRegionPos, State<VanillaRegionPos>> snapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        if (this.currentStateIdx == NO_STATE) {
            // snapshot can go ahead with no checks
            Map<VanillaRegionPos, State<VanillaRegionPos>> newStates = new HashMap<>();
            for (VanillaRegionPos regionPos : regionPositions) {
                newStates.put(regionPos, externalStateForRegion(regionPos));
            }
            return newStates;
        } else {
            // snapshot must check against current state to warn user
            Map<VanillaRegionPos, State<VanillaRegionPos>> newStates = new HashMap<>();

            boolean anyDiffer = false;
            for (VanillaRegionPos regionPos : regionPositions) {
                State<VanillaRegionPos> previousAny = findPreviousForRegion(regionPos);
                ExternalState previousExternal = findPreviousExternalForRegion(regionPos);

                State<VanillaRegionPos> newState = externalStateForRegion(regionPos);

                if (previousExternal != null && previousAny != null) {
                    boolean dataMatchesPrevious = previousExternal.dataMatches(newState);
                    if (dataMatchesPrevious) {
                        boolean headerMatchesCurrent = previousAny.headerMatches(newState);
                        if (!headerMatchesCurrent) { // only header differs? internal state
                            newState = internalStateForRegion(regionPos);
                            anyDiffer = true;
                        }
                    } else {
                        anyDiffer = true;
                    }
                } else {
                    anyDiffer = true;
                }
                newStates.put(regionPos, newState);
            }

            if (anyDiffer) {
                return newStates;
            }
        }
        return null;
    }

    /**
     * Retake the current snapshot
     * @return True if a snapshot was taken (the current state differed from the new state)
     */
    public boolean snapshotCurrentState(List<VanillaRegionPos> regionPositions) throws IOException {
        removeFutureStates();

        Map<VanillaRegionPos, State<VanillaRegionPos>> snapshot = snapshot(regionPositions);

        if(snapshot == null) {
            return false;
        }
        if (this.currentStateIdx == NO_STATE) {
            this.states.add(snapshot);
            this.currentStateIdx = 0;
        } else {
            this.states.set(this.currentStateIdx, snapshot);
        }

        return true;
    }

    /**
     * @return True if a snapshot was taken (the current state differed from the new state)
     */
    public boolean snapshotState(List<VanillaRegionPos> regionPositions) throws IOException {
        this.removeFutureStates();
        Map<VanillaRegionPos, State<VanillaRegionPos>> snapshot = snapshot(regionPositions);
        if (snapshot == null) {
            return false;
        }
        this.states.add(snapshot);
        this.currentStateIdx++;
        return true;
    }

    /**
     * Remove all states after the current one
     */
    public void removeFutureStates() {
        if(this.currentStateIdx == NO_STATE) {
            return;
        }
        for (int i = 0; i < this.states.size() - this.currentStateIdx - 1; i++) {
            this.states.remove(currentStateIdx + 1);
        }
    }

    /**
     * Remove all header backups stored
     */
    public void removeAllStates() {
        this.states.clear();
    }

    public CompletableFuture<Boolean> deleteChunks(Executor taskExecutor, Map<VanillaRegionPos, List<ChunkPosition>> regionSelection) {
        if (!worldLock.tryLock()) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> deletionFuture = CompletableFuture.supplyAsync(() -> {
            regionSelection.forEach((regionPos, chunkPositions) -> {
                File regionFile = this.regionDirectory.resolve(regionPos.fileName()).toFile();

                try (RandomAccessFile file = new RandomAccessFile(regionFile, "rw")) {
                    long length = file.length();
                    if (length < 2 * HEADER_SIZE_BYTES) {
                        Log.warn("Missing header in region file, despite trying to delete chunks from it?!\nThis is really bad");
                        return;
                    }

                    for (ChunkPosition chunkPos : chunkPositions) {
                        int x = chunkPos.x & 31;
                        int z = chunkPos.z & 31;
                        int index = x + z * 32;

                        file.seek(4 * index);
                        file.writeInt(0);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return true;
        }, taskExecutor);

        deletionFuture.whenCompleteAsync((result, throwable) -> {
            if (result == null || !result) { // execution or lock failure, no need to update
                return;
            }
            Platform.runLater(() -> regionSelection.forEach((regionPos, chunkPositions) -> {
                Region region = world.getRegion(ChunkPosition.get(regionPos.x, regionPos.z));
                for (ChunkPosition chunkPos : chunkPositions) {
                    Chunk chunk = world.getChunk(chunkPos);
                    if (!chunk.isEmpty()) {
                        chunk.reset();
                        Accessor.invoke_MCRegion$setChunk((MCRegion) region, chunkPos, EmptyChunk.INSTANCE);
                        world.chunkDeleted(chunkPos);
                    }
                }
            }));
        });

        return deletionFuture;
    }

    public CompletableFuture<Void> undo() {
        if (this.currentStateIdx <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        currentStateIdx--; // we decrement first so that if there are errors the user can cancel and redo
        Map<VanillaRegionPos, State<VanillaRegionPos>> previousState = this.states.get(currentStateIdx);

        List<VanillaRegionPos> writtenRegions = new ArrayList<>();
        previousState.forEach((regionPos, state) -> {
            try {
                //TODO: only write to regions modified since the snapshot was taken
                state.writeState(this.regionDirectory);
                writtenRegions.add(state.position());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Platform.runLater(() -> writtenRegions.forEach(regionPos -> {
            Region region = world.getRegion(ChunkPosition.get(regionPos.x, regionPos.z));
            region.parse(0, 0);
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    ChunkPosition chunkPos = ChunkPosition.get(x, z);
                    world.chunkUpdated(chunkPos);
//                    if (chunk.isEmpty()) {
//                        Accessor.invoke_MCRegion$setChunk((MCRegion) region, chunkPos, new Chunk(chunkPos, world));
//                    }
                }
            }
        }));
        return CompletableFuture.completedFuture(null);
    }

    public void redo() {

    }

    private static class StateGroup {
        Map<VanillaRegionPos, State<VanillaRegionPos>> state = new HashMap<>();
        boolean hasExternal = false;

        void put(VanillaRegionPos pos, State<VanillaRegionPos> state) {
            this.state.put(pos, state);
            if (!state.isInternal()) {
                hasExternal = true;
            }
        }

        State<VanillaRegionPos> get(VanillaRegionPos pos) {
            return this.state.get(pos);
        }
    }
}
