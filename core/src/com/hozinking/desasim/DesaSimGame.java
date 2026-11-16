package com.hozinking.desasim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

/**
 * Hozin Desa Sim v1.0 — simulasi desa 2D top-down, gaya flat modern (bukan pixel art).
 * Pemain mengatur desa Karangsepi; desa Limpahraya jalan sendiri (AI boros).
 * Lomba kemakmuran 20 tahun (600 hari); setelah itu bisa lanjut tanpa akhir.
 */
public class DesaSimGame extends ApplicationAdapter implements GestureDetector.GestureListener {
    private static final float WORLD_W = Sim.WORLD_W;
    private static final float WORLD_H = Sim.WORLD_H;
    private static final String PREFS = "desasim";
    private static final String SAVE_KEY = "save";

    private OrthographicCamera cam;
    private Matrix4 hudMatrix = new Matrix4(); // proyeksi layar (piksel) untuk HUD
    private float zoomFit = 1f; // zoom yang membuat seluruh peta muat dalam satu layar
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont fontHud;
    private BitmapFont fontSmall;
    private Sim sim;
    private Preferences prefs;
    private Random rng = new Random(7L);

    private BuildingType buildMode = null;
    private Villager selectedVillager = null;
    private Building selectedBuilding = null;
    private String toast = "";
    private float toastTimer = 0f;
    private float bladeAngle = 0f;
    private boolean raceDialogLaidOut = false;

    // Pohon + batu dekoratif (posisi tetap, seed sama tiap run).
    private Array<Float> treeX = new Array<>();
    private Array<Float> treeY = new Array<>();

    // ---------- tombol HUD (koordinat layar, y dari ATAS) ----------
    private static class Btn {
        float x, top, w, h;
        String label;
        int action;

        Btn(float x, float top, float w, float h, String label, int action) {
            this.x = x; this.top = top; this.w = w; this.h = h; this.label = label; this.action = action;
        }

        boolean contains(float sx, float sy) {
            return sx >= x && sx <= x + w && sy >= top && sy <= top + h;
        }
    }

    private Array<Btn> buttons = new Array<>();

    // action id
    private static final int A_PAUSE = 1, A_SPEED1 = 2, A_SPEED5 = 3, A_SPEED10 = 4;
    private static final int A_SAVE = 5, A_NEW = 6, A_CONTINUE = 7, A_RESTART = 8;
    private static final int A_BUILD_RUMAH = 10, A_BUILD_LADANG = 11, A_BUILD_SUMUR = 12;
    private static final int A_BUILD_KINCIR = 13, A_BUILD_TAMBANG = 14, A_BUILD_PASAR = 15;
    private static final int A_JOB_PETANI_MINUS = 20, A_JOB_PETANI_PLUS = 21;
    private static final int A_JOB_AIR_MINUS = 22, A_JOB_AIR_PLUS = 23;
    private static final int A_JOB_KAYU_MINUS = 24, A_JOB_KAYU_PLUS = 25;
    private static final int A_JOB_TAMBANG_MINUS = 26, A_JOB_TAMBANG_PLUS = 27;
    private static final int A_SELL_FOOD = 30, A_SELL_WOOD = 31, A_BUY_FOOD = 32;

    @Override
    public void create() {
        cam = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        fontHud = new BitmapFont();
        fontHud.getData().setScale(1.7f);
        fontSmall = new BitmapFont();
        fontSmall.getData().setScale(1.25f);
        prefs = Gdx.app.getPreferences(PREFS);

        String saved = prefs.getString(SAVE_KEY, "");
        if (!saved.isEmpty()) {
            try {
                sim = Sim.fromJson(saved);
            } catch (Exception e) {
                sim = Sim.newGame();
            }
        } else {
            sim = Sim.newGame();
        }

        // Pohon di pinggir + jalur tengah (di luar zona desa).
        Random decor = new Random(99L);
        for (int i = 0; i < 26; i++) {
            float x = 1000 + decor.nextFloat() * 200; // jalur tengah
            float y = 60 + decor.nextFloat() * (WORLD_H - 120);
            treeX.add(x);
            treeY.add(y);
        }
        for (int i = 0; i < 10; i++) {
            treeX.add(20 + decor.nextFloat() * 60);
            treeY.add(60 + decor.nextFloat() * (WORLD_H - 120));
            treeX.add(WORLD_W - 80 + decor.nextFloat() * 60);
            treeY.add(60 + decor.nextFloat() * (WORLD_H - 120));
        }

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(new InputMultiplexer(new GestureDetector(this)));
        showToast("Selamat datang di Karangsepi! Bangun ladang + sumur, atur pekerjaan, kalahkan Limpahraya.");
    }

