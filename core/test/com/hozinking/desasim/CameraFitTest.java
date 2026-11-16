package com.hozinking.desasim;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tes regresi bug v1.0 (laporan tes POCO 19:44-19:46): peta kedip-kedip dan
 * lari ke pojok saat zoom-out. Akar: clamp kamera lama memakai
 * MathUtils.clamp(pos, vw, WORLD_W - vw) — begitu zoom > 1.0, vw > WORLD_W/2
 * sehingga min > max dan kamera dijepit ke pojok lalu melompat tiap frame.
 */
public class CameraFitTest {

    @Test
    public void fitZoomMuatSeluruhPetaDiLayarPOCO() {
        // POCO X6 Pro 2712x1220 landscape: viewport 2200 x ~990.
        float viewportH = 2200f * 1220f / 2712f;
        float z = DesaSimGame.fitZoom(2200f, viewportH);
        assertEquals(1200f / viewportH, z, 0.001f);
        // Seluruh peta (2200x1200) muat: lebar DAN tinggi terlihat >= dunia.
        assertTrue(2200f * z >= 2200f);
        assertTrue(viewportH * z >= 1200f);
    }

    @Test
    public void fitZoomLayarPasDanSempit() {
        assertEquals(1f, DesaSimGame.fitZoom(2200f, 1200f), 0.001f);
        assertEquals(2f, DesaSimGame.fitZoom(2200f, 600f), 0.001f);
    }

    @Test
    public void clampAxisTengahSaatZoomOutMelewatiBatas() {
        // v1.0: clamp(1100, 1980, 220-1980) -> 1980 (pojok). v1.1: tengah dunia.
        assertEquals(1100f, DesaSimGame.clampAxis(1100f, 1980f, 2200f), 0.001f);
        assertEquals(1100f, DesaSimGame.clampAxis(220f, 1980f, 2200f), 0.001f);
        assertEquals(1100f, DesaSimGame.clampAxis(2000f, 1980f, 2200f), 0.001f);
        // Batas tepat: separuh terlihat == separuh dunia -> tetap tengah.
        assertEquals(1100f, DesaSimGame.clampAxis(500f, 1100f, 2200f), 0.001f);
    }

    @Test
    public void clampAxisJepitNormalSaatZoomIn() {
        assertEquals(500f, DesaSimGame.clampAxis(100f, 500f, 2200f), 0.001f);
        assertEquals(1700f, DesaSimGame.clampAxis(2000f, 500f, 2200f), 0.001f);
        assertEquals(1100f, DesaSimGame.clampAxis(1100f, 500f, 2200f), 0.001f);
        assertEquals(400f, DesaSimGame.clampAxis(50f, 400f, 1200f), 0.001f);
        assertEquals(800f, DesaSimGame.clampAxis(1150f, 400f, 1200f), 0.001f);
    }
}
