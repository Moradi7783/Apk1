package com.example.data

import kotlinx.coroutines.flow.Flow

class IpRepository(private val ipDao: IpDao) {
    val allIps: Flow<List<IpEntity>> = ipDao.getAllIps()
    val selectedIpFlow: Flow<IpEntity?> = ipDao.getSelectedIpFlow()

    suspend fun getSelectedIp(): IpEntity? {
        return ipDao.getSelectedIp()
    }

    suspend fun insert(ip: IpEntity): Long {
        return ipDao.insertIp(ip)
    }

    suspend fun insertAll(ips: List<IpEntity>) {
        ipDao.insertIps(ips)
    }

    suspend fun update(ip: IpEntity) {
        ipDao.updateIp(ip)
    }

    suspend fun delete(ip: IpEntity) {
        ipDao.deleteIp(ip)
    }

    suspend fun deleteById(id: Long) {
        ipDao.deleteIpById(id)
    }

    suspend fun selectIp(id: Long) {
        ipDao.selectIp(id)
    }

    suspend fun getCount(): Int {
        return ipDao.getCount()
    }
}
