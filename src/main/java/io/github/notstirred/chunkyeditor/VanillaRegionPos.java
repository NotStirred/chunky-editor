package io.github.notstirred.chunkyeditor;

public record VanillaRegionPos(int x, int z) {
    public String fileName() {
        return String.format("r.%d.%d.mca", this.x, this.z);
    }
}
