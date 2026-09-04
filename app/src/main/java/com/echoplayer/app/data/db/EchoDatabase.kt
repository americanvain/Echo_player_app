package com.echoplayer.app.data.db

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun practiceDao(): PracticeDao
    abstract fun issueDao(): IssueDao
    abstract fun vocabDao(): VocabDao

    companion object {
        fun build(context: Context): EchoDatabase =
            Room.databaseBuilder(context.applicationContext, EchoDatabase::class.java, "echo_player.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
