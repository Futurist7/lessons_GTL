package com.linguascreen.data.repo

import com.linguascreen.data.db.HistoryDao
import com.linguascreen.data.db.HistoryEntity
import com.linguascreen.domain.TranslatedBlock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only translation history (Room). Nothing here ever leaves the device. */
@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {
    fun all(): Flow<List<HistoryEntity>> = dao.observeAll()
    fun recent(limit: Int = 5): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    suspend fun save(blocks: List<TranslatedBlock>, sourceApp: String?) {
        val now = System.currentTimeMillis()
        val items = blocks
            .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
            .distinctBy { it.original }
            .map { HistoryEntity(original = it.original, translation = it.translation, createdAt = now, sourceApp = sourceApp) }
        if (items.isEmpty()) return
        dao.insertAll(items)
        dao.prune(MAX_ENTRIES)
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()

    private companion object {
        const val MAX_ENTRIES = 2000
    }
}
