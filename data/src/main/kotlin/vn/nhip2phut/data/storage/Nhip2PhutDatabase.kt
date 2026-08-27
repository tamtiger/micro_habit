package vn.nhip2phut.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ClockStateEntity::class],
    version = Nhip2PhutDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class Nhip2PhutDatabase : RoomDatabase() {
    abstract fun clockStateDao(): ClockStateDao

    companion object {
        const val DATABASE_NAME = "nhip2phut.db"
        const val SCHEMA_VERSION = 1

        val MIGRATIONS: Array<Migration> = emptyArray()

        fun open(context: Context): Nhip2PhutDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                Nhip2PhutDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
