/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, UserAccountEntity::class],
    version = 7,
    exportSchema = false
)
abstract class SaifDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userAccountDao(): UserAccountDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep existing chat data on disk, but do not expose it to an unverified
                // account. New sessions are explicitly owned by a Firebase UID.
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun addIfMissing(table: String, column: String, definition: String) {
                    val columns = mutableSetOf<String>()
                    db.query("PRAGMA table_info($table)").use { cursor ->
                        val nameIndex = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
                    }
                    if (column !in columns) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
                }
                addIfMissing("chat_sessions", "ownerUid", "TEXT NOT NULL DEFAULT ''")
                addIfMissing("chat_sessions", "mode", "TEXT NOT NULL DEFAULT 'general'")
                addIfMissing("chat_messages", "isBookmarked", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentUris TEXT")
            }
        }

        @Volatile
        private var INSTANCE: SaifDatabase? = null

        fun getDatabase(context: Context): SaifDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaifDatabase::class.java,
                    "saif_ai_database_v2"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
