package com.lydiaperspective.seamworkstudio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Update
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

@Entity(
    tableName = "sketch_pages",
    foreignKeys = [ForeignKey(
        entity = GarmentProject::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class SketchPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val paperSize: String = "A4",
    val background: String = "GRID",
    val drawingData: String = "",
    val updatedAt: Long = System.currentTimeMillis()
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

@Dao
interface SketchPageDao {
    @Query("SELECT * FROM sketch_pages WHERE projectId = :projectId ORDER BY id ASC")
    fun observePages(projectId: Long): Flow<List<SketchPage>>

    @Insert
    suspend fun insertPage(page: SketchPage): Long

    @Update
    suspend fun updatePage(page: SketchPage)

    @Query("DELETE FROM sketch_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)
}

@Database(
    entities = [GarmentProject::class, InspirationItem::class, SketchPage::class],
    version = 2,
    exportSchema = false
)
abstract class SeamworkDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun inspirationDao(): InspirationDao
    abstract fun sketchPageDao(): SketchPageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sketch_pages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        paperSize TEXT NOT NULL,
                        background TEXT NOT NULL,
                        drawingData TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(projectId) REFERENCES garment_projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sketch_pages_projectId ON sketch_pages(projectId)")
            }
        }

        @Volatile private var INSTANCE: SeamworkDatabase? = null

        fun get(context: Context): SeamworkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SeamworkDatabase::class.java,
                    "seamwork-studio.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