    /** Zoom yang membuat seluruh dunia (2200x1200) muat dalam viewport. */
    static float fitZoom(float viewportW, float viewportH) {
        return Math.max(WORLD_W / viewportW, WORLD_H / viewportH);
    }

    /**
     * Jepit posisi kamera pada satu sumbu. Bila separuh area terlihat >= separuh
     * dunia (zoom-out melewati batas), kembalikan tengah dunia — JANGAN jepit
     * ke tepi (bug v1.0: min > max pada clamp membuat kamera melompat ke pojok
     * dan peta berkedip tiap frame saat zoom berubah).
     */
    static float clampAxis(float pos, float halfVisible, float worldSize) {
        if (halfVisible >= worldSize / 2f) return worldSize / 2f;
        return MathUtils.clamp(pos, halfVisible, worldSize - halfVisible);
    }

    @Override
    public void resize(int width, int height) {
        float aspect = (float) height / width;
        cam.viewportWidth = WORLD_W;
        cam.viewportHeight = WORLD_W * aspect;
        // Default: seluruh peta muat dalam satu layar (fit-all).
        zoomFit = fitZoom(cam.viewportWidth, cam.viewportHeight);
        cam.zoom = zoomFit;
        cam.position.set(WORLD_W / 2f, WORLD_H / 2f, 0);
        clampCamera();
        cam.update();
        hudMatrix.setToOrtho2D(0, 0, width, height);
        layoutButtons(width, height);
    }

