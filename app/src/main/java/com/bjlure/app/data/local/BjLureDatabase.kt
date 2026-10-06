package com.bjlure.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bjlure.app.data.local.dao.FishingDao
import com.bjlure.app.data.local.entity.FishEntity
import com.bjlure.app.data.local.entity.LureEntity
import com.bjlure.app.data.local.entity.SpotEntity
import com.bjlure.app.data.local.entity.ZoneEntity

@Database(
    entities = [SpotEntity::class, FishEntity::class, LureEntity::class, ZoneEntity::class],
    // ⚠️ 改了任何 entity 的字段就必须把这里 +1。
    // 种子数据每次启动都会整包重灌，所以配 fallbackToDestructiveMigration 直接重建库就行；
    // 但版本号不加的话，装过旧版的机器一升级就会在开库时抛
    // 「Room cannot verify the data integrity」直接闪退。
    // v2：spots 表加 radiusMeters
    version = 2,
    exportSchema = false,
)
abstract class BjLureDatabase : RoomDatabase() {

    abstract fun fishingDao(): FishingDao

    companion object {
        private const val DB_NAME = "bjlure.db"

        @Volatile
        private var instance: BjLureDatabase? = null

        fun get(context: Context): BjLureDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BjLureDatabase::class.java,
                    DB_NAME,
                )
                    // 种子数据整包替换，结构变更时直接重建即可，不做迁移
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
