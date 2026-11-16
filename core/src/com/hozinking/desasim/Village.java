package com.hozinking.desasim;

import java.util.ArrayList;
import java.util.Random;

/**
 * Satu desa: sumber daya, bangunan, penduduk, pekerjaan, dan kebijakan.
 * Semua angka diproduksi/dikonsumsi per HARI lewat dailyTick().
 * isPlayer=false = desa lawan yang dikendalikan AI boros.
 */
public class Village {
    public String name;
    public boolean isPlayer;
    public float zoneX0;
    public float zoneX1;
    public float zoneY0;
    public float zoneY1;

    public int air;
    public int makanan;
    public int kayu;
    public int emas;
    public int happiness = 50;
    public int pestaDaysLeft = 0;

    public ArrayList<Building> buildings = new ArrayList<>();
    public ArrayList<Villager> villagers = new ArrayList<>();
    /** jobCounts[Job.ordinal()] — MENGANGGUR dihitung sebagai sisa populasi. */
    public int[] jobCounts = new int[Villager.Job.values().length];
    public ArrayList<Integer> scoreHistory = new ArrayList<>();

    public transient Random rng = new Random();

    public Village() {}

    public Village(String name, boolean isPlayer, float x0, float x1, float y0, float y1, long seed) {
        this.name = name;
        this.isPlayer = isPlayer;
        this.zoneX0 = x0;
        this.zoneX1 = x1;
        this.zoneY0 = y0;
        this.zoneY1 = y1;
        this.rng = new Random(seed);
    }

    // ---------- info turunan ----------

    public int population() {
        return villagers.size();
    }

    public int countBuilding(BuildingType type) {
        int n = 0;
        for (Building b : buildings) if (b.type == type) n++;
        return n;
    }

    public int housingCapacity() {
        return countBuilding(BuildingType.RUMAH) * 25;
    }

    /** Pekerja efektif: dibatasi kapasitas bangunan pendukung. */
    public int effectiveWorkers(Villager.Job job, BuildingType support) {
        int cap = countBuilding(support) * support.workerCap;
        return Math.min(jobCounts[job.ordinal()], cap);
    }

    public int idleCount() {
        int used = 0;
        for (Villager.Job j : Villager.Job.values()) {
            if (j != Villager.Job.MENGANGGUR) used += jobCounts[j.ordinal()];
        }
        return Math.max(0, population() - used);
    }

    public int prosperity() {
        return population() * 2 + makanan + air + kayu / 2 + emas * 3
                + buildings.size() * 5 + happiness;
    }

    public boolean canAfford(BuildingType type) {
        return kayu >= type.kayuCost && emas >= type.emasCost;
    }

    // ---------- aksi pemain ----------

    /** Pindah 1 penduduk ke pekerjaan (delta +1) atau kembali menganggur (-1). */
    public boolean assignJob(Villager.Job job, int delta) {
        if (job == Villager.Job.MENGANGGUR) return false;
        int cur = jobCounts[job.ordinal()];
        if (delta > 0) {
            if (idleCount() <= 0) return false;
            jobCounts[job.ordinal()] = cur + 1;
        } else {
            if (cur <= 0) return false;
            jobCounts[job.ordinal()] = cur - 1;
        }
        syncVillagerJobs();
        return true;
    }

    public boolean placeBuilding(BuildingType type, float x, float y) {
        if (!canAfford(type)) return false;
        if (x < zoneX0 + 30 || x > zoneX1 - 30 || y < zoneY0 + 30 || y > zoneY1 - 30) return false;
        for (Building b : buildings) {
            float dx = b.x - x, dy = b.y - y;
            if (dx * dx + dy * dy < 70f * 70f) return false; // terlalu dekat bangunan lain
        }
        kayu -= type.kayuCost;
        emas -= type.emasCost;
        buildings.add(new Building(type, x, y));
        return true;
    }

    // Jual-beli di pasar (butuh bangunan PASAR).
    public boolean sellFood() { // 10 makanan -> 3 emas
        if (countBuilding(BuildingType.PASAR) == 0 || makanan < 10) return false;
        makanan -= 10;
        emas += 3;
        return true;
    }

