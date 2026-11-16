package com.hozinking.desasim;

import java.util.Random;

/** Satu penduduk. Kebutuhan 0..100 (0 = kenyang/segar, 100 = lapar/haus/lelah). */
public class Villager {
    public enum Job { PETANI, PENCARI_AIR, PENEBANG, PENAMBANG, MENGANGGUR }

    public float x;
    public float y;
    public float tx;
    public float ty;
    public Job job = Job.MENGANGGUR;
    public int lapar;   // 0 kenyang — 100 kelaparan
    public int haus;    // 0 segar — 100 kehausan
    public int energi;  // 0 lelah — 100 bugar
    public float speed;

    public Villager() {}

    public Villager(float x, float y, Random rng) {
        this.x = x;
        this.y = y;
        this.tx = x;
        this.ty = y;
        this.lapar = rng.nextInt(40);
        this.haus = rng.nextInt(40);
        this.energi = 60 + rng.nextInt(40);
        this.speed = 22f + rng.nextFloat() * 18f;
    }

    /** Gerak visual: jalan ke target; kalau sampai, pilih target baru di dekat titik jangkar. */
    public void stepVisual(float dt, float anchorX, float anchorY, float range, Random rng) {
        float dx = tx - x;
        float dy = ty - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 4f) {
            tx = anchorX + (rng.nextFloat() * 2f - 1f) * range;
            ty = anchorY + (rng.nextFloat() * 2f - 1f) * range;
            return;
        }
        float step = speed * dt;
        if (step >= d) {
            x = tx;
            y = ty;
        } else {
            x += dx / d * step;
            y += dy / d * step;
        }
    }
}
