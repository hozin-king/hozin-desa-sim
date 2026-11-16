# Hozin Desa Sim

Simulasi desa 2D top-down (libGDX, native Android, full offline, grafik flat
modern — bukan pixel art). Terinspirasi video "100 Jenius Miskin vs 100 Idiot
Kaya": dua desa berpenduduk 100 orang berlomba kemakmuran selama 20 tahun.

## Cara main

- **Kamu** mengatur desa **Karangsepi** (kiri, hijau muda).
- **Limpahraya** (kanan) jalan sendiri — AI kaya tapi boros: pesta tiap 10
  hari, jual emas murah saat kehabisan makanan.
- Bangun: Rumah, Ladang, Sumur, Kincir Angin, Tambang Emas, Pasar (tap tombol
  bangun, lalu tap tanah di desamu).
- Atur pekerjaan penduduk: Petani, Pencari Air, Penebang, Penambang
  (tombol - / + di kanan bawah; sisanya menganggur).
- Jual-beli di pasar (butuh bangunan Pasar): jual makanan/kayu jadi emas,
  beli makanan pakai emas.
- Kecepatan: pause / 1x / 5x / 10x. Siang-malam berjalan; 1 tahun = 30 hari.
- Tap penduduk untuk lihat lapar/haus/energi; tap bangunan untuk info.
- Skor kemakmuran: penduduk + stok sumber daya + bangunan + kebahagiaan.
  Grafik dua desa ada di kanan atas.
- Setelah 20 tahun (600 hari), pemenang diumumkan — bisa lanjut mode tanpa
  akhir. Save otomatis saat aplikasi di-pause + tombol Simpan.

## Ekonomi (per hari)

- Petani: 6 makanan (butuh air irigasi 2/hari per ladang; kering = separuh;
  kincir = +50%). Pencari air: 8 air. Penebang: 3 kayu. Penambang: 1 emas.
- Tiap penduduk makan 1 + minum 1 per hari. Kekurangan = kelaparan/kematian.
- Surplus + ada kapasitas rumah = kelahiran (maks +3/hari).
- Rumah menampung 25 penduduk.

## Bangun

- `./gradlew :core:test` — unit test logika inti (ekonomi, kebutuhan, skor).
- `./gradlew :android:assembleDebug` — APK debug (tanda tangan keystore debug
  persisten bila `debug.keystore` ada di root; secret repo
  `DEBUG_KEYSTORE_BASE64`).
- Rilis otomatis lewat GitHub Actions saat push ke `main` (tag v1.0.0).

## Yang dipangkas untuk v1.0 (jujur)

- Tanpa festival/migrasi antar desa, tanpa event acak, tanpa suara/musik,
  tanpa mode tontonan murni (dua-duanya AI) — desa lawan selalu AI, pemain
  selalu mengatur satu desa.
- Hutan tidak bisa habis (kayu tak terbatas, tapi lambat).
- Grafik skor hanya 150 titik terakhir.
