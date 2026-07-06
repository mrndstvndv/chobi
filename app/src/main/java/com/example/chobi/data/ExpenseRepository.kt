package com.example.chobi.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)

    fun getAllCategories(): Flow<List<Category>>
    suspend fun insertCategory(category: Category)
    suspend fun updateCategory(category: Category, oldName: String)
    suspend fun deleteCategory(category: Category)
    suspend fun prepopulateDefaultCategories()
    suspend fun clearAllData()
    suspend fun importData(categories: List<Category>, expenses: List<Pair<Expense, Long?>>, budgets: List<Budget>, overwrite: Boolean)

    fun getAllBudgets(): Flow<List<Budget>>
    fun getActiveBudget(): Flow<Budget?>
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
}

class DefaultExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao
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

    override suspend fun clearAllData() {
        expenseDao.deleteAllExpenses()
        categoryDao.deleteAllCategories()
        budgetDao.deleteAllBudgets()
    }

    override suspend fun importData(categories: List<Category>, expenses: List<Pair<Expense, Long?>>, budgets: List<Budget>, overwrite: Boolean) {
        if (overwrite) {
            clearAllData()
            for (cat in categories) {
                categoryDao.insertCategory(cat.copy(id = 0))
            }
            val newBudgetIdsByStart = mutableMapOf<Long, Long>()
            for (bud in budgets) {
                val newId = budgetDao.insertBudget(bud.copy(id = 0))
                newBudgetIdsByStart[bud.startTimestamp] = newId
            }
            for (pair in expenses) {
                val exp = pair.first
                val budgetStart = pair.second
                val mappedBudgetId = budgetStart?.let { newBudgetIdsByStart[it] }
                expenseDao.insertExpense(exp.copy(id = 0, budgetId = mappedBudgetId))
            }
        } else {
            // Merge mode
            val existingCats = categoryDao.getAllCategories().first()
            val existingNames = existingCats.map { it.name.lowercase() }.toSet()
            for (cat in categories) {
                if (cat.name.lowercase() !in existingNames) {
                    categoryDao.insertCategory(cat.copy(id = 0))
                }
            }

            val existingBudgets = budgetDao.getAllBudgets().first()
            val newBudgetIdsByStart = mutableMapOf<Long, Long>()
            for (bud in budgets) {
                val existing = existingBudgets.firstOrNull {
                    it.title.lowercase() == bud.title.lowercase() &&
                    it.limitAmount == bud.limitAmount &&
                    it.startTimestamp == bud.startTimestamp
                }
                val newId = if (existing != null) {
                    existing.id
                } else {
                    budgetDao.insertBudget(bud.copy(id = 0))
                }
                newBudgetIdsByStart[bud.startTimestamp] = newId
            }

            val existingExpenses = expenseDao.getAllExpenses().first()
            for (pair in expenses) {
                val exp = pair.first
                val budgetStart = pair.second
                val isDuplicate = existingExpenses.any {
                    it.title.lowercase() == exp.title.lowercase() &&
                    it.amount == exp.amount &&
                    it.category.lowercase() == exp.category.lowercase() &&
                    it.timestamp == exp.timestamp
                }
                if (!isDuplicate) {
                    val mappedBudgetId = budgetStart?.let { newBudgetIdsByStart[it] }
                    expenseDao.insertExpense(exp.copy(id = 0, budgetId = mappedBudgetId))
                }
            }
        }
    }

    override fun getAllBudgets(): Flow<List<Budget>> = budgetDao.getAllBudgets()

    override fun getActiveBudget(): Flow<Budget?> = budgetDao.getActiveBudget()

    override suspend fun insertBudget(budget: Budget): Long = budgetDao.insertBudget(budget)

    override suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)

    override suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)
}
