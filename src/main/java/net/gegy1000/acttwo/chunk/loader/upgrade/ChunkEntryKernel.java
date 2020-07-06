package net.gegy1000.acttwo.chunk.loader.upgrade;

import net.gegy1000.acttwo.chunk.ChunkMap;
import net.gegy1000.acttwo.chunk.entry.ChunkEntry;
import net.gegy1000.acttwo.chunk.entry.ChunkEntryState;
import net.gegy1000.acttwo.lock.JoinedRead;
import net.gegy1000.acttwo.lock.JoinedWrite;
import net.gegy1000.acttwo.lock.RwGuard;
import net.gegy1000.acttwo.lock.RwLock;
import net.gegy1000.justnow.Waker;
import net.gegy1000.justnow.future.Future;
import net.minecraft.util.Unit;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import javax.annotation.Nullable;
import java.util.function.Function;

final class ChunkEntryKernel {
    private final ChunkUpgradeKernel kernel;

    private boolean idle = true;

    private final RwLock<ChunkEntryState>[] writeLocks;
    private final RwLock<ChunkEntryState>[] readLocks;

    private final JoinedRead<ChunkEntryState> joinedRead;
    private final JoinedWrite<ChunkEntryState> joinedWrite;

    private final ChunkEntryState[] entries;

    private RwGuard<ChunkEntryState[]> writeEntries;
    private RwGuard<ChunkEntryState[]> readEntries;

    @SuppressWarnings("unchecked")
    ChunkEntryKernel(ChunkUpgradeKernel kernel) {
        this.kernel = kernel;

        int size = kernel.getSize();

        this.readLocks = new RwLock[size * size];
        this.writeLocks = new RwLock[size * size];

        this.entries = new ChunkEntryState[size * size];

        this.joinedRead = new JoinedRead<>(this.readLocks, this.entries);
        this.joinedWrite = new JoinedWrite<>(this.writeLocks, this.entries);
    }

    public boolean prepareWriters(ChunkMap chunks, ChunkPos pos, ChunkStatus targetStatus) {
        this.idle = false;

        int writeCount = 0;

        int centerX = pos.x;
        int centerZ = pos.z;
        int radius = this.kernel.getRadius();
        int size = this.kernel.getSize();

        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                ChunkEntry entry = chunks.getEntry(x + centerX, z + centerZ);
                if (entry == null) {
                    continue;
                }

                int idx = (x + radius) + (z + radius) * size;

                if (entry.canUpgradeTo(targetStatus)) {
                    this.writeLocks[idx] = entry.getState();
                    writeCount++;
                }
            }
        }

        return writeCount > 0;
    }

    // TODO: there is a lot of iteration here
    public void prepareReaders(ChunkMap chunks, ChunkPos pos, ChunkStatus targetStatus) {
        int radius = this.kernel.getRadius();
        int size = this.kernel.getSize();

        int taskMargin = targetStatus.getTaskMargin();
        if (taskMargin > 0) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    int idx = (x + radius) + (z + radius) * size;
                    if (this.writeLocks[idx] != null) {
                        this.addMargin(chunks, pos, x, z, taskMargin);
                    }
                }
            }
        }
    }

    private void addMargin(ChunkMap chunks, ChunkPos pos, int centerX, int centerZ, int margin) {
        RwLock<ChunkEntryState>[] writeLocks = this.writeLocks;

        int radius = this.kernel.getRadius();
        int size = this.kernel.getSize();

        int minX = Math.max(centerX - margin, -radius);
        int maxX = Math.min(centerX + margin, radius);
        int minZ = Math.max(centerZ - margin, -radius);
        int maxZ = Math.min(centerZ + margin, radius);

        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int idx = (x + radius) + (z + radius) * size;

                if (writeLocks[idx] == null) {
                    ChunkEntry entry = chunks.getEntry(x + pos.x, z + pos.z);
                    if (entry == null) {
                        continue;
                    }

                    this.readLocks[idx] = entry.getState();
                }
            }
        }
    }

    @Nullable
    public Unit pollEntries(Waker waker) {
        if (this.writeEntries == null) {
            this.writeEntries = this.joinedWrite.poll(waker);
        }

        if (this.readEntries == null) {
            this.readEntries = this.joinedRead.poll(waker);
        }

        if (this.writeEntries != null && this.readEntries != null) {
            return Unit.INSTANCE;
        } else {
            return null;
        }
    }

    public <T> void openWriteTasks(Future<T>[] tasks, Function<ChunkEntryState, Future<T>> function) {
        ChunkEntryState[] entries = this.entries;

        for (int i = 0; i < entries.length; i++) {
            ChunkEntryState entry = entries[i];
            if (entry == null) continue;

            if (this.writeLocks[i] != null) {
                tasks[i] = function.apply(entry);
            }
        }
    }

    public void release() {
        if (this.writeEntries != null) {
            this.writeEntries.release();
            this.writeEntries = null;
        }

        if (this.readEntries != null) {
            this.readEntries.release();
            this.readEntries = null;
        }

        this.idle = true;
    }

    public boolean isIdle() {
        return this.idle;
    }

    public int getRadius() {
        return this.kernel.getRadius();
    }

    public ChunkEntryState[] getEntries() {
        return this.entries;
    }
}
