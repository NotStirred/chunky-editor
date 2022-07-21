package io.github.notstirred.chunkyeditor.minecraft;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import se.llbit.fxutil.Dialogs;
import se.llbit.util.annotation.NotNull;
import se.llbit.util.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class WorldLock {
    @NotNull
    private final File lockFile;

    /**
     * The time the lock was validated. If the lock timestamp is after this then minecraft has opened the world.
     */
    @Nullable
    private ZonedDateTime validTime = null;

    private WorldLock(@NotNull File lockFile) {
        this.lockFile = lockFile;
    }

    private boolean isValid() {
        if (this.validTime == null) {
            return false;
        }

        ZonedDateTime lockTimestamp = getLockTimestamp(this.lockFile);

        return this.validTime.isAfter(lockTimestamp);
    }

    /**
     * Check if the file is untouched, ask the user for confirmation if it has been modified (world has been opened externally)
     *
     * @return True if locked, false if not.
     */
    public boolean tryLock() {
        if (isValid()) {
            return true;
        }

        if (!getUserConfirmation()) {
            return false;
        }

        this.validTime = ZonedDateTime.now();

        return true;
    }

    private boolean getUserConfirmation() {
        Dialog<ButtonType> confirmationDialog = Dialogs.createSpecialApprovalConfirmation(
                "World may be open in Minecraft",
                "It looks like your world might be open in minecraft",
                "Do you really want to allow chunky to modify your world?\nIf the world is open in minecraft, chunky WILL break your world.\nBe sure to have a backup!",
                "I DO NOT have this world open in Minecraft"
        );

        return confirmationDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @NotNull
    private static ZonedDateTime getLockTimestamp(@NotNull File lockFile) {
        long lastModified = lockFile.lastModified();

        ZonedDateTime timeNow = ZonedDateTime.now();
        int offset = timeNow.getOffset().getTotalSeconds();

        return ZonedDateTime.of(
                LocalDateTime.ofEpochSecond(lastModified / 1000, 0, ZoneOffset.ofTotalSeconds(offset)),
                timeNow.getZone()
        );
    }

    public static WorldLock of(@NotNull Path worldPath) throws FileNotFoundException {
        Path sessionLockFile = worldPath.resolve("session.lock");

        File lockFile = sessionLockFile.toFile();
        if (!lockFile.exists()) {
            throw new FileNotFoundException();
        }

//        long lastModified = new File("/home/tom/Downloads/MultiMC/bin/instances/1.12.2/.minecraft/saves/New World/session.lock").lastModified();
        return new WorldLock(lockFile);
    }
}
