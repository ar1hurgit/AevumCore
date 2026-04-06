package me.ar1hurgit.aevumcore.storage.data;

import java.util.UUID;

public class ClaimData {

    private final String id;
    private UUID owner;

    private String world;
    private int x1, y1, z1;
    private int x2, y2, z2;

    public ClaimData(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getZ1() { return z1; }

    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getZ2() { return z2; }
}