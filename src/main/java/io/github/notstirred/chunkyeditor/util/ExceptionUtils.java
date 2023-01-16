package io.github.notstirred.chunkyeditor.util;

import java.util.Collection;

public class ExceptionUtils {
    public static <T extends Exception> T chainSuppressedExceptions(Collection<? extends T> causes) {
        T first = null;
        for (T cause : causes) {
            if (first == null) {
                first = cause;
            } else {
                first.addSuppressed(cause);
            }
        }
        return first;
    }
}
