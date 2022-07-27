package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.State;
import se.llbit.util.annotation.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static io.github.notstirred.chunkyeditor.state.vanilla.VanillaWorldState.HEADER_SIZE_BYTES;

/**
 * Before any changes are made to the world, it should be checked against the current state to verify nothing has changed
 * If there are changes
 */
public class VanillaStateTracker {
    private static final int NO_STATE = -1;

    private final Path regionDirectory;

    private final List<Map<VanillaRegionPos, State<VanillaRegionPos>>> states = new ArrayList<>();
    private int currentStateIdx = NO_STATE;

    public VanillaStateTracker(Path regionDirectory) {
        this.regionDirectory = regionDirectory;
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
    private Map<VanillaRegionPos, State<VanillaRegionPos>> snapshot(Collection<VanillaRegionPos> regionPositions) throws IOException {
        Map<VanillaRegionPos, State<VanillaRegionPos>> newStates = new HashMap<>();
        if (this.currentStateIdx == NO_STATE) {
            // snapshot can go ahead with no checks
            for (VanillaRegionPos regionPos : regionPositions) {
                newStates.put(regionPos, externalStateForRegion(regionPos));
            }
            return newStates;
        } else {
            // snapshot must check against current state to warn user

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
    public boolean snapshotCurrentState() throws IOException {
        removeFutureStates();

        if (this.currentStateIdx == NO_STATE) {
            return false;
        }

        Map<VanillaRegionPos, State<VanillaRegionPos>> snapshot = snapshot(this.states.get(this.currentStateIdx).keySet());

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

    public boolean hasState() {
        return this.currentStateIdx != NO_STATE;
    }

    public boolean hasPreviousState() {
        return this.currentStateIdx > 0;
    }

    public Map<VanillaRegionPos, State<VanillaRegionPos>> previousState() {
        if (this.currentStateIdx == 0) {
            throw new ArrayIndexOutOfBoundsException("Tried to get previous state when none exists");
        }

        currentStateIdx--;
        return this.states.get(currentStateIdx);
    }

    public boolean hasNextState() {
        return this.currentStateIdx + 1 < this.states.size();
    }

    public Map<VanillaRegionPos, State<VanillaRegionPos>> nextState() {
        if (this.currentStateIdx + 1 >= this.states.size()) {
            throw new ArrayIndexOutOfBoundsException("Tried to get next state when none exists");
        }

        currentStateIdx++;
        return this.states.get(currentStateIdx);
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

    public int stateCount() {
        return this.states.size();
    }

    /**
     * Remove all header backups stored
     */
    public void removeAllStates() {
        this.states.clear();
        this.currentStateIdx = NO_STATE;
    }

    public long statesSizeBytes() {
        long[] bytes = new long[] { 0 }; //java is annoying
        for (Map<VanillaRegionPos, State<VanillaRegionPos>> states : this.states) {
            states.forEach((regionPos, state) -> bytes[0] += state.size());
        }
        return bytes[0];
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
