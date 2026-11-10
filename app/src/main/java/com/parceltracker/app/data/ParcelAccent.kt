package com.parceltracker.app.data

/**
 * App color themes. Colors are stored as ARGB longs so the data layer stays
 * independent of Compose; the theme maps them to a ColorScheme.
 */
enum class ParcelAccent(
    val label: String,
    val swatch: Long,
    val lightPrimary: Long,
    val lightOnPrimary: Long,
    val lightContainer: Long,
    val lightOnContainer: Long,
    val darkPrimary: Long,
    val darkOnPrimary: Long,
    val darkContainer: Long,
    val darkOnContainer: Long,
) {
    FOREST(
        label = "Forest",
        swatch = 0xFF2E4B3C,
        lightPrimary = 0xFF2E4B3C,
        lightOnPrimary = 0xFFFFFCF5,
        lightContainer = 0xFFDCE8DD,
        lightOnContainer = 0xFF1C3427,
        darkPrimary = 0xFFA8C9B4,
        darkOnPrimary = 0xFF10281C,
        darkContainer = 0xFF2C4636,
        darkOnContainer = 0xFFD6E8DA,
    ),
    OCEAN(
        label = "Ocean",
        swatch = 0xFF2F5D7C,
        lightPrimary = 0xFF2F5D7C,
        lightOnPrimary = 0xFFFFFCF5,
        lightContainer = 0xFFD6E6F2,
        lightOnContainer = 0xFF10344A,
        darkPrimary = 0xFF9CC5E4,
        darkOnPrimary = 0xFF0A283A,
        darkContainer = 0xFF24465C,
        darkOnContainer = 0xFFD3E8F7,
    ),
    TERRACOTTA(
        label = "Terracotta",
        swatch = 0xFFB65F3B,
        lightPrimary = 0xFFB65F3B,
        lightOnPrimary = 0xFFFFFCF5,
        lightContainer = 0xFFF4DDD1,
        lightOnContainer = 0xFF3B1608,
        darkPrimary = 0xFFE2A182,
        darkOnPrimary = 0xFF3B1608,
        darkContainer = 0xFF5A3423,
        darkOnContainer = 0xFFF8DCCB,
    ),
    PLUM(
        label = "Plum",
        swatch = 0xFF6B4A7A,
        lightPrimary = 0xFF6B4A7A,
        lightOnPrimary = 0xFFFFFCF5,
        lightContainer = 0xFFEADCF1,
        lightOnContainer = 0xFF2E1638,
        darkPrimary = 0xFFC9A6D8,
        darkOnPrimary = 0xFF2E1638,
        darkContainer = 0xFF4A2E57,
        darkOnContainer = 0xFFEADCF1,
    ),
    INDIGO(
        label = "Indigo",
        swatch = 0xFF445A9C,
        lightPrimary = 0xFF445A9C,
        lightOnPrimary = 0xFFFFFCF5,
        lightContainer = 0xFFDDE2F5,
        lightOnContainer = 0xFF17234D,
        darkPrimary = 0xFFAAB8F0,
        darkOnPrimary = 0xFF17234D,
        darkContainer = 0xFF303D6B,
        darkOnContainer = 0xFFDDE2F5,
    ),
    ROSE(
        label = "Rose",
        swatch = 0xFFA83E5E,
        lightPrimary = 0xFFA83E5E,
        lightOnPrimary = 0xFFFFFCF5,
        lightContainer = 0xFFF7D9E1,
        lightOnContainer = 0xFF3D0F1E,
        darkPrimary = 0xFFEFA3B7,
        darkOnPrimary = 0xFF3D0F1E,
        darkContainer = 0xFF5E2A39,
        darkOnContainer = 0xFFF7D9E1,
    ),
    ;

    companion object {
        fun from(value: String?): ParcelAccent =
            ParcelAccent.entries.firstOrNull { it.name == value } ?: FOREST
    }
}
