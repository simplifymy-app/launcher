package app.simplifymy.launcher.core.workspace

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

object Container {
    const val DESKTOP = -100L
    const val DOCK = -101L
}

object ItemType {
    const val APP = 0
    const val SHORTCUT = 1
    const val FOLDER = 2
    const val WIDGET = 3
}

object ItemFlags {
    const val SWIPE_TO_OPEN = 1
}

@Serializable
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: Long,
    val page: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val type: Int,
    val app: String? = null,
    val packageName: String? = null,
    val shortcutId: String? = null,
    val user: Long = 0,
    val title: String? = null,
    val icon: String? = null,
    val swipe: String? = null,
    val widgetId: Int = -1,
    val provider: String? = null,
    val flags: Int = 0,
) {
    val isFolder: Boolean get() = type == ItemType.FOLDER
    val isWidget: Boolean get() = type == ItemType.WIDGET
    val isLaunchable: Boolean get() = type == ItemType.APP || type == ItemType.SHORTCUT
    fun has(flag: Int) = flags and flag != 0
}

@Serializable
@Entity(tableName = "app_overrides")
data class AppOverride(
    @PrimaryKey val key: String,
    val hidden: Boolean = false,
    val label: String? = null,
    val icon: String? = null,
    val launches: Int = 0,
    val lastLaunch: Long = 0,
)

object GroupKind {
    const val TAB = 0
    const val FOLDER = 1
}

@Serializable
@Entity(tableName = "drawer_groups")
data class DrawerGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: Int,
    val rank: Int = 0,
    val members: String = "",
) {
    val memberKeys: Set<String> get() = members.lineSequence().filter { it.isNotBlank() }.toSet()
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM items")
    fun items(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items")
    suspend fun allItems(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE container = :container")
    suspend fun inContainer(container: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): ItemEntity?

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Update
    suspend fun updateAll(items: List<ItemEntity>)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("SELECT * FROM app_overrides")
    fun overrides(): Flow<List<AppOverride>>

    @Query("SELECT * FROM app_overrides")
    suspend fun allOverrides(): List<AppOverride>

    @Query("SELECT * FROM app_overrides WHERE `key` = :key")
    suspend fun override(key: String): AppOverride?

    @Upsert
    suspend fun upsert(override: AppOverride)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverrides(overrides: List<AppOverride>)

    @Query("DELETE FROM app_overrides")
    suspend fun clearOverrides()

    @Query("SELECT * FROM drawer_groups ORDER BY rank, id")
    fun groups(): Flow<List<DrawerGroup>>

    @Query("SELECT * FROM drawer_groups ORDER BY rank, id")
    suspend fun allGroups(): List<DrawerGroup>

    @Upsert
    suspend fun upsert(group: DrawerGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<DrawerGroup>)

    @Delete
    suspend fun delete(group: DrawerGroup)

    @Query("DELETE FROM drawer_groups")
    suspend fun clearGroups()

    @Transaction
    suspend fun replaceAll(items: List<ItemEntity>, overrides: List<AppOverride>, groups: List<DrawerGroup>) {
        clearItems(); clearOverrides(); clearGroups()
        insertAll(items); upsertOverrides(overrides); upsertGroups(groups)
    }
}

@Database(entities = [ItemEntity::class, AppOverride::class, DrawerGroup::class], version = 1, exportSchema = true)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun dao(): WorkspaceDao

    companion object {
        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db").build()
    }
}
