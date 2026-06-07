package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.IpDao
import com.example.data.IpEntity
import com.example.data.IpRepository
import com.example.data.LakDatabase
import com.example.service.LakDnsVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class LakDnsViewModel(application: Application) : AndroidViewModel(application) {

    private val ipRepository: IpRepository
    val allIps: StateFlow<List<IpEntity>>
    val selectedIpFlow: StateFlow<IpEntity?>

    // Re-expose VpnService live state flows
    val isVpnRunning = LakDnsVpnService.isRunning
    val activeIp = LakDnsVpnService.activeIp
    val activeLabel = LakDnsVpnService.activeLabel

    private val _isSpeedTesting = MutableStateFlow(false)
    val isSpeedTesting: StateFlow<Boolean> = _isSpeedTesting

    init {
        val database = LakDatabase.getInstance(application)
        ipRepository = IpRepository(database.ipDao())
        allIps = ipRepository.allIps.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        selectedIpFlow = ipRepository.selectedIpFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Pre-seed default IPs if empty
        viewModelScope.launch {
            if (ipRepository.getCount() == 0) {
                seedDefaultIps()
            }
        }
    }

    private suspend fun seedDefaultIps() {
        val defaults = listOf(
            IpEntity(ip = "185.143.232.122", label = "شکن (Shecan 1)", isCustom = false, isSelected = true, category = "شکن‌گر (Bypass)"),
            IpEntity(ip = "185.143.233.122", label = "شکن (Shecan 2)", isCustom = false, isSelected = false, category = "شکن‌گر (Bypass)"),
            IpEntity(ip = "10.201.201.201", label = "رادار بازی (Radar Game 1)", isCustom = false, isSelected = false, category = "بازی (Gaming)"),
            IpEntity(ip = "10.201.201.202", label = "رادار بازی (Radar Game 2)", isCustom = false, isSelected = false, category = "بازی (Gaming)"),
            IpEntity(ip = "94.232.174.194", label = "الکترو (Electro 1)", isCustom = false, isSelected = false, category = "شکن‌گر (Bypass)"),
            IpEntity(ip = "94.232.174.195", label = "الکترو (Electro 2)", isCustom = false, isSelected = false, category = "شکن‌گر (Bypass)"),
            IpEntity(ip = "78.157.108.108", label = "بگرد (Begard 1)", isCustom = false, isSelected = false, category = "شکن‌گر (Bypass)"),
            IpEntity(ip = "78.157.108.109", label = "بگرد (Begard 2)", isCustom = false, isSelected = false, category = "شکن‌گر (Bypass)"),
            IpEntity(ip = "1.1.1.1", label = "کلودفلر (Cloudflare)", isCustom = false, isSelected = false, category = "عمومی (Global)"),
            IpEntity(ip = "8.8.8.8", label = "گوگل (Google)", isCustom = false, isSelected = false, category = "عمومی (Global)"),
            IpEntity(ip = "9.9.9.9", label = "کواد ۹ (Quad9)", isCustom = false, isSelected = false, category = "عمومی (Global)")
        )
        ipRepository.insertAll(defaults)
    }

    fun addSingleIp(ipString: String, labelString: String, categoryString: String) {
        val trimmedIp = ipString.trim()
        val trimmedLabel = labelString.trim().ifEmpty { "آی‌پی دستی" }
        if (isValidIp(trimmedIp)) {
            viewModelScope.launch {
                val ipEntity = IpEntity(
                    ip = trimmedIp,
                    label = trimmedLabel,
                    isCustom = true,
                    isSelected = false,
                    category = categoryString.trim().ifEmpty { "شخصی (Custom)" }
                )
                ipRepository.insert(ipEntity)
            }
        }
    }

    fun addBulkIps(bulkText: String) {
        viewModelScope.launch {
            val lines = bulkText.lines()
            val listToInsert = mutableListOf<IpEntity>()
            for (line in lines) {
                if (line.isBlank()) continue
                // Standard parsers split by comma, semicolon, space or pipe
                val parts = line.split(Regex("[,;|\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isNotEmpty()) {
                    val ip = parts.firstOrNull { isValidIp(it) } ?: continue
                    val label = parts.firstOrNull { !isValidIp(it) } ?: "وارد شده گروهی"
                    listToInsert.add(
                        IpEntity(
                            ip = ip,
                            label = label,
                            isCustom = true,
                            isSelected = false,
                            category = "وارد شده (Group)"
                        )
                    )
                }
            }
            if (listToInsert.isNotEmpty()) {
                ipRepository.insertAll(listToInsert)
            }
        }
    }

    fun deleteIp(id: Long) {
        viewModelScope.launch {
            ipRepository.deleteById(id)
        }
    }

    fun selectIp(id: Long) {
        viewModelScope.launch {
            ipRepository.selectIp(id)
        }
    }

    fun testAllSpeeds() {
        if (_isSpeedTesting.value) return
        _isSpeedTesting.value = true
        viewModelScope.launch {
            val currentList = allIps.value
            val deferList = currentList.map { ipEntity ->
                async(Dispatchers.IO) {
                    val latency = runLatencyCheck(ipEntity.ip)
                    ipRepository.update(ipEntity.copy(latency = latency))
                }
            }
            deferList.awaitAll()
            _isSpeedTesting.value = false
        }
    }

    fun testSingleIpSpeed(ipEntity: IpEntity) {
        viewModelScope.launch {
            val latency = runLatencyCheck(ipEntity.ip)
            ipRepository.update(ipEntity.copy(latency = latency))
        }
    }

    private suspend fun runLatencyCheck(ip: String): Int {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                // Use port 53 (DNS resolution port) which is guaranteed to be open on DNS servers
                socket.connect(InetSocketAddress(ip, 53), 1500) // 1.5 seconds timeout
                val duration = (System.currentTimeMillis() - startTime).toInt()
                duration
            } catch (e: Exception) {
                // Secondary check: Let's see if we can do socket on standard port 80/443 if port 53 fails, just in case it is a generic CDN node
                try {
                    val altSocket = Socket()
                    altSocket.connect(InetSocketAddress(ip, 80), 800)
                    altSocket.close()
                    val duration = (System.currentTimeMillis() - startTime).toInt()
                    duration
                } catch (e2: Exception) {
                    -2 // Offline
                }
            } finally {
                try {
                    socket?.close()
                } catch (ignore: Exception) {
                }
            }
        }
    }

    fun isValidIp(ip: String): Boolean {
        val ipv4Pattern = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
        val domainPattern = "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}$"
        return ip.matches(Regex(ipv4Pattern)) || ip.matches(Regex(domainPattern))
    }
}
