package io.github.notstirred.chunkyeditor.ui;

import io.github.notstirred.chunkyeditor.Editor;
import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.vanilla.VanillaStateTracker;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.ui.controller.ChunkyFxController;
import se.llbit.chunky.ui.controller.RenderControlsFxController;
import se.llbit.chunky.ui.render.RenderControlsTab;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.fxutil.Dialogs;
import se.llbit.log.Log;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class EditorTab implements RenderControlsTab {
    protected final VBox box;
    private final Editor editor;

    private ChunkyFxController chunkyFxController;

    public EditorTab(Editor editor) {
        this.editor = editor;

        box = new VBox(10.0);
        box.setPadding(new Insets(10.0));

        Button deleteSelectedChunks = new Button("Delete Selected Chunks");
        deleteSelectedChunks.setOnMouseClicked(event -> {
            try {
                VanillaStateTracker stateTracker = editor.getStateTracker();

                if (stateTracker == null) // user said no to confirmation
                    return;

                Collection<ChunkPosition> chunkSelection = this.chunkyFxController.getChunkSelection().getSelection();

                Dialog<ButtonType> confirmationDialog = Dialogs.createSpecialApprovalConfirmation(
                        "Confirm chunk deletion",
                        String.format("Do you want to delete %d chunks?", chunkSelection.size()),
                        "These chunks will be removed from your actual minecraft world\nIf the world is open in minecraft, chunky WILL break your world.\nBe sure to have a backup!",
                        String.format("I do want to delete %d chunks", chunkSelection.size())
                );
                if(confirmationDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }

                Map<VanillaRegionPos, List<ChunkPosition>> regionSelection = new HashMap<>();
                for (ChunkPosition chunkPosition : chunkSelection) {
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
                    return; // We haven't started yet, so can safely cancel
                }

                // do chunk deletion
                var deletionFuture = stateTracker.deleteChunks(this.editor::submitTask, regionSelection);
                deletionFuture = deletionFuture.whenCompleteAsync((result, throwable) -> {
                    // take snapshot of new state to warn user if anything changed when they press undo
                    try {
                        stateTracker.snapshotState(regions);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                try {
                    //TODO: don't immediately wait for the future
                    deletionFuture.get();
                } catch (ExecutionException e) {
                    Log.warn("Deletion completed exceptionally", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        Button undoPreviousAction = new Button("Undo");
        undoPreviousAction.setOnMouseClicked(event -> {
            VanillaStateTracker stateTracker = editor.getStateTracker();

            if (stateTracker == null) { // user said no to confirmation
                return;
            }

            CompletableFuture<Boolean> undoFuture = stateTracker.undo();

            try {
                Boolean b = undoFuture.get();
            } catch (ExecutionException e) {
                Log.warn("Deletion completed exceptionally", e);
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

        Button clearUndoStates = new Button("Clear Undo States");
        clearUndoStates.setOnMouseClicked(event -> {
            VanillaStateTracker stateTracker = editor.getStateTracker();

            if (stateTracker == null) { // user said no to confirmation
                return;
            }

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
}

