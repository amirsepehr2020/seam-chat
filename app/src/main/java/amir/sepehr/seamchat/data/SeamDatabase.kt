package amir.sepehr.seamchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import amir.sepehr.seamchat.chat.MessageDao
import amir.sepehr.seamchat.chat.MessageEntity

@Database(entities = [MessageEntity::class], version = 1, exportSchema = true)
abstract class SeamDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: SeamDatabase? = null

        fun get(context: Context): SeamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SeamDatabase::class.java,
                    "seam-chat.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
