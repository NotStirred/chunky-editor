package io.github.notstirred.chunkyeditor;

import io.github.notstirred.chunkyeditor.minecraft.WorldLock;
import io.github.notstirred.chunkyeditor.ui.EditorTab;
import io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState;
import io.github.notstirred.chunkyeditor.ui.util.ConfirmationDialogue;
import javafx.scene.control.*;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.map.WorldMapLoader;
import se.llbit.chunky.ui.ChunkyFx;
import se.llbit.chunky.ui.render.RenderControlsTab;
import se.llbit.chunky.ui.render.RenderControlsTabTransformer;
import se.llbit.chunky.world.World;
import se.llbit.fxutil.Dialogs;
import se.llbit.log.Log;
import se.llbit.util.annotation.NotNull;
import se.llbit.util.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class Editor implements Plugin {
    public static Editor INSTANCE;
    public static final Cleaner CLEANER = Cleaner.create();

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

            WorldLock worldLock = WorldLock.of(worldDirectory.toPath(), isFirstConfirm -> {
               if (isFirstConfirm) {
                   return Editor.getUserConfirmation();
               }
               return Editor.getScaryUserConfirmation();
            });
            if (worldLock.tryLock()) {
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

    @Nullable
    public VanillaWorldState getWorldStateUnsafe() {
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

    private static boolean getUserConfirmation() {
        Dialog<ButtonType> confirmationDialog = Dialogs.createSpecialApprovalConfirmation(
                "World may be open in Minecraft",
                "Make sure your world is not open in Minecraft",
                "Do you really want to allow Chunky to modify your world?\nIf the world is open in Minecraft, Chunky WILL break your world.\nBe sure to have a backup!",
                "I DO NOT have this world open in Minecraft"
        );

        return confirmationDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static boolean getScaryUserConfirmation() {
        Dialog<ButtonType> confirmationDialog = new ConfirmationDialogue(
                "World is open in Minecraft",
                "It REALLY looks like your world might be open in Minecraft",
                "If the world is open in Minecraft, Chunky WILL break your world.\nIf you ignore this MAKE A BACKUP ANYWAY.\n\nContinuing could corrupt your undo states, making any future undos further break your world!",
                "I AM 100% SURE I DO NOT HAVE THIS WORLD OPEN IN MINECRAFT",
                "Continue and don't Clear Undo Backups",
                "Continuing could corrupt your undo states, making any future undos further break your world!",
                "Continue and Clear Undo Backups"
        );

        ButtonType buttonType = confirmationDialog.showAndWait().orElse(ButtonType.CANCEL);
        if (ButtonType.YES.equals(buttonType)) {
            VanillaWorldState worldStateUnsafe = Editor.INSTANCE.getWorldStateUnsafe();
            assert worldStateUnsafe != null;
            worldStateUnsafe.getStateTracker().removeAllStates();
            return true;
        }
        return ButtonType.NO.equals(buttonType);
    }
}
