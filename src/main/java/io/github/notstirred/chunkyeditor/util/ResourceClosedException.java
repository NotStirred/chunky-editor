package io.github.notstirred.chunkyeditor.util;

import java.io.IOException;

public class ResourceClosedException extends IOException {
    public ResourceClosedException() {
        super();
    }

    public ResourceClosedException(String message) {
        super(message);
    }

    public ResourceClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceClosedException(Throwable cause) {
        super(cause);
    }
}