    public boolean sellWood() { // 10 kayu -> 2 emas
        if (countBuilding(BuildingType.PASAR) == 0 || kayu < 10) return false;
        kayu -= 10;
        emas += 2;
        return true;
    }

    public boolean buyFood() { // 5 emas -> 10 makanan
        if (countBuilding(BuildingType.PASAR) == 0 || emas < 5) return false;
        emas -= 5;
        makanan += 10;
        return true;
    }

    // ---------- simulasi harian ----------

    public void dailyTick(int day) {
        if (!isPlayer) rivalPolicy(day);

        int ladang = countBuilding(BuildingType.LADANG);
        int sumur = countBuilding(BuildingType.SUMUR);
        int tambang = countBuilding(BuildingType.TAMBANG);
        boolean kincir = countBuilding(BuildingType.KINCIR) > 0;

        int petani = effectiveWorkers(Villager.Job.PETANI, BuildingType.LADANG);
        int pencariAir = effectiveWorkers(Villager.Job.PENCARI_AIR, BuildingType.SUMUR);
        int penebang = jobCounts[Villager.Job.PENEBANG.ordinal()];
        int penambang = effectiveWorkers(Villager.Job.PENAMBANG, BuildingType.TAMBANG);

        // Produksi. Ladang butuh 2 air/hari per ladang — kalau kurang, hasil separuh.
        int airButuhLadang = ladang * 2;
        int airHasil = pencariAir * 8;
        boolean ladangKering = (air + airHasil) < airButuhLadang;
        float hasilPetani = petani * 6f * (ladangKering ? 0.5f : 1f) * (kincir ? 1.5f : 1f);
        makanan += Math.round(hasilPetani);
        air += airHasil;
        kayu += penebang * 3;
        emas += penambang;
        air = Math.max(0, air - airButuhLadang);

        // Konsumsi: 1 makanan + 1 air per penduduk per hari.
        int pop = population();
        int makanKurang = Math.max(0, pop - makanan);
        int airKurang = Math.max(0, pop - air);
        makanan = Math.max(0, makanan - pop);
        air = Math.max(0, air - pop);

        // Kelaparan / kehausan -> kematian.
        if (makanKurang > 0 || airKurang > 0) {
            int mati = Math.min(pop - 1, 1 + (makanKurang + airKurang) / 10);
            for (int i = 0; i < mati && !villagers.isEmpty(); i++) {
                villagers.remove(rng.nextInt(villagers.size()));
            }
            happiness -= 20;
            clampJobsToPopulation();
            syncVillagerJobs();
        } else {
            happiness += 2;
        }

        // Kelahiran bila surplus + masih ada kapasitas rumah.
        pop = population();
        if (makanKurang == 0 && airKurang == 0 && pop < housingCapacity() && pop < 200) {
            int lahir = Math.min(3, Math.max(1, pop / 50));
            lahir = Math.min(lahir, housingCapacity() - pop);
            for (int i = 0; i < lahir; i++) addVillager();
            syncVillagerJobs();
        }

        if (population() > housingCapacity()) happiness -= 5;
        if (pestaDaysLeft > 0) {
            happiness += 3;
            pestaDaysLeft--;
        }
        happiness = Math.max(0, Math.min(100, happiness));

        // Kapasitas simpan (lumbung): stok berlebih terbuang — skor tidak bisa
        // dimenangkan cuma dengan menimbun.
        makanan = Math.min(makanan, 2000);
        air = Math.min(air, 2000);
        kayu = Math.min(kayu, 5000);

        // Kebutuhan tiap penduduk ikut kondisi desa (untuk panel info).
        for (Villager v : villagers) {
            v.lapar = clamp(v.lapar + (makanKurang > 0 ? 12 : -8), 0, 100);
            v.haus = clamp(v.haus + (airKurang > 0 ? 12 : -8), 0, 100);
            v.energi = clamp(v.energi + (makanKurang > 0 || airKurang > 0 ? -10 : 5), 0, 100);
        }

        scoreHistory.add(prosperity());
        if (scoreHistory.size() > 600) scoreHistory.remove(0);
    }

