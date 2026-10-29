package com.brexa.app.data.local.dao

import androidx.room.*
import com.brexa.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type AND is_archived = 0 ORDER BY is_default DESC, sort_order ASC, name ASC")
    fun getActiveCategoriesByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY type ASC, is_default DESC, sort_order ASC")
    fun getAllActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type ASC, is_default DESC, sort_order ASC")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    fun getCategoryById(id: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryByIdSync(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE type = :type AND is_default = 0 AND is_archived = 0")
    suspend fun getCustomCategoriesCountByType(type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET is_archived = :isArchived WHERE id = :categoryId")
    suspend fun setArchived(categoryId: String, isArchived: Boolean)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategoriesSync()
}
