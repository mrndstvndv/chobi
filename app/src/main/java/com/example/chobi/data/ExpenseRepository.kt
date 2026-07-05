package com.example.chobi.data

import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)

    fun getAllCategories(): Flow<List<Category>>
    suspend fun insertCategory(category: Category)
    suspend fun updateCategory(category: Category, oldName: String)
    suspend fun deleteCategory(category: Category)
    suspend fun prepopulateDefaultCategories()
}

class DefaultExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : ExpenseRepository {
    override fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()

    override suspend fun insertExpense(expense: Expense) {
        expenseDao.insertExpense(expense)
    }

    override suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    override fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    override suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    override suspend fun updateCategory(category: Category, oldName: String) {
        if (category.name != oldName) {
            expenseDao.updateExpenseCategory(oldName, category.name)
        }
        categoryDao.insertCategory(category)
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    override suspend fun prepopulateDefaultCategories() {
        val defaults = listOf(
            Category(name = "Food", iconName = "Restaurant", colorHex = "#FF9800"),
            Category(name = "Transport", iconName = "DirectionsCar", colorHex = "#2196F3"),
            Category(name = "Entertainment", iconName = "Movie", colorHex = "#9C27B0"),
            Category(name = "Health", iconName = "LocalHospital", colorHex = "#F44336"),
            Category(name = "Education", iconName = "School", colorHex = "#3F51B5")
        )
        for (category in defaults) {
            categoryDao.insertCategory(category)
        }
    }
}
