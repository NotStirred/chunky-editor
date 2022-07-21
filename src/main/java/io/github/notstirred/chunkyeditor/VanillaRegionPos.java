package io.github.notstirred.chunkyeditor;

import java.util.Objects;

public class VanillaRegionPos {
    public final int x;
    public final int z;

    public VanillaRegionPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public String fileName() {
        return String.format("r.%d.%d.mca", this.x, this.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VanillaRegionPos that = (VanillaRegionPos) o;
        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
