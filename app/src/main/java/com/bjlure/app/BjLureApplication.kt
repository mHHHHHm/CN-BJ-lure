package com.bjlure.app

import android.app.Application
import com.bjlure.app.data.local.BjLureDatabase
import com.bjlure.app.data.repo.FishingRepository
import com.bjlure.app.data.seed.SeedImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BjLureApplication : Application() {

    /** 全局作用域，只用于首次启动时的种子数据导入 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: BjLureDatabase by lazy { BjLureDatabase.get(this) }
    val repository: FishingRepository by lazy { FishingRepository(database.fishingDao()) }

    override fun onCreate() {
        super.onCreate()
        // 种子数据导入是幂等的：版本没变就直接返回
        appScope.launch {
            SeedImporter(this@BjLureApplication, database.fishingDao()).importIfNeeded()
        }
    }
}
