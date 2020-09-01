package net.gegy1000.tictacs.chunk.tracker;

import net.minecraft.util.math.ChunkPos;

import java.util.function.LongConsumer;

final class ChunkTrackView {
    final int minX;
    final int minZ;
    final int maxX;
    final int maxZ;

    ChunkTrackView(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public static ChunkTrackView withRadius(int x, int z, int radius) {
        return new ChunkTrackView(x - radius, z - radius, x + radius, z + radius);
    }

    public void forEach(LongConsumer consumer) {
        forEach(
                this.minX, this.minZ,
                this.maxX, this.maxZ,
                consumer
        );
    }

    public void forEachDifference(ChunkTrackView other, LongConsumer consumer) {
        if (this.equals(other)) {
            return;
        }

        if (!this.intersects(other)) {
            this.forEach(consumer);
            return;
        }

        boolean tl = this.contains(other.minX, other.minZ);
        boolean tr = this.contains(other.maxX, other.minZ);
        boolean bl = this.contains(other.minX, other.maxZ);
        boolean br = this.contains(other.maxX, other.maxZ);

        // corners
        if (tl) forEach(this.minX, this.minZ, other.minX - 1, other.minZ - 1, consumer);
        if (tr) forEach(other.maxX + 1, this.minZ, this.maxX, other.minZ - 1, consumer);
        if (bl) forEach(this.minX, other.maxZ + 1, other.minX - 1, this.maxZ, consumer);
        if (br) forEach(other.maxX + 1, other.maxZ + 1, this.maxX, this.maxZ, consumer);

        // edges
        if (tl || tr) {
            forEach(
                    Math.max(other.minX - 1, this.minX), this.minZ,
                    Math.min(other.maxX + 1, this.maxX), other.minZ - 1,
                    consumer
            );
        }

        if (bl || br) {
            forEach(
                    Math.max(other.minX - 1, this.minX), other.maxZ + 1,
                    Math.min(other.maxX + 1, this.maxX), this.maxZ,
                    consumer
            );
        }

        if (tl || bl) {
            forEach(
                    this.minX, Math.max(other.minZ - 1, this.minZ),
                    other.minX - 1, Math.min(other.maxZ + 1, this.maxZ),
                    consumer
            );
        }

        if (tr || br) {
            forEach(
                    other.maxX + 1, Math.max(other.minZ - 1, this.minZ),
                    this.maxX, Math.min(other.maxZ + 1, this.maxZ),
                    consumer
            );
        }
    }

    private boolean contains(int x, int z) {
        return x >= this.minX && z >= this.minZ && x <= this.maxX && z <= this.maxZ;
    }

    public boolean intersects(ChunkTrackView view) {
        return this.minX < view.maxX && this.maxX > view.minX && this.minZ < view.maxZ && this.maxZ > view.minZ;
    }

    private static void forEach(int minX, int minZ, int maxX, int maxZ, LongConsumer consumer) {
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                consumer.accept(ChunkPos.toLong(x, z));
            }
        }
    }

    public boolean equals(ChunkTrackView other) {
        return this.minX == other.minX && this.minZ == other.minZ && this.maxX == other.maxX && this.maxZ == other.maxZ;
    }
}
