package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.BehaviouralHypothesisDao
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity

class BehaviouralHypothesisRepository(private val dao: BehaviouralHypothesisDao) {
    suspend fun needsColdStart(): Boolean =
        runCatching { dao.getAll().isEmpty() }.getOrDefault(true)

    suspend fun save(hypothesis: BehaviouralHypothesisEntity) {
        runCatching { dao.insert(hypothesis) }
    }

    suspend fun getAll(): List<BehaviouralHypothesisEntity> =
        runCatching { dao.getAll() }.getOrDefault(emptyList())

    suspend fun getReflexAppPackage(): String? =
        runCatching { dao.getReflexAppPackage() }.getOrNull()
}