package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IpDao {
    @Query("SELECT * FROM dns_ips ORDER BY label ASC")
    fun getAllIps(): Flow<List<IpEntity>>

    @Query("SELECT * FROM dns_ips WHERE isSelected = 1 LIMIT 1")
    fun getSelectedIpFlow(): Flow<IpEntity?>

    @Query("SELECT * FROM dns_ips WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedIp(): IpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIp(ip: IpEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIps(ips: List<IpEntity>)

    @Update
    suspend fun updateIp(ip: IpEntity)

    @Delete
    suspend fun deleteIp(ip: IpEntity)

    @Query("DELETE FROM dns_ips WHERE id = :id")
    suspend fun deleteIpById(id: Long)

    @Query("UPDATE dns_ips SET isSelected = 0")
    suspend fun deselectAll()

    @Transaction
    suspend fun selectIp(id: Long) {
        deselectAll()
        setIpSelected(id)
    }

    @Query("UPDATE dns_ips SET isSelected = 1 WHERE id = :id")
    suspend fun setIpSelected(id: Long)

    @Query("SELECT COUNT(*) FROM dns_ips")
    suspend fun getCount(): Int
}
