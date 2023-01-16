package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.state.State;
import io.github.notstirred.chunkyeditor.util.ExceptionUtils;
import se.llbit.util.Pair;
import se.llbit.util.annotation.NotNull;
import se.llbit.util.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Before any changes are made to the world, it should be checked against the current state to verify nothing has changed
 * If there are changes
 */
public class VanillaStateTracker {
    private static final int NO_STATE = -1;

    private final Path regionDirectory;

    private final List<StateGroup> states = new ArrayList<>();
    private int currentStateIdx = NO_STATE;

    public VanillaStateTracker(Path regionDirectory) {
        this.regionDirectory = regionDirectory;
    }

    private InternalState internalStateForRegion(VanillaRegionPos regionPos) throws IOException {
        Path regionPath = this.regionDirectory.resolve(regionPos.fileName());
        return new InternalState(regionPath);
    }
    private ExternalState externalStateForRegion(VanillaRegionPos regionPos) throws IOException {
        Path regionPath = this.regionDirectory.resolve(regionPos.fileName());
        return new ExternalState(regionPath);
    }

    /**
     * Can return the current state
     * @return Null if no previous external can be found for the region
     */
    @Nullable
    private ExternalState findPreviousExternalForRegion(VanillaRegionPos regionPos) {
        for (int i = currentStateIdx; i >= 0; i--) {
            State state = this.states.get(i).get(regionPos);
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
    private State findPreviousForRegion(VanillaRegionPos regionPos) {
        for (int i = currentStateIdx; i >= 0; i--) {
            State state = this.states.get(i).get(regionPos);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    /**
     * Attempt to take a snapshot of the requested regions.
     *
     * @param regionPositions Positions to snapshot
     * @return Null if no changes since the current snapshot
     */
    @NotNull
    private StateGroup snapshot(Collection<VanillaRegionPos> regionPositions) throws IOException {
        StateGroup newStates = new StateGroup();
        if (this.currentStateIdx == NO_STATE) {
            // snapshot can go ahead with no checks
            for (VanillaRegionPos regionPos : regionPositions) {
                newStates.put(regionPos, externalStateForRegion(regionPos));
            }
        } else {
            // snapshot must check against current state to warn user

            //TODO: could probably be timestamp based, but I've seen some external programs intentionally keep timestamps unchanged
            //      and so went with the super safe approach for now.
            for (VanillaRegionPos regionPos : regionPositions) {
                State previousAny = findPreviousForRegion(regionPos);
                ExternalState previousExternal = findPreviousExternalForRegion(regionPos);

                State newState = externalStateForRegion(regionPos);

                if (previousExternal != null && previousAny != null) {
                    boolean dataMatchesPrevious = previousExternal.dataMatches(newState);
                    if (dataMatchesPrevious) {
                        boolean headerMatchesCurrent = previousAny.headerMatches(newState);
                        if (!headerMatchesCurrent) { // only header differs? internal state
                            newState = internalStateForRegion(regionPos);
                        }
                    }
                }
                newStates.put(regionPos, newState);
            }
        }
        return newStates;
    }

    /**
     * Take a snapshot of the requested regions.
     * This method MUST attempt to snapshot every region, even if some regions fail.
     *
     * @param regionPositions Positions to snapshot
     * @return Null if no changes since the current snapshot
     */
    @NotNull
    private Pair<StateGroup, IOException> snapshotNoFail(Collection<VanillaRegionPos> regionPositions) {
        Collection<IOException> suppressedExceptions = new ArrayList<>();

        StateGroup states = new StateGroup();
        if (this.currentStateIdx == NO_STATE) {
            // snapshot can go ahead with no checks
            for (VanillaRegionPos regionPos : regionPositions) {
                try {
                    states.put(regionPos, externalStateForRegion(regionPos));
                } catch (IOException e) {
                    suppressedExceptions.add(e);
                    states.put(regionPos, null);
                }
            }
        } else {
            // snapshot must check against current state to warn user

            //TODO: could probably be timestamp based, but I've seen some external programs intentionally keep timestamps unchanged
            //      and so went with the super safe approach for now.
            for (VanillaRegionPos regionPos : regionPositions) {
                var previousAny = findPreviousForRegion(regionPos);
                var previousExternal = findPreviousExternalForRegion(regionPos);

                ExternalState externalState;
                try {
                    externalState = externalStateForRegion(regionPos);
                } catch (IOException e) {
                    suppressedExceptions.add(e);
                    continue; // we failed to snapshot this region, continue to the next ones.
                }

                if (previousExternal != null && previousAny != null) {
                    try {
                        boolean dataMatchesPrevious = previousExternal.dataMatches(externalState);
                        if (dataMatchesPrevious) {
                            boolean headerMatchesCurrent = previousAny.headerMatches(externalState);
                            if (!headerMatchesCurrent) { // only header differs? internal state
                                states.put(regionPos, externalState.asInternalState());
                                continue;
                            }
                        }
                    } catch (IOException e) {
                        suppressedExceptions.add(e);
                    }
                }
                states.put(regionPos, externalState);
            }
        }
        return new Pair<>(states, ExceptionUtils.chainSuppressedExceptions(suppressedExceptions));
    }

    /**
     * @param forcedRegions Positions to add to the snapshot, even if they don't differ
     */
    private void addToSnapshot(Collection<VanillaRegionPos> forcedRegions) throws IOException {
        if (this.currentStateIdx == NO_STATE) {
            throw new IllegalStateException("Trying to retake snapshot when none exists");
        }
        StateGroup states = this.states.get(this.currentStateIdx);

        // snapshot must check against current state to warn user

        //TODO: could probably be timestamp based, but I've seen some external programs intentionally keep timestamps unchanged
        //      and so went with the super safe approach for now.
        for (VanillaRegionPos regionPos : forcedRegions) {
            var previousAny = findPreviousForRegion(regionPos);
            var previousExternal = findPreviousExternalForRegion(regionPos);

            var externalState = externalStateForRegion(regionPos);

            if (previousExternal != null && previousAny != null) {
                boolean dataMatchesPrevious = previousExternal.dataMatches(externalState);
                // Region data matches, so we add an internal state instead
                if (dataMatchesPrevious) {
                    states.put(regionPos, externalState.asInternalState());
                    continue;
                }
            }
            states.put(regionPos, externalState);
        }
    }

    /**
     * Retake the current snapshot, including the provided regions if they were not already
     * @param additionalRegions Additional regions to include in the snapshot (if any)
     * @return True if a snapshot was taken (the current state differed from the new state)
     */
    public boolean snapshotCurrentState(Collection<VanillaRegionPos> additionalRegions) throws IOException {
        removeFutureStates();

        if (this.currentStateIdx == NO_STATE) {
            return false;
        }

        addToSnapshot(additionalRegions);

        return true;
    }

    /**
     * @return True if a snapshot was taken (the current state differed from the new state)
     */
    public boolean snapshotState(List<VanillaRegionPos> regionPositions) throws IOException {
        this.removeFutureStates();
        this.states.add(snapshot(regionPositions));
        this.currentStateIdx++;
        return true;
    }

    /**
     * @return True if a snapshot was taken (the current state differed from the new state)
     */
    public boolean snapshotStateNoFail(List<VanillaRegionPos> regionPositions) throws IOException {
        this.removeFutureStates();
        Pair<StateGroup, IOException> snapshot = snapshotNoFail(regionPositions);
        this.states.add(snapshot.thing1);
        this.currentStateIdx++;

        // rethrow any suppressed exceptions
        if (snapshot.thing2 != null)
            throw snapshot.thing2;

        return true;
    }

    public boolean hasState() {
        return this.currentStateIdx != NO_STATE;
    }

    public StateGroup currentState() {
        if (this.currentStateIdx == NO_STATE) {
            throw new IllegalStateException("Tried to get current state when none exists");
        }

        return this.states.get(currentStateIdx);
    }

    public boolean hasPreviousState() {
        return this.currentStateIdx > 0;
    }

    public StateGroup previousState() {
        if (this.currentStateIdx == 0) {
            throw new ArrayIndexOutOfBoundsException("Tried to get previous state when none exists");
        }

        currentStateIdx--;
        return this.states.get(currentStateIdx);
    }

    public boolean hasNextState() {
        return this.currentStateIdx + 1 < this.states.size();
    }

    public StateGroup nextState() {
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
        this.states.forEach(StateGroup::release);
        this.states.clear();
        this.currentStateIdx = NO_STATE;
    }

    public long statesSizeBytes() {
        long[] bytes = new long[] { 0 }; //java is annoying
        for (StateGroup stateGroups : this.states) {
            stateGroups.getStates().forEach((regionPos, state) -> bytes[0] += state.size());
        }
        return bytes[0];
    }

    public long statesDiskSizeBytes() {
        long[] bytes = new long[] { 0 };
        for (StateGroup stateGroups : this.states) {
            stateGroups.getStates().forEach((regionPos, state) -> bytes[0] += state.onDiskSize());
        }
        return bytes[0];
    }

    public static class StateGroup {
        private final Map<VanillaRegionPos, State> states = new HashMap<>();

        private void put(VanillaRegionPos pos, State state) {
            this.states.put(pos, state);
            if (state != null) state.allowToDisk();
        }

        public State get(VanillaRegionPos pos) {
            return this.states.get(pos);
        }

        public boolean hasExternal() {
            for (Map.Entry<VanillaRegionPos, State> entry : this.states.entrySet()) {
                VanillaRegionPos pos = entry.getKey();
                State state = entry.getValue();
                if (!state.isInternal()) {
                    return true;
                }
            }
            return false;
        }

        public Map<VanillaRegionPos, State> getStates() {
            return Collections.unmodifiableMap(this.states);
        }

        public void release() {
            states.values().forEach(State::release);
            states.clear();
        }
    }
}
