package com.lydiaperspective.seamworkstudio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "garment_projects")
data class GarmentProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inspiration_items",
    foreignKeys = [ForeignKey(
        entity = GarmentProject::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class InspirationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val kind: String,
    val uri: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM garment_projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<GarmentProject>>

    @Insert
    suspend fun insertProject(project: GarmentProject): Long

    @Query("DELETE FROM garment_projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: Long)
}

@Dao
interface InspirationDao {
    @Query("SELECT * FROM inspiration_items WHERE projectId = :projectId ORDER BY addedAt DESC")
    fun observeItems(projectId: Long): Flow<List<InspirationItem>>

    @Insert
    suspend fun insert(item: InspirationItem)

    @Query("DELETE FROM inspiration_items WHERE id = :itemId")
    suspend fun delete(itemId: Long)
}

@Database(
    entities = [GarmentProject::class, InspirationItem::class],
    version = 1,
    exportSchema = false
)
abstract class SeamworkDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun inspirationDao(): InspirationDao

    companion object {
        @Volatile private var INSTANCE: SeamworkDatabase? = null

        fun get(context: Context): SeamworkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SeamworkDatabase::class.java,
                    "seamwork-studio.db"
                ).build().also { INSTANCE = it }
            }
    }
}
