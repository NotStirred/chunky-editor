package io.github.notstirred.chunkyeditor;

import io.github.notstirred.chunkyeditor.minecraft.WorldLock;
import io.github.notstirred.chunkyeditor.ui.EditorTab;
import io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.map.WorldMapLoader;
import se.llbit.chunky.ui.ChunkyFx;
import se.llbit.chunky.ui.render.RenderControlsTab;
import se.llbit.chunky.ui.render.RenderControlsTabTransformer;
import se.llbit.chunky.world.World;
import se.llbit.log.Log;
import se.llbit.util.annotation.NotNull;
import se.llbit.util.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Editor implements Plugin {
    public static Editor INSTANCE;

    private final Executor editorExecutor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1));

    @Nullable private VanillaWorldState worldState = null;

    @Nullable private WorldMapLoader mapLoader;

    public Editor() {
        if (Editor.INSTANCE != null) {
            throw new IllegalStateException("Created two instances of the Editor plugin?!");
        }
        Editor.INSTANCE = this;
    }

    @Override public void attach(Chunky chunky) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        }, editorExecutor);

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }

        RenderControlsTabTransformer prev = chunky.getRenderControlsTabTransformer();
        chunky.setRenderControlsTabTransformer(tabs -> {
            List<RenderControlsTab> transformed = new ArrayList<>(prev.apply(tabs));

            transformed.add(new EditorTab(this));

            return transformed;
        });
    }

    public static void main(String[] args) {
        // Start Chunky normally with this plugin attached.
        Chunky.loadDefaultTextures();
        Chunky chunky = new Chunky(ChunkyOptions.getDefaults());
        new Editor().attach(chunky);
        ChunkyFx.startChunkyUI(chunky);
    }

    /**
     * When a world is loaded we dump the current state, ready to be lazy loaded when the user makes an editor action
     */
    public void worldLoaded(World world, Boolean isSameWorld) {
        if(!isSameWorld) {
            this.worldState = null;
        }
    }

    /**
     * Create a world state for the specified world
     * Will ask the user for confirmation if the world has been accessed recently
     *
     * @return The world state for the world, or null if the user cancelled, or null if error
     */
    @Nullable
    private static VanillaWorldState createWorldState(@NotNull World world) {
        try {
            File worldDirectory = world.getWorldDirectory();
            if (worldDirectory == null) {
                return null;
            }

            WorldLock worldLock = WorldLock.of(worldDirectory.toPath());
            if (worldLock.tryLockNormal()) {
                return new VanillaWorldState(world, worldLock);
            } else {
                return null;
            }
        } catch (FileNotFoundException e) {
            Log.warn("Loaded world doesn't exist?!", e);
            return null;
        }
    }

    @Nullable
    public VanillaWorldState getWorldState() {
        if (this.mapLoader != null && this.worldState == null) {
            World world = this.mapLoader.getWorld();
            this.worldState = createWorldState(world);
        }

        return this.worldState;
    }

    public void setMapLoader(@NotNull WorldMapLoader mapLoader) {
        this.mapLoader = mapLoader;
    }

    public <T> Future<T> submitTask(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, this.editorExecutor);
    }
    public Future<Void> submitTask(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, this.editorExecutor);
    }

    public WorldMapLoader mapLoader() {
        return this.mapLoader;
    }
}
