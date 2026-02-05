package com.idyl.snailman.pathfinder;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CollisionMap extends SplitFlagMap {
    public CollisionMap(int regionSize, Map<Position, byte[]> compressedRegions) {
        super(regionSize, compressedRegions, 2);
    }

    public boolean n(int x, int y, int z) {
        return this.get(x, y, z, 0);
    }

    public boolean s(int x, int y, int z) {
        return this.n(x, y - 1, z);
    }

    public boolean e(int x, int y, int z) {
        return this.get(x, y, z, 1);
    }

    public boolean w(int x, int y, int z) {
        return this.e(x - 1, y, z);
    }

    private boolean ne(int x, int y, int z) {
        return this.n(x, y, z) && this.e(x, y + 1, z) && this.e(x, y, z) && this.n(x + 1, y, z);
    }

    private boolean nw(int x, int y, int z) {
        return this.n(x, y, z) && this.w(x, y + 1, z) && this.w(x, y, z) && this.n(x - 1, y, z);
    }

    private boolean se(int x, int y, int z) {
        return this.s(x, y, z) && this.e(x, y - 1, z) && this.e(x, y, z) && this.s(x + 1, y, z);
    }

    private boolean sw(int x, int y, int z) {
        return this.s(x, y, z) && this.w(x, y - 1, z) && this.w(x, y, z) && this.s(x - 1, y, z);
    }

    public boolean isBlocked(int x, int y, int z) {
        return !this.n(x, y, z) && !this.s(x, y, z) && !this.e(x, y, z) && !this.w(x, y, z);
    }

    public List<WorldPoint> getNeighbors(WorldPoint position) {
        int x = position.getX();
        int y = position.getY();
        int z = position.getPlane();

        List<WorldPoint> neighbors = new ArrayList<>();
        boolean[] traversable = new boolean[]{
            this.w(x, y, z), this.e(x, y, z), this.s(x, y, z), this.n(x, y, z), this.sw(x, y, z), this.se(x, y, z), this.nw(x, y, z), this.ne(x, y, z)
        };

        for (int i = 0; i < traversable.length; i++) {
            if (traversable[i]) {
                OrdinalDirection direction = OrdinalDirection.values()[i];
                neighbors.add(position.dx(direction.x).dy(direction.y));
            }
        }

        return neighbors;
    }
}