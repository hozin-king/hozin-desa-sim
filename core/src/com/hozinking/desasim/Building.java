package com.hozinking.desasim;

/** Satu bangunan di peta. Posisi dalam koordinat dunia. */
public class Building {
    public BuildingType type;
    public float x;
    public float y;

    public Building() {}

    public Building(BuildingType type, float x, float y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }
}
