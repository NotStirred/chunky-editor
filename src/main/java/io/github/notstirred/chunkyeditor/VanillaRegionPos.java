package io.github.notstirred.chunkyeditor;

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
}
