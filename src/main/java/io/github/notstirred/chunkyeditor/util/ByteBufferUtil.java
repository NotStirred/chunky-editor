package io.github.notstirred.chunkyeditor.util;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class ByteBufferUtil {
    private ByteBufferUtil() {}

    public static boolean equals(ByteBuffer a, ByteBuffer b) {
        // Reset the position to 0, this dosen't actually erase the data
        a.clear();
        b.clear();
        return Objects.equals(a, b);
    }

    public static boolean equalsRegion(ByteBuffer a, ByteBuffer b, int fromIndex, int toIndex) {
        if (a.hasArray() && b.hasArray()) {
            return Arrays.equals(a.array(), fromIndex, toIndex, b.array(), fromIndex, toIndex);
        } else {
            byte[] destA = new byte[toIndex - fromIndex];
            byte[] destB = new byte[toIndex - fromIndex];
            a.get(fromIndex, destA);
            b.get(fromIndex, destB);
            return Arrays.equals(destA, destB);
        }
    }

    public static boolean equalsRegion(ByteBuffer a, byte[] b, int fromIndex, int toIndex) {
        if (a.hasArray()) {
            return Arrays.equals(a.array(), fromIndex, toIndex, b, fromIndex, toIndex);
        } else {
            byte[] destA = new byte[toIndex - fromIndex];
            a.get(fromIndex, destA);
            return Arrays.equals(destA, 0, destA.length, b, fromIndex, toIndex);
        }
    }
}
