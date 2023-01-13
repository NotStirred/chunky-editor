package io.github.notstirred.chunkyeditor.ui;

import io.github.notstirred.chunkyeditor.Editor;
import io.github.notstirred.chunkyeditor.state.vanilla.VanillaStateTracker;
import io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.ui.controller.ChunkyFxController;
import se.llbit.chunky.ui.controller.RenderControlsFxController;
import se.llbit.chunky.ui.render.RenderControlsTab;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.fxutil.Dialogs;
import se.llbit.log.Log;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class EditorTab implements RenderControlsTab {
    protected final VBox box;
    private final Editor editor;

    private ChunkyFxController chunkyFxController;

    private Button deleteSelectedChunks;
    private Button undoPreviousAction;
    private Button clearUndoStates = null;

    private static final String CLEAR_UNDO_STATES_TEXT = "Clear Undo States";

    public EditorTab(Editor editor) {
        this.editor = editor;

        box = new VBox(10.0);
        box.setPadding(new Insets(10.0));

        deleteSelectedChunks = new Button("Delete Selected Chunks");
        deleteSelectedChunks.setTooltip(new Tooltip("Deletes the selected chunks (shocking, I know)"));
        deleteSelectedChunks.setOnMouseClicked(event -> {
            VanillaWorldState worldState = editor.getWorldState();

            if (worldState == null) // user said no to confirmation
                return;

            VanillaStateTracker stateTracker = worldState.getStateTracker();

            Collection<ChunkPosition> chunkSelection = this.chunkyFxController.getChunkSelection().getSelection();

            Dialog<ButtonType> confirmationDialog = Dialogs.createSpecialApprovalConfirmation(
                    "Confirm chunk deletion",
                    String.format("Do you want to delete %d chunks?", chunkSelection.size()),
                    "These chunks will be removed from your actual Minecraft world\nIf the world is open in Minecraft, Chunky WILL break your world.\nBe sure to have a backup!",
                    String.format("I do want to delete %d chunks", chunkSelection.size())
            );
            if(confirmationDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
                return;

            try {
                CompletableFuture<Optional<IOException>> deletionFuture = worldState.deleteChunks(this.editor::submitTask, chunkSelection);
                // set memory usage info on clear states button
                deletionFuture.whenCompleteAsync((result, throwable) -> {
                    clearUndoStates.setText(String.format("%s (%dMiB)", CLEAR_UNDO_STATES_TEXT, (int) (stateTracker.statesSizeBytes() / 1024 / 1024)));
                }, Platform::runLater);

                //TODO: don't immediately wait for the future
                Optional<IOException> exception = deletionFuture.get();
                exception.ifPresent(e -> Log.warn("Error when deleting chunks", e));

            } catch (ExecutionException e) {
                Log.warn("Deletion completed exceptionally", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        undoPreviousAction = new Button("Undo");
        undoPreviousAction.setTooltip(new Tooltip("Undoes the last delete"));
        undoPreviousAction.setOnMouseClicked(event -> {
            VanillaWorldState worldState = editor.getWorldState();

            if (worldState == null) // user said no to confirmation
                return;

            try {
                Optional<IOException> exception = worldState.undo(this.editor::submitTask).get();
                exception.ifPresent(e -> Log.warn("Error when undoing", e));
            } catch (ExecutionException e) {
                Log.warn("Undo completed exceptionally", e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(6);
        optionsGrid.add(deleteSelectedChunks, 0, 0);
        optionsGrid.add(undoPreviousAction, 1, 0);

        GridPane advancedOptionsGrid = new GridPane();
        advancedOptionsGrid.setHgap(6);

        clearUndoStates = new Button(CLEAR_UNDO_STATES_TEXT);
        clearUndoStates.setTooltip(new Tooltip("Clears the deletion undo states, and saves some memory"));
        clearUndoStates.setOnMouseClicked(event -> {
            VanillaWorldState worldState = editor.getWorldState();

            if (worldState == null) // user said no to confirmation
                return;

            VanillaStateTracker stateTracker = worldState.getStateTracker();

            if (!stateTracker.hasState()) // no active state
                return;

            int stateCount = stateTracker.stateCount();
            Dialog<ButtonType> confirmationDialog = Dialogs.createSpecialApprovalConfirmation(
                    "Confirm state clear",
                    String.format("Do you want to clear %d undo states?", stateCount),
                    String.format("This will save approximately %dMiB.\nYou won't be able to get the previous undo states back!", (int) (stateTracker.statesSizeBytes() / 1024 / 1024)),
                    String.format("I do want to clear %d undo states", stateCount));

            if (confirmationDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }

            clearUndoStates.setText(CLEAR_UNDO_STATES_TEXT);
            stateTracker.removeAllStates();
        });
        advancedOptionsGrid.add(clearUndoStates, 0, 0);

        TitledPane advancedOptionsPane = new TitledPane("Advanced Options", advancedOptionsGrid);
        advancedOptionsPane.setExpanded(false);
        advancedOptionsPane.setAnimated(false);
        advancedOptionsPane.setMaxWidth(400);

        box.getChildren().add(optionsGrid);
        box.getChildren().add(advancedOptionsPane);
    }

    public void setController(RenderControlsFxController controller) {
        this.chunkyFxController = controller.getChunkyController();
        this.chunkyFxController.getMapLoader().addWorldLoadListener(this.editor::worldLoaded);
        this.chunkyFxController.getMapLoader().addWorldLoadListener(this::worldLoaded);
        this.editor.setMapLoader(this.chunkyFxController.getMapLoader());
    }

    @Override
    public void update(Scene scene) {

    }

    @Override
    public String getTabTitle() {
        return "Editor";
    }

    @Override
    public Node getTabContent() {
        return box;
    }

    private void worldLoaded(World world, Boolean isSameWorld) {
        if (!isSameWorld) {
            this.clearUndoStates.setText(CLEAR_UNDO_STATES_TEXT);
        }
    }
}

