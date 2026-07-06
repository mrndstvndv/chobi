package com.example.chobi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long


    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("UPDATE expenses SET category = :newCategoryName WHERE category = :oldCategoryName")
    suspend fun updateExpenseCategory(oldCategoryName: String, newCategoryName: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}
