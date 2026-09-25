package com.primenotes.bd.domain.model

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val deletedAt: Long? = null,
    val revision: Long = 0
)
