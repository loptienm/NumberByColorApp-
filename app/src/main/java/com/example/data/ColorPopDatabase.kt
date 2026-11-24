package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
  @PrimaryKey val id: String,
  val name: String,
  val avatar: String,
  val challengeMode: String,
  val soundEnabled: Boolean,
  val createdAt: Long = System.currentTimeMillis(),
  val customLevelId: String = "",
)

@Entity(tableName = "picture_progress", primaryKeys = ["profileId", "pictureId"])
data class PictureProgressEntity(
  val profileId: String,
  val pictureId: String,
  val paintedGrid: String,
  val isCompleted: Boolean,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_templates")
data class CustomTemplateEntity(
  @PrimaryKey val id: String,
  val title: String,
  val gridWidth: Int,
  val gridHeight: Int,
  val paletteSerialized: String,
  val gridSerialized: String,
  val framesSerialized: String = "",
  val frameMs: Int = 400,
  val version: Int = 1,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "synced_templates")
data class SyncedTemplateEntity(
  @PrimaryKey val id: String,
  val title: String,
  val gridWidth: Int,
  val gridHeight: Int,
  val paletteSerialized: String,
  val gridSerialized: String,
  val framesSerialized: String = "",
  val frameMs: Int = 400,
  val version: Int = 1,
  val sourceFileName: String = "",
  val sourceSha: String = "",
  val syncedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sticker_pages", primaryKeys = ["profileId", "pageId"])
data class StickerPageEntity(
  val profileId: String,
  val pageId: String,
  val pageOrder: Int,
  val paperIndex: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sticker_placements")
data class PlacedStickerEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val pageId: String,
  val templateId: String,
  val xFraction: Float,
  val yFraction: Float,
  val scale: Float,
  val zOrder: Long,
  val isMirrored: Boolean = false,
)

@Entity(tableName = "custom_questions")
data class CustomQuestionEntity(
  @PrimaryKey val id: String,
  val prompt: String,
  val clueItemsJson: String = "[]",
  val correctAnswer: String,
  val wrongAnswersJson: String,
  val levelsCsv: String,
  val customLevelIdsCsv: String = "",
  val isSentence: Boolean = false,
  val version: Int = 1,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "synced_questions")
data class SyncedQuestionEntity(
  @PrimaryKey val id: String,
  val prompt: String,
  val clueItemsJson: String = "[]",
  val correctAnswer: String,
  val wrongAnswersJson: String,
  val levelsCsv: String,
  val customLevelIdsCsv: String = "",
  val isSentence: Boolean = false,
  val version: Int = 1,
  val sourceFileName: String = "",
  val sourceSha: String = "",
  val syncedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_levels")
data class CustomEducationLevelEntity(
  @PrimaryKey val id: String,
  val name: String,
  val description: String = "",
  val topicsCsv: String = "",
  val version: Int = 1,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "synced_levels")
data class SyncedEducationLevelEntity(
  @PrimaryKey val id: String,
  val name: String,
  val description: String = "",
  val topicsCsv: String = "",
  val version: Int = 1,
  val sourceFileName: String = "",
  val sourceSha: String = "",
  val syncedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProfileDao {
  @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
  fun observeProfiles(): Flow<List<ProfileEntity>>

  @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
  suspend fun getProfilesOnce(): List<ProfileEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfile(profile: ProfileEntity)

  @Query("DELETE FROM profiles WHERE id = :profileId")
  suspend fun deleteProfile(profileId: String)

  @Query("UPDATE profiles SET customLevelId = :newLevelId WHERE customLevelId = :oldLevelId")
  suspend fun migrateCustomLevelId(oldLevelId: String, newLevelId: String)
}

@Dao
interface ProgressDao {
  @Query("SELECT * FROM picture_progress WHERE profileId = :profileId")
  fun observeProgressForProfile(profileId: String): Flow<List<PictureProgressEntity>>

  @Query("SELECT * FROM picture_progress WHERE profileId = :profileId")
  suspend fun getProgressOnce(profileId: String): List<PictureProgressEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProgress(progress: PictureProgressEntity)

  @Query("DELETE FROM picture_progress WHERE profileId = :profileId AND pictureId = :pictureId")
  suspend fun deleteProgress(profileId: String, pictureId: String)

  @Query("DELETE FROM picture_progress WHERE profileId = :profileId")
  suspend fun deleteAllForProfile(profileId: String)

  @Query("DELETE FROM picture_progress WHERE pictureId = :pictureId")
  suspend fun deleteAllForPicture(pictureId: String)

  @Query("UPDATE OR IGNORE picture_progress SET pictureId = :newId WHERE pictureId = :oldId")
  suspend fun copyProgressToNewPictureId(oldId: String, newId: String)
}

@Dao
interface CustomTemplateDao {
  @Query("SELECT * FROM custom_templates ORDER BY createdAt DESC")
  fun observeCustomTemplates(): Flow<List<CustomTemplateEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomTemplate(template: CustomTemplateEntity)

  @Query("SELECT * FROM custom_templates")
  suspend fun getCustomTemplatesOnce(): List<CustomTemplateEntity>

  @Query("DELETE FROM custom_templates WHERE id = :templateId")
  suspend fun deleteCustomTemplate(templateId: String)
}

@Dao
interface SyncedTemplateDao {
  @Query("SELECT * FROM synced_templates ORDER BY syncedAt DESC")
  fun observeSyncedTemplates(): Flow<List<SyncedTemplateEntity>>

  @Query("SELECT * FROM synced_templates")
  suspend fun getSyncedTemplatesOnce(): List<SyncedTemplateEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncedTemplates(templates: List<SyncedTemplateEntity>)

  @Query("DELETE FROM synced_templates WHERE id NOT IN (:activeIds)")
  suspend fun deleteMissingSyncedTemplates(activeIds: List<String>)

  @Query("DELETE FROM synced_templates")
  suspend fun deleteAllSyncedTemplates()
}

@Dao
interface StickerDao {
  @Query("SELECT * FROM sticker_pages WHERE profileId = :profileId ORDER BY pageOrder ASC, createdAt ASC")
  fun observePagesForProfile(profileId: String): Flow<List<StickerPageEntity>>

  @Query("SELECT * FROM sticker_pages WHERE profileId = :profileId ORDER BY pageOrder ASC, createdAt ASC")
  suspend fun getPagesOnce(profileId: String): List<StickerPageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPage(page: StickerPageEntity)

  @Query("SELECT * FROM sticker_placements WHERE profileId = :profileId ORDER BY zOrder ASC")
  fun observeStickersForProfile(profileId: String): Flow<List<PlacedStickerEntity>>

  @Query("SELECT * FROM sticker_placements WHERE profileId = :profileId ORDER BY zOrder ASC")
  suspend fun getStickersOnce(profileId: String): List<PlacedStickerEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSticker(sticker: PlacedStickerEntity)

  @Query("DELETE FROM sticker_placements WHERE id = :stickerId")
  suspend fun deleteSticker(stickerId: String)

  @Query("DELETE FROM sticker_placements WHERE profileId = :profileId AND pageId = :pageId")
  suspend fun clearPageStickers(profileId: String, pageId: String)

  @Query("DELETE FROM sticker_placements WHERE profileId = :profileId")
  suspend fun deleteAllStickersForProfile(profileId: String)

  @Query("DELETE FROM sticker_pages WHERE profileId = :profileId")
  suspend fun deleteAllPagesForProfile(profileId: String)

  @Query("DELETE FROM sticker_placements WHERE templateId = :templateId")
  suspend fun deleteAllForTemplate(templateId: String)

  @Query("UPDATE sticker_placements SET templateId = :newId WHERE templateId = :oldId")
  suspend fun migrateTemplateId(oldId: String, newId: String)
}

@Dao
interface CustomQuestionDao {
  @Query("SELECT * FROM custom_questions ORDER BY createdAt DESC")
  fun observeCustomQuestions(): Flow<List<CustomQuestionEntity>>

  @Query("SELECT * FROM custom_questions")
  suspend fun getCustomQuestionsOnce(): List<CustomQuestionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomQuestion(question: CustomQuestionEntity)

  @Query("DELETE FROM custom_questions WHERE id = :questionId")
  suspend fun deleteCustomQuestion(questionId: String)
}

@Dao
interface SyncedQuestionDao {
  @Query("SELECT * FROM synced_questions ORDER BY syncedAt DESC")
  fun observeSyncedQuestions(): Flow<List<SyncedQuestionEntity>>

  @Query("SELECT * FROM synced_questions")
  suspend fun getSyncedQuestionsOnce(): List<SyncedQuestionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncedQuestions(questions: List<SyncedQuestionEntity>)

  @Query("DELETE FROM synced_questions WHERE id NOT IN (:activeIds)")
  suspend fun deleteMissingSyncedQuestions(activeIds: List<String>)
}

@Dao
interface CustomLevelDao {
  @Query("SELECT * FROM custom_levels ORDER BY createdAt DESC")
  fun observeCustomLevels(): Flow<List<CustomEducationLevelEntity>>

  @Query("SELECT * FROM custom_levels")
  suspend fun getCustomLevelsOnce(): List<CustomEducationLevelEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomLevel(level: CustomEducationLevelEntity)

  @Query("DELETE FROM custom_levels WHERE id = :levelId")
  suspend fun deleteCustomLevel(levelId: String)
}

@Dao
interface SyncedLevelDao {
  @Query("SELECT * FROM synced_levels ORDER BY syncedAt DESC")
  fun observeSyncedLevels(): Flow<List<SyncedEducationLevelEntity>>

  @Query("SELECT * FROM synced_levels")
  suspend fun getSyncedLevelsOnce(): List<SyncedEducationLevelEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncedLevels(levels: List<SyncedEducationLevelEntity>)

  @Query("DELETE FROM synced_levels WHERE id NOT IN (:activeIds)")
  suspend fun deleteMissingSyncedLevels(activeIds: List<String>)
}

@Database(
  entities =
    [
      ProfileEntity::class,
      PictureProgressEntity::class,
      CustomTemplateEntity::class,
      SyncedTemplateEntity::class,
      StickerPageEntity::class,
      PlacedStickerEntity::class,
      CustomQuestionEntity::class,
      SyncedQuestionEntity::class,
      CustomEducationLevelEntity::class,
      SyncedEducationLevelEntity::class,
    ],
  version = 7,
  exportSchema = false,
)
abstract class ColorPopDatabase : RoomDatabase() {
  abstract fun profileDao(): ProfileDao

  abstract fun progressDao(): ProgressDao

  abstract fun customTemplateDao(): CustomTemplateDao

  abstract fun syncedTemplateDao(): SyncedTemplateDao

  abstract fun stickerDao(): StickerDao

  abstract fun customQuestionDao(): CustomQuestionDao

  abstract fun syncedQuestionDao(): SyncedQuestionDao

  abstract fun customLevelDao(): CustomLevelDao

  abstract fun syncedLevelDao(): SyncedLevelDao

  companion object {
    @Volatile private var instance: ColorPopDatabase? = null

    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE custom_templates ADD COLUMN framesSerialized TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE custom_templates ADD COLUMN frameMs INTEGER NOT NULL DEFAULT 400"
          )
        }
      }

    private val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sticker_pages` (
              `profileId` TEXT NOT NULL,
              `pageId` TEXT NOT NULL,
              `pageOrder` INTEGER NOT NULL,
              `paperIndex` INTEGER NOT NULL DEFAULT 0,
              `createdAt` INTEGER NOT NULL,
              PRIMARY KEY(`profileId`, `pageId`)
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sticker_placements` (
              `id` TEXT NOT NULL,
              `profileId` TEXT NOT NULL,
              `pageId` TEXT NOT NULL,
              `templateId` TEXT NOT NULL,
              `xFraction` REAL NOT NULL,
              `yFraction` REAL NOT NULL,
              `scale` REAL NOT NULL,
              `zOrder` INTEGER NOT NULL,
              `isMirrored` INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
        }
      }

    private val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE sticker_placements ADD COLUMN isMirrored INTEGER NOT NULL DEFAULT 0"
          )
        }
      }

    private val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE custom_templates ADD COLUMN version INTEGER NOT NULL DEFAULT 1"
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `synced_templates` (
              `id` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `gridWidth` INTEGER NOT NULL,
              `gridHeight` INTEGER NOT NULL,
              `paletteSerialized` TEXT NOT NULL,
              `gridSerialized` TEXT NOT NULL,
              `framesSerialized` TEXT NOT NULL DEFAULT '',
              `frameMs` INTEGER NOT NULL DEFAULT 400,
              `version` INTEGER NOT NULL DEFAULT 1,
              `sourceFileName` TEXT NOT NULL DEFAULT '',
              `sourceSha` TEXT NOT NULL DEFAULT '',
              `syncedAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
        }
      }

    private val MIGRATION_5_6 =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_questions` (
              `id` TEXT NOT NULL,
              `prompt` TEXT NOT NULL,
              `clueItemsJson` TEXT NOT NULL DEFAULT '[]',
              `correctAnswer` TEXT NOT NULL,
              `wrongAnswersJson` TEXT NOT NULL,
              `levelsCsv` TEXT NOT NULL,
              `isSentence` INTEGER NOT NULL DEFAULT 0,
              `version` INTEGER NOT NULL DEFAULT 1,
              `createdAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `synced_questions` (
              `id` TEXT NOT NULL,
              `prompt` TEXT NOT NULL,
              `clueItemsJson` TEXT NOT NULL DEFAULT '[]',
              `correctAnswer` TEXT NOT NULL,
              `wrongAnswersJson` TEXT NOT NULL,
              `levelsCsv` TEXT NOT NULL,
              `isSentence` INTEGER NOT NULL DEFAULT 0,
              `version` INTEGER NOT NULL DEFAULT 1,
              `sourceFileName` TEXT NOT NULL DEFAULT '',
              `sourceSha` TEXT NOT NULL DEFAULT '',
              `syncedAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
        }
      }

    private val MIGRATION_6_7 =
      object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE profiles ADD COLUMN customLevelId TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "ALTER TABLE custom_questions ADD COLUMN customLevelIdsCsv TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            "ALTER TABLE synced_questions ADD COLUMN customLevelIdsCsv TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_levels` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `description` TEXT NOT NULL DEFAULT '',
              `topicsCsv` TEXT NOT NULL DEFAULT '',
              `version` INTEGER NOT NULL DEFAULT 1,
              `createdAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `synced_levels` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `description` TEXT NOT NULL DEFAULT '',
              `topicsCsv` TEXT NOT NULL DEFAULT '',
              `version` INTEGER NOT NULL DEFAULT 1,
              `sourceFileName` TEXT NOT NULL DEFAULT '',
              `sourceSha` TEXT NOT NULL DEFAULT '',
              `syncedAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
        }
      }

    fun getInstance(context: Context): ColorPopDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                ColorPopDatabase::class.java,
                "color_pop_offline.db",
              )
              .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