    private void layoutButtons(int w, int h) {
        buttons.clear();
        float topBarH = 64;
        // Kanan atas: kecepatan + simpan + baru.
        float bw = 62, bh = 48, gap = 8, x = w - gap;
        buttons.add(new Btn(x - bw, 8, bw, bh, "Baru", A_NEW)); x -= bw + gap;
        buttons.add(new Btn(x - bw, 8, bw, bh, "Simpan", A_SAVE)); x -= bw + gap;
        buttons.add(new Btn(x - bw, 8, bw, bh, "10x", A_SPEED10)); x -= bw + gap;
        buttons.add(new Btn(x - bw, 8, bw, bh, "5x", A_SPEED5)); x -= bw + gap;
        buttons.add(new Btn(x - bw, 8, bw, bh, "1x", A_SPEED1)); x -= bw + gap;
        buttons.add(new Btn(x - bw, 8, bw, bh, "II", A_PAUSE));
        // Bawah: 6 tombol bangun (lebar 150 supaya label harga tidak meluber).
        float buildW = 150, buildH = 62, buildGap = 8, bx = 8, by = h - buildH - 8;
        buttons.add(new Btn(bx, by, buildW, buildH, "Rumah\n10 kayu", A_BUILD_RUMAH)); bx += buildW + buildGap;
        buttons.add(new Btn(bx, by, buildW, buildH, "Ladang\n8 kayu", A_BUILD_LADANG)); bx += buildW + buildGap;
        buttons.add(new Btn(bx, by, buildW, buildH, "Sumur\n6 kayu", A_BUILD_SUMUR)); bx += buildW + buildGap;
        buttons.add(new Btn(bx, by, buildW, buildH, "Kincir\n15 kayu+5 emas", A_BUILD_KINCIR)); bx += buildW + buildGap;
        buttons.add(new Btn(bx, by, buildW, buildH, "Tambang\n20 kayu+10 emas", A_BUILD_TAMBANG)); bx += buildW + buildGap;
        buttons.add(new Btn(bx, by, buildW, buildH, "Pasar\n12 kayu", A_BUILD_PASAR)); bx += buildW + buildGap;
        // Pekerjaan: 4 baris "- N +" di kanan bawah.
        float jobW = 250, jobH = 30, jx = w - jobW - 8, jy = h - 8;
        buttons.add(new Btn(jx, jy - jobH, 34, jobH, "-", A_JOB_PETANI_MINUS));
        buttons.add(new Btn(jx + jobW - 34, jy - jobH, 34, jobH, "+", A_JOB_PETANI_PLUS)); jy -= jobH + 4;
        buttons.add(new Btn(jx, jy - jobH, 34, jobH, "-", A_JOB_AIR_MINUS));
        buttons.add(new Btn(jx + jobW - 34, jy - jobH, 34, jobH, "+", A_JOB_AIR_PLUS)); jy -= jobH + 4;
        buttons.add(new Btn(jx, jy - jobH, 34, jobH, "-", A_JOB_KAYU_MINUS));
        buttons.add(new Btn(jx + jobW - 34, jy - jobH, 34, jobH, "+", A_JOB_KAYU_PLUS)); jy -= jobH + 4;
        buttons.add(new Btn(jx, jy - jobH, 34, jobH, "-", A_JOB_TAMBANG_MINUS));
        buttons.add(new Btn(jx + jobW - 34, jy - jobH, 34, jobH, "+", A_JOB_TAMBANG_PLUS));
        // Jual-beli (butuh pasar): 3 tombol di kiri panel pekerjaan.
        // Label dua baris supaya tidak meluber keluar tombol.
        float tw = 170, th = 48, tx = w - 258 - 16 - 3 * tw - 2 * 8, ty = h - th - 8;
        buttons.add(new Btn(tx, ty, tw, th, "Jual 10 Makan\n3 Emas", A_SELL_FOOD)); tx += tw + 8;
        buttons.add(new Btn(tx, ty, tw, th, "Jual 10 Kayu\n2 Emas", A_SELL_WOOD)); tx += tw + 8;
        buttons.add(new Btn(tx, ty, tw, th, "Beli 10 Makan\n5 Emas", A_BUY_FOOD));
        // Dialog akhir lomba.
        if (sim.raceOver) {
            float dw = 300, dh = 54, dx = w / 2f - dw / 2f, dy = h / 2f - 40;
            buttons.add(new Btn(dx, dy, dw, dh, "Lanjutkan (tanpa akhir)", A_CONTINUE));
            buttons.add(new Btn(dx, dy + dh + 10, dw, dh, "Mulai Baru", A_RESTART));
        }
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        sim.update(dt);
        if (sim.raceOver && !raceDialogLaidOut) {
            raceDialogLaidOut = true;
            layoutButtons(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        bladeAngle += dt * 50f;
        if (toastTimer > 0) toastTimer -= dt;
        updateVillagerVisuals(dt);

        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapes.setProjectionMatrix(cam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawWorld();
        shapes.end();

        drawHud();
    }

    // ---------- dunia ----------

    private void drawWorld() {
        // Tanah: zona pemain hijau muda, zona lawan hijau hangat, tengah tanah.
        shapes.setColor(0.45f, 0.72f, 0.38f, 1f);
        shapes.rect(0, 0, 1000, WORLD_H);
        shapes.setColor(0.55f, 0.70f, 0.34f, 1f);
        shapes.rect(1200, 0, 1000, WORLD_H);
        shapes.setColor(0.62f, 0.50f, 0.33f, 1f);
        shapes.rect(1000, 0, 200, WORLD_H);
        // Garis batas zona.
        shapes.setColor(1f, 1f, 1f, 0.35f);
        shapes.rect(996, 0, 4, WORLD_H);
        shapes.rect(1196, 0, 4, WORLD_H);
        // Pohon.
        for (int i = 0; i < treeX.size; i++) {
            shapes.setColor(0.16f, 0.45f, 0.20f, 1f);
            shapes.circle(treeX.get(i), treeY.get(i), 22);
            shapes.setColor(0.25f, 0.60f, 0.28f, 1f);
            shapes.circle(treeX.get(i) - 5, treeY.get(i) + 6, 14);
        }
        drawVillage(sim.player);
        drawVillage(sim.rival);
        // Gelap malam.
        float dark = nightDarkness(sim.timeOfDay());
        if (dark > 0.01f) {
            shapes.setColor(0.05f, 0.08f, 0.25f, dark);
            shapes.rect(cam.position.x - cam.viewportWidth * cam.zoom / 2f,
                    cam.position.y - cam.viewportHeight * cam.zoom / 2f,
                    cam.viewportWidth * cam.zoom, cam.viewportHeight * cam.zoom);
        }
    }

    private float nightDarkness(float t) {
        if (t >= 0.25f && t < 0.75f) return 0f;
        if (t >= 0.85f || t < 0.15f) return 0.22f;
        if (t < 0.25f) return 0.22f * (0.25f - t) / 0.10f;
        return 0.22f * (t - 0.75f) / 0.10f;
    }

    private void drawVillage(Village v) {
        for (Building b : v.buildings) drawBuilding(b);
        for (Villager vill : v.villagers) {
            shapes.setColor(jobColor(vill.job));
            shapes.circle(vill.x, vill.y, 7);
            shapes.setColor(0f, 0f, 0f, 0.25f);
            shapes.circle(vill.x + 2, vill.y - 2, 7); // bayangan tipis
        }
    }

    private Color jobColor(Villager.Job job) {
        switch (job) {
            case PETANI: return Color.valueOf("4CAF50");
            case PENCARI_AIR: return Color.valueOf("2196F3");
            case PENEBANG: return Color.valueOf("8D6E63");
            case PENAMBANG: return Color.valueOf("FFC107");
            default: return Color.valueOf("9E9E9E");
        }
    }

    private void drawBuilding(Building b) {
        float x = b.x, y = b.y;
        switch (b.type) {
            case RUMAH:
                shapes.setColor(0.98f, 0.92f, 0.80f, 1f);
                shapes.rect(x - 28, y - 22, 56, 44);
                shapes.setColor(0.72f, 0.30f, 0.22f, 1f);
                shapes.triangle(x - 32, y + 22, x + 32, y + 22, x, y + 48);
                shapes.setColor(0.45f, 0.30f, 0.18f, 1f);
                shapes.rect(x - 7, y - 22, 14, 22);
                break;
            case LADANG:
                shapes.setColor(0.35f, 0.62f, 0.22f, 1f);
                shapes.rect(x - 40, y - 28, 80, 56);
                shapes.setColor(0.25f, 0.50f, 0.16f, 1f);
                for (int i = -1; i <= 1; i++) shapes.rect(x - 40, y + i * 16 - 2, 80, 4);
                shapes.setColor(0.55f, 0.85f, 0.30f, 1f);
                for (int i = 0; i < 5; i++) shapes.circle(x - 28 + i * 14, y + 10, 4);
                break;
            case SUMUR:
                shapes.setColor(0.55f, 0.55f, 0.58f, 1f);
                shapes.circle(x, y, 24);
                shapes.setColor(0.15f, 0.55f, 0.90f, 1f);
                shapes.circle(x, y, 15);
                shapes.setColor(0.72f, 0.30f, 0.22f, 1f);
                shapes.triangle(x - 26, y + 18, x + 26, y + 18, x, y + 44);
                break;
            case KINCIR:
                shapes.setColor(0.95f, 0.95f, 0.93f, 1f);
                shapes.rect(x - 10, y - 30, 20, 60);
                shapes.setColor(0.90f, 0.90f, 0.88f, 1f);
                for (int i = 0; i < 4; i++) {
                    float a = (float) Math.toRadians(bladeAngle + i * 90);
                    shapes.rectLine(x, y + 30, x + (float) Math.cos(a) * 34, y + 30 + (float) Math.sin(a) * 34, 5);
                }
                shapes.setColor(0.30f, 0.30f, 0.32f, 1f);
                shapes.circle(x, y + 30, 6);
                break;
            case TAMBANG:
                shapes.setColor(0.35f, 0.35f, 0.38f, 1f);
                shapes.triangle(x - 34, y - 24, x + 34, y - 24, x, y + 34);
                shapes.setColor(0.10f, 0.10f, 0.12f, 1f);
                shapes.rect(x - 10, y - 24, 20, 20);
                shapes.setColor(1f, 0.80f, 0.15f, 1f);
                shapes.circle(x - 18, y + 8, 4);
                shapes.circle(x + 16, y + 2, 4);
                shapes.circle(x + 4, y + 18, 4);
                break;
            case PASAR:
                shapes.setColor(0.55f, 0.30f, 0.65f, 1f);
                shapes.rect(x - 34, y - 24, 68, 40);
                for (int i = 0; i < 4; i++) {
                    shapes.setColor(i % 2 == 0 ? Color.WHITE : Color.valueOf("9C27B0"));
                    shapes.rect(x - 34 + i * 17, y + 16, 17, 12);
                }
                shapes.setColor(1f, 0.85f, 0.20f, 1f);
                shapes.circle(x - 12, y - 6, 5);
                shapes.circle(x + 12, y - 6, 5);
                break;
        }
    }

    private void updateVillagerVisuals(float dt) {
        boolean night = !sim.isDaytime();
        for (Village v : new Village[]{sim.player, sim.rival}) {
            for (Villager vill : v.villagers) {
                float ax, ay, range = 130f;
                if (night) {
                    Building home = nearestBuilding(v, BuildingType.RUMAH, vill.x, vill.y);
                    ax = home != null ? home.x : (v.zoneX0 + v.zoneX1) / 2f;
                    ay = home != null ? home.y : (v.zoneY0 + v.zoneY1) / 2f;
                    range = 60f;
                } else {
                    BuildingType work = null;
                    if (vill.job == Villager.Job.PETANI) work = BuildingType.LADANG;
                    else if (vill.job == Villager.Job.PENCARI_AIR) work = BuildingType.SUMUR;
                    else if (vill.job == Villager.Job.PENAMBANG) work = BuildingType.TAMBANG;
                    if (work != null) {
                        Building b = nearestBuilding(v, work, vill.x, vill.y);
                        ax = b != null ? b.x : (v.zoneX0 + v.zoneX1) / 2f;
                        ay = b != null ? b.y : (v.zoneY0 + v.zoneY1) / 2f;
                    } else if (vill.job == Villager.Job.PENEBANG) {
                        ax = v.isPlayer ? 1050 : 1150;
                        ay = vill.y;
                        range = 200f;
                    } else {
                        ax = (v.zoneX0 + v.zoneX1) / 2f;
                        ay = (v.zoneY0 + v.zoneY1) / 2f;
                    }
                }
                vill.stepVisual(dt, ax, ay, range, rng);
            }
        }
    }

    private Building nearestBuilding(Village v, BuildingType type, float x, float y) {
        Building best = null;
        float bestD = Float.MAX_VALUE;
        for (Building b : v.buildings) {
            if (b.type != type) continue;
            float dx = b.x - x, dy = b.y - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    // ---------- HUD ----------

    private void drawHud() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        // HUD digambar dalam koordinat layar (piksel), BUKAN koordinat dunia —
        // v1.0 memakai matriks kamera di sini sehingga semua panel pindah
        // tempat begitu kamera di-zoom/di-pan.
        shapes.setProjectionMatrix(hudMatrix);
        batch.setProjectionMatrix(hudMatrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Bar atas.
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, h - 64, w, 64);
        // Bar bawah.
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, w, 150);
        // Tombol.
        for (Btn b : buttons) {
            boolean active = isActiveButton(b.action);
            shapes.setColor(active ? Color.valueOf("FF9800") : Color.valueOf("37474F"));
            shapes.rect(b.x, h - b.top - b.h, b.w, b.h);
            shapes.setColor(1f, 1f, 1f, 0.6f);
            shapes.rect(b.x, h - b.top - 2, b.w, 2);
        }
        // Panel grafik skor (kanan atas, di bawah bar atas).
        float chartX = w - 320, chartY = h - 64 - 130, chartW = 300, chartH = 120;
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(chartX, chartY, chartW, chartH);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        int max = 1;
        for (int s : sim.player.scoreHistory) max = Math.max(max, s);
        for (int s : sim.rival.scoreHistory) max = Math.max(max, s);
        drawHistoryLine(chartX, chartY, chartW, chartH, sim.player.scoreHistory, max, Color.CYAN);
        drawHistoryLine(chartX, chartY, chartW, chartH, sim.rival.scoreHistory, max, Color.ORANGE);
        shapes.end();

        batch.begin();
        // Bar atas: sumber daya + waktu.
        Village p = sim.player;
        fontHud.setColor(Color.WHITE);
        fontHud.draw(batch, "Air " + p.air + "   Makan " + p.makanan + "   Kayu " + p.kayu
                + "   Emas " + p.emas + "   Penduduk " + p.population() + "/" + p.housingCapacity()
                + "   Skor " + p.prosperity(), 12, h - 22);
        fontSmall.setColor(Color.valueOf("FFE082"));
        fontSmall.draw(batch, "Tahun " + sim.year() + "  Hari " + sim.dayOfYear() + "/30"
                + (sim.isDaytime() ? "  Siang" : "  Malam")
                + "   |   Karangsepi " + p.prosperity() + " vs Limpahraya " + sim.rival.prosperity()
                + (sim.rival.pestaDaysLeft > 0 ? "  (Lawan lagi PESTA!)" : ""), 12, h - 48);
        // Label tombol.
        for (Btn b : buttons) {
            fontSmall.setColor(Color.WHITE);
            drawMultilineCentered(b.label, b.x + b.w / 2f, h - b.top - b.h / 2f + 8);
        }
        // Panel pekerjaan: teks sejajar baris tombol -/+ di kanan bawah
        // (y dari bawah; v1.0 menggambarnya di dekat ATAS sehingga menimpa
        // tombol kecepatan 1x/5x/10x dan panel grafik).
        // Baris tombol: y 8..38, 42..72, 76..106, 110..140 dari bawah.
        float jx = w - 258;
        fontSmall.setColor(Color.WHITE);
        fontSmall.draw(batch, "Petani " + p.jobCounts[Villager.Job.PETANI.ordinal()], jx + 44, 29);
        fontSmall.draw(batch, "Air " + p.jobCounts[Villager.Job.PENCARI_AIR.ordinal()], jx + 44, 63);
        fontSmall.draw(batch, "Kayu " + p.jobCounts[Villager.Job.PENEBANG.ordinal()], jx + 44, 97);
        fontSmall.draw(batch, "Tambang " + p.jobCounts[Villager.Job.PENAMBANG.ordinal()], jx + 44, 131);
        fontSmall.draw(batch, "Menganggur " + p.idleCount(), jx + 44, 165);
        // Info pilihan / toast.
        if (selectedVillager != null) {
            Villager v = selectedVillager;
            fontSmall.setColor(Color.valueOf("B3E5FC"));
            fontSmall.draw(batch, "Penduduk - " + jobName(v.job) + "  Lapar " + v.lapar
                    + "  Haus " + v.haus + "  Energi " + v.energi, 12, 168);
        } else if (selectedBuilding != null) {
            fontSmall.setColor(Color.valueOf("B3E5FC"));
            fontSmall.draw(batch, selectedBuilding.type.label + " - " + selectedBuilding.type.desc, 12, 168);
        } else if (buildMode != null) {
            fontSmall.setColor(Color.valueOf("FFE082"));
            fontSmall.draw(batch, "Mode bangun: " + buildMode.label + " (" + buildMode.kayuCost
                    + " kayu, " + buildMode.emasCost + " emas) - tap tanah di desamu", 12, 168);
        }
        if (toastTimer > 0) {
            fontSmall.setColor(Color.WHITE);
            fontSmall.draw(batch, toast, 12, 192);
        }
        // Label grafik skor.
        fontSmall.setColor(Color.CYAN);
        fontSmall.draw(batch, "Kamu", w - 314, h - 70);
        fontSmall.setColor(Color.ORANGE);
        fontSmall.draw(batch, "Lawan", w - 260, h - 70);
        // Dialog akhir lomba.
        if (sim.raceOver) {
            fontHud.setColor(Color.valueOf("FFD54F"));
            String msg = "Lomba 20 tahun selesai! Pemenang: " + sim.winner
                    + "  (" + p.prosperity() + " vs " + sim.rival.prosperity() + ")";
            fontHud.draw(batch, msg, w / 2f - 380, h / 2f + 60);
        }
        batch.end();
    }

    private void drawMultilineCentered(String text, float cx, float cy) {
        String[] lines = text.split("\n");
        float y = cy + (lines.length - 1) * 9;
        GlyphLayout layout = new GlyphLayout();
        for (String line : lines) {
            layout.setText(fontSmall, line);
            fontSmall.draw(batch, line, cx - layout.width / 2f, y);
            y -= 18;
        }
    }

    private void drawHistoryLine(float x, float y, float w, float h, java.util.ArrayList<Integer> hist, int max, Color c) {
        if (hist.size() < 2) return;
        shapes.setColor(c);
        int n = Math.min(hist.size(), 150);
        for (int i = 1; i < n; i++) {
            float x0 = x + (i - 1) / 149f * w;
            float x1 = x + i / 149f * w;
            float y0 = y + hist.get(hist.size() - n + i - 1) / (float) max * h;
            float y1 = y + hist.get(hist.size() - n + i) / (float) max * h;
            shapes.line(x0, y0, x1, y1);
        }
    }

    private boolean isActiveButton(int action) {
        if (action == A_PAUSE) return sim.speed == 0;
        if (action == A_SPEED1) return sim.speed == 1;
        if (action == A_SPEED5) return sim.speed == 5;
        if (action == A_SPEED10) return sim.speed == 10;
        if (action == A_BUILD_RUMAH) return buildMode == BuildingType.RUMAH;
        if (action == A_BUILD_LADANG) return buildMode == BuildingType.LADANG;
        if (action == A_BUILD_SUMUR) return buildMode == BuildingType.SUMUR;
        if (action == A_BUILD_KINCIR) return buildMode == BuildingType.KINCIR;
        if (action == A_BUILD_TAMBANG) return buildMode == BuildingType.TAMBANG;
        if (action == A_BUILD_PASAR) return buildMode == BuildingType.PASAR;
        return false;
    }

    private String jobName(Villager.Job job) {
        switch (job) {
            case PETANI: return "Petani";
            case PENCARI_AIR: return "Pencari Air";
            case PENEBANG: return "Penebang";
            case PENAMBANG: return "Penambang";
            default: return "Menganggur";
        }
    }

    private void showToast(String msg) {
        toast = msg;
        toastTimer = 4f;
    }

    private void newGame() {
        prefs.remove(SAVE_KEY);
        prefs.flush();
        sim = Sim.newGame();
        buildMode = null;
        selectedVillager = null;
        selectedBuilding = null;
        raceDialogLaidOut = false;
        layoutButtons(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        showToast("Permainan baru dimulai.");
    }

    private void saveGame() {
        prefs.putString(SAVE_KEY, sim.toJson());
        prefs.flush();
        showToast("Disimpan.");
    }

    // ---------- input ----------

    @Override
    public boolean tap(float x, float y, int count, int button) {
        for (Btn b : buttons) {
            if (b.contains(x, y)) {
                handleButton(b.action);
                return true;
            }
        }
        Vector3 world = cam.unproject(new Vector3(x, y, 0));
        float wx = world.x, wy = world.y;
        if (buildMode != null) {
            if (sim.player.placeBuilding(buildMode, wx, wy)) {
                showToast(buildMode.label + " dibangun.");
                buildMode = null;
            } else {
                showToast(!sim.player.canAfford(buildMode) ? "Sumber daya tidak cukup."
                        : "Tidak bisa dibangun di sini (di luar desa / terlalu dekat bangunan lain).");
            }
            return true;
        }
        // Pilih penduduk / bangunan terdekat.
        selectedVillager = null;
        selectedBuilding = null;
        float bestD = 30f * 30f;
        for (Village v : new Village[]{sim.player, sim.rival}) {
            for (Villager vill : v.villagers) {
                float dx = vill.x - wx, dy = vill.y - wy;
                float d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    selectedVillager = vill;
                }
            }
        }
        if (selectedVillager == null) {
            bestD = 60f * 60f;
            for (Village v : new Village[]{sim.player, sim.rival}) {
                for (Building b : v.buildings) {
                    float dx = b.x - wx, dy = b.y - wy;
                    float d = dx * dx + dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        selectedBuilding = b;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        float scale = cam.viewportWidth * cam.zoom / Gdx.graphics.getWidth();
        cam.position.x -= deltaX * scale;
        cam.position.y += deltaY * scale;
        clampCamera();
        cam.update();
        return true;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        float ratio = initialDistance / distance;
        // Zoom-out maksimal = fit-all (seluruh peta muat); zoom-in minimal 0.35.
        cam.zoom = MathUtils.clamp(cam.zoom * ratio, 0.35f, zoomFit);
        clampCamera();
        cam.update();
        return true;
    }

    private void clampCamera() {
        float vw = cam.viewportWidth * cam.zoom / 2f;
        float vh = cam.viewportHeight * cam.zoom / 2f;
        cam.position.x = clampAxis(cam.position.x, vw, WORLD_W);
        cam.position.y = clampAxis(cam.position.y, vh, WORLD_H);
    }

    private void handleButton(int action) {
        switch (action) {
            case A_PAUSE: sim.speed = 0; break;
            case A_SPEED1: sim.speed = 1; break;
            case A_SPEED5: sim.speed = 5; break;
            case A_SPEED10: sim.speed = 10; break;
            case A_SAVE: saveGame(); break;
            case A_NEW:
                newGame();
                break;
            case A_CONTINUE:
                sim.raceOver = false;
                layoutButtons(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                showToast("Mode tanpa akhir — teruskan desamu!");
                break;
            case A_RESTART:
                newGame();
                break;
            case A_BUILD_RUMAH: buildMode = toggle(BuildingType.RUMAH); break;
            case A_BUILD_LADANG: buildMode = toggle(BuildingType.LADANG); break;
            case A_BUILD_SUMUR: buildMode = toggle(BuildingType.SUMUR); break;
            case A_BUILD_KINCIR: buildMode = toggle(BuildingType.KINCIR); break;
            case A_BUILD_TAMBANG: buildMode = toggle(BuildingType.TAMBANG); break;
            case A_BUILD_PASAR: buildMode = toggle(BuildingType.PASAR); break;
            case A_JOB_PETANI_MINUS: sim.player.assignJob(Villager.Job.PETANI, -1); break;
            case A_JOB_PETANI_PLUS: sim.player.assignJob(Villager.Job.PETANI, 1); break;
            case A_JOB_AIR_MINUS: sim.player.assignJob(Villager.Job.PENCARI_AIR, -1); break;
            case A_JOB_AIR_PLUS: sim.player.assignJob(Villager.Job.PENCARI_AIR, 1); break;
            case A_JOB_KAYU_MINUS: sim.player.assignJob(Villager.Job.PENEBANG, -1); break;
            case A_JOB_KAYU_PLUS: sim.player.assignJob(Villager.Job.PENEBANG, 1); break;
            case A_JOB_TAMBANG_MINUS: sim.player.assignJob(Villager.Job.PENAMBANG, -1); break;
            case A_JOB_TAMBANG_PLUS: sim.player.assignJob(Villager.Job.PENAMBANG, 1); break;
            case A_SELL_FOOD:
                showToast(sim.player.sellFood() ? "Jual 10 makanan = 3 emas." : "Butuh pasar + 10 makanan.");
                break;
            case A_SELL_WOOD:
                showToast(sim.player.sellWood() ? "Jual 10 kayu = 2 emas." : "Butuh pasar + 10 kayu.");
                break;
            case A_BUY_FOOD:
                showToast(sim.player.buyFood() ? "Beli 10 makanan = 5 emas." : "Butuh pasar + 5 emas.");
                break;
        }
    }

    private BuildingType toggle(BuildingType type) {
        selectedVillager = null;
        selectedBuilding = null;
        return buildMode == type ? null : type;
    }

    @Override public boolean touchDown(float x, float y, int pointer, int button) { return false; }
    @Override public boolean longPress(float x, float y) { return false; }
    @Override public boolean fling(float velocityX, float velocityY, int button) { return false; }
    @Override public boolean panStop(float x, float y, int pointer, int button) { return false; }
    @Override public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) { return false; }
    @Override public void pinchStop() {}

    @Override
    public void pause() {
        saveGame();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        fontHud.dispose();
        fontSmall.dispose();
    }
}
