package com.hozinking.desasim;

/** Jenis bangunan. Biaya dalam kayu/emas; workerCap = maks pekerja yang didukung. */
public enum BuildingType {
    RUMAH("Rumah", 10, 0, 0, "Kapasitas +25 penduduk"),
    LADANG("Ladang", 8, 0, 5, "5 petani, hasil makanan"),
    SUMUR("Sumur", 6, 0, 5, "5 pencari air"),
    KINCIR("Kincir Angin", 15, 5, 0, "Hasil ladang +50%"),
    TAMBANG("Tambang Emas", 20, 10, 5, "5 penambang, hasil emas"),
    PASAR("Pasar", 12, 0, 0, "Buka jual-beli");

    public final String label;
    public final int kayuCost;
    public final int emasCost;
    public final int workerCap;
    public final String desc;

    BuildingType(String label, int kayuCost, int emasCost, int workerCap, String desc) {
        this.label = label;
        this.kayuCost = kayuCost;
        this.emasCost = emasCost;
        this.workerCap = workerCap;
        this.desc = desc;
    }
}
