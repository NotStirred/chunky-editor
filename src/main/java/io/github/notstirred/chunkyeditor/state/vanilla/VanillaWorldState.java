package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.Accessor;
import io.github.notstirred.chunkyeditor.Editor;
import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.minecraft.WorldLock;
import javafx.application.Platform;
import se.llbit.chunky.world.Chunk;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.EmptyChunk;
import se.llbit.chunky.world.World;
import se.llbit.chunky.world.region.MCRegion;
import se.llbit.chunky.world.region.Region;
import se.llbit.log.Log;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VanillaWorldState {
    public static final int HEADER_SIZE_BYTES = 4096;

    private final Path regionDirectory;
    private final World world;
    private final WorldLock worldLock;

    private final VanillaStateTracker stateTracker;

    public VanillaWorldState(World world, WorldLock worldLock) throws FileNotFoundException {
        this.regionDirectory = world.getWorldDirectory().toPath().resolve("region");
        this.world = world;
        this.worldLock = worldLock;

        this.stateTracker = new VanillaStateTracker(regionDirectory);
    }

    public CompletableFuture<Boolean> deleteChunks(Executor taskExecutor, Collection<ChunkPosition> chunks) {
        Map<VanillaRegionPos, List<ChunkPosition>> regionSelection = new HashMap<>();
        for (ChunkPosition chunkPosition : chunks) {
            ChunkPosition asRegionPos = chunkPosition.regionPosition();

            regionSelection.computeIfAbsent(new VanillaRegionPos(asRegionPos.x, asRegionPos.z), pos -> new ArrayList<>())
                    .add(chunkPosition);
        }
        List<VanillaRegionPos> regions = new ArrayList<>(regionSelection.keySet());

        try {
            // we first overwrite the current snapshot, ready to be undone
            stateTracker.snapshotCurrentState(regions);
        } catch (FileNotFoundException e) {
            Log.info("Could not find region file, ignoring this file and continuing", e);
        } catch (EOFException e) {
            Log.info("Invalid region file header, ignoring this file and continuing", e);
        } catch (IOException e) {
            Log.warn("Could not take snapshot of regions, aborting.", e);
            return CompletableFuture.completedFuture(false); // We haven't started yet, so can safely cancel
        }

        CompletableFuture<Boolean> deletionFuture = this.deleteChunks(taskExecutor, regionSelection);

        deletionFuture = deletionFuture.whenCompleteAsync((result, throwable) -> {
            // take snapshot of new state to warn user if anything changed when they press undo
            try {
                stateTracker.snapshotState(regions);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return deletionFuture;
    }

    private CompletableFuture<Boolean> deleteChunks(Executor taskExecutor, Map<VanillaRegionPos, List<ChunkPosition>> regionSelection) {
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
            regionSelection.forEach((regionPos, chunkPositions) -> {
                Region region = world.getRegion(ChunkPosition.get(regionPos.x, regionPos.z));
                for (ChunkPosition chunkPos : chunkPositions) {
                    Chunk chunk = world.getChunk(chunkPos);
                    if (!chunk.isEmpty()) {
                        chunk.reset();
                        Accessor.invoke_MCRegion$setChunk((MCRegion) region, chunkPos, EmptyChunk.INSTANCE);
                        world.chunkDeleted(chunkPos);
                    }
                }
            });
        }, Platform::runLater);

        return deletionFuture;
    }

    public CompletableFuture<Boolean> undo() {
        if(!this.stateTracker.hasPreviousState()) {
            return CompletableFuture.completedFuture(false);
        }

        if (!worldLock.tryLock())
            return CompletableFuture.completedFuture(false);

        List<VanillaRegionPos> writtenRegions = new ArrayList<>();
        CompletableFuture<Boolean> undoFuture = CompletableFuture.supplyAsync(() -> {
            this.stateTracker.previousState().forEach((regionPos, state) -> {
                try {
                    //TODO: only write to regions modified since the snapshot was taken
                    state.writeState(this.regionDirectory);
                    writtenRegions.add(state.position());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return true;
        });

        undoFuture.whenCompleteAsync((success, throwable) -> {
            if (!success) {
                return;
            }
            writtenRegions.forEach(regionPos ->
                    Editor.INSTANCE.mapLoader().regionUpdated(ChunkPosition.get(regionPos.x, regionPos.z)));
        }, Platform::runLater);
        return undoFuture;
    }

    public VanillaStateTracker getStateTracker() {
        return this.stateTracker;
    }
}