    /** AI desa lawan: kaya tapi boros — banyak penambang, pesta tiap 10 hari, jual emas murah. */
    private void rivalPolicy(int day) {
        int pop = population();
        // Target campuran: 35% petani, 20% air, 15% kayu, 20% tambang (bila ada tambang).
        int targetTambang = countBuilding(BuildingType.TAMBANG) > 0 ? pop * 20 / 100 : 0;
        int targetPetani = pop * 35 / 100;
        int targetAir = pop * 20 / 100;
        int targetKayu = pop * 15 / 100;
        setJobCount(Villager.Job.PENAMBANG, targetTambang);
        setJobCount(Villager.Job.PETANI, targetPetani);
        setJobCount(Villager.Job.PENCARI_AIR, targetAir);
        setJobCount(Villager.Job.PENEBANG, targetKayu);

        // Pesta tiap 10 hari: boros makanan + emas demi kebahagiaan sesaat.
        if (day % 10 == 0 && makanan >= 20 && emas >= 10) {
            makanan -= 20;
            emas -= 10;
            happiness = Math.min(100, happiness + 15);
            pestaDaysLeft = 3;
        }
        // Kehabisan makanan -> jual emas murah (10 emas -> 12 makanan), bukan bertani.
        if (makanan < 10 && emas >= 10) {
            emas -= 10;
            makanan += 12;
        }
        // Bangun otomatis bila kaya, sesuai kebutuhan — JANGAN ladang terus tanpa air.
        int airCapacity = countBuilding(BuildingType.SUMUR) * 5 * 8;
        int airNeed = population() + countBuilding(BuildingType.LADANG) * 2;
        if (kayu >= 25 && emas >= 10 && countBuilding(BuildingType.TAMBANG) == 0) {
            autoPlace(BuildingType.TAMBANG);
        } else if (kayu >= 6 && airCapacity < airNeed + 20) {
            autoPlace(BuildingType.SUMUR);
        } else if (kayu >= 10 && housingCapacity() < population() + 25) {
            autoPlace(BuildingType.RUMAH);
        } else if (kayu >= 25 && rng.nextFloat() < 0.3f) {
            autoPlace(rng.nextFloat() < 0.5f ? BuildingType.RUMAH : BuildingType.LADANG);
        }
    }

    private void autoPlace(BuildingType type) {
        if (!canAfford(type)) return;
        for (int attempt = 0; attempt < 8; attempt++) {
            float x = zoneX0 + 40 + rng.nextFloat() * (zoneX1 - zoneX0 - 80);
            float y = zoneY0 + 40 + rng.nextFloat() * (zoneY1 - zoneY0 - 80);
            if (placeBuilding(type, x, y)) return;
        }
    }

    // ---------- util ----------

    private void setJobCount(Villager.Job job, int target) {
        int totalOthers = 0;
        for (Villager.Job j : Villager.Job.values()) {
            if (j != Villager.Job.MENGANGGUR && j != job) totalOthers += jobCounts[j.ordinal()];
        }
        int maxAllowed = Math.max(0, population() - totalOthers);
        jobCounts[job.ordinal()] = Math.max(0, Math.min(target, maxAllowed));
        syncVillagerJobs();
    }

    private void clampJobsToPopulation() {
        int pop = population();
        int used = 0;
        for (Villager.Job j : Villager.Job.values()) {
            if (j == Villager.Job.MENGANGGUR) continue;
            int cur = jobCounts[j.ordinal()];
            int allowed = Math.max(0, pop - used);
            jobCounts[j.ordinal()] = Math.min(cur, allowed);
            used += jobCounts[j.ordinal()];
        }
    }

    /** Sebar pekerjaan ke penduduk (visual): N penduduk pertama per pekerjaan. */
    public void syncVillagerJobs() {
        int idx = 0;
        for (Villager.Job j : Villager.Job.values()) {
            if (j == Villager.Job.MENGANGGUR) continue;
            for (int i = 0; i < jobCounts[j.ordinal()] && idx < villagers.size(); i++, idx++) {
                villagers.get(idx).job = j;
            }
        }
        for (; idx < villagers.size(); idx++) villagers.get(idx).job = Villager.Job.MENGANGGUR;
    }

    public void addVillager() {
        float x = zoneX0 + 40 + rng.nextFloat() * (zoneX1 - zoneX0 - 80);
        float y = zoneY0 + 40 + rng.nextFloat() * (zoneY1 - zoneY0 - 80);
        villagers.add(new Villager(x, y, rng));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
