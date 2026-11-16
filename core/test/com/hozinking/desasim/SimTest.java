package com.hozinking.desasim;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tes logika inti: ekonomi, kebutuhan penduduk, skor kemakmuran. */
public class SimTest {

    private Village village(boolean player) {
        Village v = new Village("Tes", player, 0, 900, 0, 1000, 42L);
        v.buildings.add(new Building(BuildingType.RUMAH, 100, 100));
        v.buildings.add(new Building(BuildingType.RUMAH, 200, 100));
        v.buildings.add(new Building(BuildingType.LADANG, 100, 300));
        v.buildings.add(new Building(BuildingType.SUMUR, 200, 300));
        for (int i = 0; i < 20; i++) v.addVillager();
        return v;
    }

    @Test
    public void petaniMenghasilkanMakananBilaAdaAir() {
        Village v = village(true);
        v.jobCounts[Villager.Job.PETANI.ordinal()] = 5;
        v.jobCounts[Villager.Job.PENCARI_AIR.ordinal()] = 5;
        int sebelum = v.makanan;
        v.makanan = 100; // stok awal agar konsumsi tidak menutupi produksi
        v.air = 100;
        v.dailyTick(1);
        // 5 petani x 6 = +30, konsumsi 20 penduduk = -20 -> bersih +10 dari 100
        assertEquals(110, v.makanan);
    }

    @Test
    public void ladangKeringHasilSeparuh() {
        Village v = village(true);
        v.jobCounts[Villager.Job.PETANI.ordinal()] = 5;
        v.jobCounts[Villager.Job.PENCARI_AIR.ordinal()] = 0;
        v.makanan = 1000;
        v.air = 0;
        v.dailyTick(1);
        // 5 petani x 6 x 0.5 = +15, konsumsi 20 -> 1000+15-20 = 995
        assertEquals(995, v.makanan);
    }

    @Test
    public void kekuranganMakananPendudukBerkurang() {
        Village v = village(true);
        v.makanan = 0;
        v.air = 0;
        int popSebelum = v.population();
        v.dailyTick(1);
        assertTrue(v.population() < popSebelum);
        assertTrue(v.population() >= 1);
    }

    @Test
    public void surplusDanRumahKosongPopulasiTumbuh() {
        Village v = village(true);
        v.makanan = 500;
        v.air = 500;
        int popSebelum = v.population();
        v.dailyTick(1);
        assertTrue(v.population() > popSebelum);
        assertTrue(v.population() <= v.housingCapacity());
    }

    @Test
    public void bangunRumahButuhKayuDanMenambahKapasitas() {
        Village v = village(true);
        v.kayu = 50;
        int capSebelum = v.housingCapacity();
        assertTrue(v.placeBuilding(BuildingType.RUMAH, 500, 500));
        assertEquals(40, v.kayu);
        assertEquals(capSebelum + 25, v.housingCapacity());
    }

    @Test
    public void bangunGagalBilaTidakMampu() {
        Village v = village(true);
        v.kayu = 0;
        v.emas = 0;
        assertFalse(v.placeBuilding(BuildingType.TAMBANG, 500, 500));
        assertEquals(0, v.countBuilding(BuildingType.TAMBANG));
    }

    @Test
    public void pasarMembukaJualBeli() {
        Village v = village(true);
        v.makanan = 50;
        assertFalse(v.sellFood()); // belum ada pasar
        v.buildings.add(new Building(BuildingType.PASAR, 400, 400));
        assertTrue(v.sellFood());
        assertEquals(40, v.makanan);
        assertEquals(3, v.emas);
    }

    @Test
    public void skorKemakmuranNaikBilaSumberDayaNaik() {
        Village v = village(true);
        int skorAwal = v.prosperity();
        v.emas += 10;
        v.makanan += 20;
        assertTrue(v.prosperity() > skorAwal);
    }

    @Test
    public void lomba20TahunBerakhir() {
        Sim sim = Sim.newGame();
        assertEquals(100, sim.player.population());
        assertEquals(100, sim.rival.population());
        // Percepat: jalankan 600 hari.
        sim.speed = 10;
        float dt = 0f;
        for (int i = 0; i < Sim.RACE_DAYS; i++) {
            sim.update(Sim.DAY_SECONDS_AT_1X / 10f); // 1 hari per langkah pada 10x
            dt += 0.2f;
        }
        assertTrue(sim.day >= Sim.RACE_DAYS);
        assertTrue(sim.raceOver);
        assertFalse(sim.winner.isEmpty());
    }

    @Test
    public void simpanDanMuatKembali() {
        Sim sim = Sim.newGame();
        sim.speed = 10;
        sim.update(2f);
        String json = sim.toJson();
        Sim loaded = Sim.fromJson(json);
        assertEquals(sim.day, loaded.day);
        assertEquals(sim.player.population(), loaded.player.population());
        assertEquals(sim.player.makanan, loaded.player.makanan);
        assertEquals(sim.rival.emas, loaded.rival.emas);
    }

    @Test
    public void kincirMenaikkanHasilLadang() {
        Village a = village(true);
        Village b = village(true);
        for (Village v : new Village[]{a, b}) {
            v.jobCounts[Villager.Job.PETANI.ordinal()] = 5;
            v.jobCounts[Villager.Job.PENCARI_AIR.ordinal()] = 5;
            v.makanan = 1000;
            v.air = 1000;
        }
        b.buildings.add(new Building(BuildingType.KINCIR, 400, 400));
        a.dailyTick(1);
        b.dailyTick(1);
        // b: 5x6x1.5=45, a: 5x6=30, konsumsi sama 20 -> b lebih banyak 15
        assertEquals(a.makanan + 15, b.makanan);
    }
}
