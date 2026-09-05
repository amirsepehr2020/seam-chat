package amir.sepehr.seamchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import amir.sepehr.seamchat.chat.MessageDao
import amir.sepehr.seamchat.chat.MessageEntity

@Database(entities=[MessageEntity::class],version=2,exportSchema=true)
abstract class SeamDatabase:RoomDatabase(){
 abstract fun messageDao():MessageDao
 companion object{
  private val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE messages ADD COLUMN editedAt INTEGER");db.execSQL("ALTER TABLE messages ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE messages ADD COLUMN replyToMessageId TEXT")}}
  @Volatile private var INSTANCE:SeamDatabase?=null
  fun get(context:Context):SeamDatabase=INSTANCE?:synchronized(this){INSTANCE?:Room.databaseBuilder(context.applicationContext,SeamDatabase::class.java,"seam-chat.db").addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().also{INSTANCE=it}}
 }
}
