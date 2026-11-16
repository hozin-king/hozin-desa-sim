package com.hozinking.desasim;

import com.badlogic.gdx.utils.Json;

/**
 * Jam simulasi + dua desa + lomba kemakmuran 20 tahun.
 * 1 tahun = 30 hari; 1 hari = 2 detik pada kecepatan 1x.
 */
public class Sim {
    public static final int DAYS_PER_YEAR = 30;
    public static final int RACE_YEARS = 20;
    public static final int RACE_DAYS = DAYS_PER_YEAR * RACE_YEARS; // 600 hari
    public static final float DAY_SECONDS_AT_1X = 2f;
    public static final float WORLD_W = 2200f;
    public static final float WORLD_H = 1200f;

    public int day = 0;
    public float dayFrac = 0f; // 0..1 — juga dipakai sebagai jam siang/malam
    public int speed = 1;      // 0 = pause, 1 / 5 / 10
    public boolean raceOver = false;
    public String winner = "";

    public Village player;
    public Village rival;

    public Sim() {}

    public static Sim newGame() {
        Sim sim = new Sim();
        // Zona: pemain kiri, lawan kanan; tengah jalur netral.
        sim.player = new Village("Karangsepi", true, 60, 980, 90, 1110, 11L);
        sim.rival = new Village("Limpahraya", false, 1220, 2140, 90, 1110, 22L);
        setupVillage(sim.player, false);
        setupVillage(sim.rival, true);
        return sim;
    }

    /** Keadaan awal: pemain miskin-pintar (sedikit, tanpa tambang/pasar); lawan kaya-boros. */
    private static void setupVillage(Village v, boolean rich) {
        float cx = (v.zoneX0 + v.zoneX1) / 2f;
        // Rumah baris y=300, ladang y=950, sumur y=500 — jarak antar bangunan >= 120.
        float[] rumahX = rich
                ? new float[]{-300, -180, -60, 60, 180, 300}
                : new float[]{-240, -80, 80, 240};
        for (float dx : rumahX) v.buildings.add(new Building(BuildingType.RUMAH, cx + dx, 300));
        float[] ladangX = {-300, -100, 100, 300};
        for (float dx : ladangX) v.buildings.add(new Building(BuildingType.LADANG, cx + dx, 950));
        float[] sumurX = {-200, 0, 200};
        for (float dx : sumurX) v.buildings.add(new Building(BuildingType.SUMUR, cx + dx, 500));
        v.kayu = rich ? 50 : 30;
        v.makanan = rich ? 60 : 40;
        v.air = rich ? 40 : 30;
        v.emas = rich ? 40 : 0;
        if (rich) {
            v.buildings.add(new Building(BuildingType.TAMBANG, cx + 300, 500));
            v.buildings.add(new Building(BuildingType.PASAR, cx - 300, 500));
        }
        for (int i = 0; i < 100; i++) v.addVillager();
        // Pekerjaan awal pemain: 20 petani, 15 air, 10 kayu, sisanya menganggur.
        if (v.isPlayer) {
            v.jobCounts[Villager.Job.PETANI.ordinal()] = 20;
            v.jobCounts[Villager.Job.PENCARI_AIR.ordinal()] = 15;
            v.jobCounts[Villager.Job.PENEBANG.ordinal()] = 10;
        }
        v.syncVillagerJobs();
        v.scoreHistory.add(v.prosperity());
    }

    public int year() {
        return day / DAYS_PER_YEAR + 1;
    }

    public int dayOfYear() {
        return day % DAYS_PER_YEAR + 1;
    }

    /** 0..1 — 0.25..0.75 siang, sisanya malam. */
    public float timeOfDay() {
        return dayFrac;
    }

    public boolean isDaytime() {
        return dayFrac >= 0.25f && dayFrac < 0.75f;
    }

    public void update(float dt) {
        if (speed <= 0 || raceOver) return;
        dayFrac += dt / DAY_SECONDS_AT_1X * speed;
        while (dayFrac >= 1f) {
            dayFrac -= 1f;
            day++;
            player.dailyTick(day);
            rival.dailyTick(day);
            if (day >= RACE_DAYS) {
                raceOver = true;
                int p = player.prosperity();
                int r = rival.prosperity();
                winner = p > r ? player.name : (r > p ? rival.name : "Seri");
            }
        }
    }

    // ---------- save / load ----------

    public String toJson() {
        return new Json().toJson(this);
    }

    public static Sim fromJson(String json) {
        Sim sim = new Json().fromJson(Sim.class, json);
        if (sim.player != null) {
            sim.player.rng = new java.util.Random();
            sim.player.syncVillagerJobs();
        }
        if (sim.rival != null) {
            sim.rival.rng = new java.util.Random();
            sim.rival.syncVillagerJobs();
        }
        return sim;
    }
}
