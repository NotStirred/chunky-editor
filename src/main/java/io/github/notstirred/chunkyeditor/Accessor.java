package io.github.notstirred.chunkyeditor;

import se.llbit.chunky.world.Chunk;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.region.MCRegion;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Accessor {
    private static final Method MCRegion$setChunk = findMethod(MCRegion.class, "setChunk", ChunkPosition.class, Chunk.class);

    public static void invoke_MCRegion$setChunk(MCRegion region, ChunkPosition pos, Chunk chunk) {
        try {
            MCRegion$setChunk.invoke(region, pos, chunk);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... params) {
        Method method;
        try {
            method = clazz.getDeclaredMethod(methodName, params);
            method.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return method;
    }
}
