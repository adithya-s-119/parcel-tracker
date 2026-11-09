package com.parceltracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromStatus(status: OrderStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): OrderStatus =
        runCatching { OrderStatus.valueOf(value) }.getOrDefault(OrderStatus.UPCOMING)
}

@Database(
    entities = [DeliveryOrder::class, ScannedEmail::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parcel.db",
                ).build().also { instance = it }
            }
    }
}
