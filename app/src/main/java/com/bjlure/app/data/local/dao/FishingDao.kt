package com.bjlure.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bjlure.app.data.local.entity.FishEntity
import com.bjlure.app.data.local.entity.LureEntity
import com.bjlure.app.data.local.entity.SpotEntity
import com.bjlure.app.data.local.entity.ZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FishingDao {

    // ---------------- 钓点 ----------------

    /** 只返回启用中且免费的钓点 —— 免费筛选在 SQL 层就做掉，不给上层留后门 */
    @Query("SELECT * FROM spots WHERE isActive = 1 AND accessType = 'FREE' ORDER BY name")
    fun observeFreeSpots(): Flow<List<SpotEntity>>

    @Query("SELECT * FROM spots WHERE isActive = 1 AND accessType = 'FREE' ORDER BY name")
    suspend fun getFreeSpots(): List<SpotEntity>

    @Query("SELECT * FROM spots WHERE id = :id LIMIT 1")
    suspend fun getSpot(id: String): SpotEntity?

    @Query("SELECT * FROM spots WHERE id = :id LIMIT 1")
    fun observeSpot(id: String): Flow<SpotEntity?>

    /** 按鱼种筛选：targetFishIds 是逗号分隔串，用 LIKE 匹配 */
    @Query(
        """
        SELECT * FROM spots
        WHERE isActive = 1 AND accessType = 'FREE'
          AND (targetFishIds LIKE '%' || :fishId || '%')
        ORDER BY name
        """
    )
    suspend fun getFreeSpotsByFish(fishId: String): List<SpotEntity>

    @Query("SELECT COUNT(*) FROM spots")
    suspend fun spotCount(): Int

    // ---------------- 鱼种 / 拟饵 ----------------

    @Query("SELECT * FROM fish ORDER BY abundance DESC, name")
    fun observeFish(): Flow<List<FishEntity>>

    @Query("SELECT * FROM fish ORDER BY abundance DESC, name")
    suspend fun getFish(): List<FishEntity>

    @Query("SELECT * FROM lures ORDER BY name")
    suspend fun getLures(): List<LureEntity>

    @Query("SELECT * FROM lures WHERE targetFishIds LIKE '%' || :fishId || '%' ORDER BY name")
    suspend fun getLuresForFish(fishId: String): List<LureEntity>

    // ---------------- 禁钓区 ----------------

    @Query("SELECT * FROM zones")
    suspend fun getZones(): List<ZoneEntity>

    @Query("SELECT * FROM zones")
    fun observeZones(): Flow<List<ZoneEntity>>

    @Query("SELECT COUNT(*) FROM zones")
    suspend fun zoneCount(): Int

    // ---------------- 导入 ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpots(spots: List<SpotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFish(fish: List<FishEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLures(lures: List<LureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<ZoneEntity>)

    @Query("DELETE FROM spots")
    suspend fun clearSpots()

    @Query("DELETE FROM fish")
    suspend fun clearFish()

    @Query("DELETE FROM lures")
    suspend fun clearLures()

    @Query("DELETE FROM zones")
    suspend fun clearZones()

    /** 种子数据是整包替换的，所以放在一个事务里，避免中途失败留下半套数据 */
    @Transaction
    suspend fun replaceAll(
        spots: List<SpotEntity>,
        fish: List<FishEntity>,
        lures: List<LureEntity>,
        zones: List<ZoneEntity>,
    ) {
        clearSpots(); clearFish(); clearLures(); clearZones()
        insertFish(fish); insertLures(lures); insertZones(zones); insertSpots(spots)
    }
}
