package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_ips")
data class IpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ip: String,
    val label: String,
    val latency: Int = -1, // -1: untested, -2: offline, >0: latency in ms
    val isCustom: Boolean = true,
    val isSelected: Boolean = false,
    val category: String = "Custom"
)
