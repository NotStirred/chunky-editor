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

    private final List<State<VanillaRegionPos>[]> states = new ArrayList<>();
    private int currentStateIdx = NO_STATE;

    private final World world;
    private final WorldLock worldLock;

    public VanillaStateTracker(World world, WorldLock worldLock) throws FileNotFoundException {
        this.regionDirectory = world.getWorldDirectory().toPath().resolve("region");
        this.world = world;
        this.worldLock = worldLock;
    }

    private State<VanillaRegionPos>[] internalSnapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        InternalState[] stateArray = new InternalState[regionPositions.size()];
        for (int regionIdx = 0, regionPositionsSize = regionPositions.size(); regionIdx < regionPositionsSize; regionIdx++) {
            VanillaRegionPos regionPosition = regionPositions.get(regionIdx);
            Path regionPath = this.regionDirectory.resolve(regionPosition.fileName());

            byte[] data = new byte[HEADER_SIZE_BYTES];
            try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "r")) {
                file.readFully(data);
            }
            stateArray[regionIdx] = new InternalState(regionPosition, data);
        }
        return stateArray;
    }

    private State<VanillaRegionPos>[] externalSnapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        ExternalState[] stateArray = new ExternalState[regionPositions.size()];
        for (int regionIdx = 0, regionPositionsSize = regionPositions.size(); regionIdx < regionPositionsSize; regionIdx++) {
            VanillaRegionPos regionPosition = regionPositions.get(regionIdx);
            Path regionPath = this.regionDirectory.resolve(regionPosition.fileName());

            byte[] data = Files.readAllBytes(regionPath);
            stateArray[regionIdx] = new ExternalState(regionPosition, data);
        }
        return stateArray;
    }

    private State<VanillaRegionPos>[] snapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        return null;
    }

    /**
     * Retake the current snapshot
     */
    public void snapshotCurrentState(List<VanillaRegionPos> regionPositions) throws IOException {
        removeFutureStates();

        State<VanillaRegionPos>[] snapshot = snapshot(regionPositions);
        if (this.currentStateIdx == NO_STATE) {
            this.states.add(snapshot);
            this.currentStateIdx = 0;
        } else {
            this.states.set(this.currentStateIdx, snapshot);
        }
    }

    public void snapshotState(List<VanillaRegionPos> regionPositions) throws IOException {
        this.removeFutureStates();
        this.states.add(snapshot(regionPositions));
        this.currentStateIdx++;
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

    public Future<Boolean> deleteChunks(Executor taskExecutor, Map<VanillaRegionPos, List<ChunkPosition>> regionSelection) {
        CompletableFuture<Boolean> deletionFuture = CompletableFuture.supplyAsync(() -> {
            if (worldLock.tryLock()) {
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
            } else {
                return false;
            }
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
}
