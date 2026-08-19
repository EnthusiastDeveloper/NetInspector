package dev.enthusiastdev.netinspector.data.persistence.wol

import androidx.room.Entity
import androidx.room.PrimaryKey

/** design §9.6/§10 - "saved WOL targets persist in Room." First real `@Database` entity
 * (design §10's schema directory placeholder, empty since Phase 0). */
@Entity(tableName = "saved_wol_target")
data class SavedWolTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val mac: String,
    val broadcastAddress: String,
)
