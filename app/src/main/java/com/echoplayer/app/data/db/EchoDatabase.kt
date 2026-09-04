package com.echoplayer.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MaterialEntity::class,
        SegmentEntity::class,
        UnitEntity::class,
        PracticeRecordEntity::class,
        IssueEntity::class,
        VocabEntity::class,
        PracticeSetEntity::class,
        ChatMessageEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun practiceDao(): PracticeDao
    abstract fun issueDao(): IssueDao
    abstract fun vocabDao(): VocabDao
    abstract fun practiceSetDao(): PracticeSetDao
    abstract fun chatDao(): ChatDao

    companion object {
        fun build(context: Context): EchoDatabase =
            Room.databaseBuilder(context.applicationContext, EchoDatabase::class.java, "echo_player.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
