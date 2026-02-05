package com.idyl.snailman.pathfinder;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class FlagMap {
    public static final int PLANE_COUNT = 4;
    public final int minX;
    public final int minY;
    public final int maxX;
    public final int maxY;
    protected final BitSet flags;
    private final int width;
    private final int height;
    private final int flagCount;

    public FlagMap(int minX, int minY, int maxX, int maxY, int flagCount) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.flagCount = flagCount;
        this.width = (maxX - minX + 1);
        this.height = (maxY - minY + 1);
        this.flags = new BitSet(this.width * this.height * PLANE_COUNT * flagCount);
    }

    public FlagMap(byte[] bytes, int flagCount) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        this.minX = buffer.getInt();
        this.minY = buffer.getInt();
        this.maxX = buffer.getInt();
        this.maxY = buffer.getInt();
        this.flagCount = flagCount;
        this.width = (this.maxX - this.minX + 1);
        this.height = (this.maxY - this.minY + 1);
        this.flags = BitSet.valueOf(buffer);
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[16 + this.flags.size()];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(this.minX);
        buffer.putInt(this.minY);
        buffer.putInt(this.maxX);
        buffer.putInt(this.maxY);
        buffer.put(this.flags.toByteArray());
        return bytes;
    }

    public boolean get(int x, int y, int z, int flag) {
        if (x < this.minX || x > this.maxX || y < this.minY || y > this.maxY || z < 0 || z > PLANE_COUNT - 1) {
            return false;
        }

        return this.flags.get(this.index(x, y, z, flag));
    }

    public void set(int x, int y, int z, int flag, boolean value) {
        this.flags.set(this.index(x, y, z, flag), value);
    }

    private int index(int x, int y, int z, int flag) {
        if (x < this.minX || x > this.maxX || y < this.minY || y > this.maxY || z < 0 || z > PLANE_COUNT - 1 || flag < 0 || flag > this.flagCount - 1) {
            throw new IndexOutOfBoundsException(x + " " + y + " " + z);
        }

        return (z * this.width * this.height + (y - this.minY) * this.width + (x - this.minX)) * this.flagCount + flag;
    }
}