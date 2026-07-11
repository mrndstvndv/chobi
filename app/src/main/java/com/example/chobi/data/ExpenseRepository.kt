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
    suspend fun importData(
        categories: List<Category>,
        expenses: List<BackupHelper.BackupExpense>,
        budgets: List<Budget>,
        overwrite: Boolean
    )

    fun getAllBudgets(): Flow<List<Budget>>
    fun getActiveBudget(): Flow<Budget?>
    suspend fun createBudget(title: String, limitAmount: Double, startTimestamp: Long): Long
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
}

class DefaultExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val database: AppDatabase
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
        database.runInTransaction {
            updateCategoryInternal(category, oldName)
        }
    }

    private suspend fun updateCategoryInternal(category: Category, oldName: String) {
        if (category.name != oldName) expenseDao.updateExpenseCategory(oldName, category.name)
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

    override suspend fun importData(
        categories: List<Category>,
        expenses: List<BackupHelper.BackupExpense>,
        budgets: List<Budget>,
        overwrite: Boolean
    ) {
        database.runInTransaction {
            importDataInternal(categories, expenses, budgets, overwrite)
        }
    }

    private suspend fun importDataInternal(
        categories: List<Category>,
        expenses: List<BackupHelper.BackupExpense>,
        budgets: List<Budget>,
        overwrite: Boolean
    ) {
        if (overwrite) {
            clearAllData()
            for (cat in categories) {
                categoryDao.insertCategory(cat.copy(id = 0))
            }
            val newBudgetIdsByKey = mutableMapOf<String, Long>()
            budgets.forEachIndexed { index, bud ->
                val newId = budgetDao.insertBudget(bud.copy(id = 0))
                newBudgetIdsByKey[budgetKey(index, bud)] = newId
                newBudgetIdsByKey["legacy-start:${bud.startTimestamp}"] = newId
            }
            for (backupExpense in expenses) {
                val mappedBudgetId = backupExpense.budgetKey?.let {
                    newBudgetIdsByKey[it] ?: error("Backup references an unknown budget")
                }
                expenseDao.insertExpense(backupExpense.expense.copy(id = 0, budgetId = mappedBudgetId))
            }
        } else {
            // Merge mode
            val existingCats = categoryDao.getAllCategories().first()
            val existingNames = existingCats.mapTo(mutableSetOf()) { it.name.trim().lowercase() }
            for (cat in categories) {
                if (cat.name.trim().lowercase() !in existingNames) {
                    categoryDao.insertCategory(cat.copy(id = 0))
                    existingNames += cat.name.trim().lowercase()
                }
            }

            val existingBudgets = budgetDao.getAllBudgets().first()
            val newBudgetIdsByKey = mutableMapOf<String, Long>()
            budgets.forEachIndexed { index, bud ->
                val existing = existingBudgets.firstOrNull {
                    it.title.trim().lowercase() == bud.title.trim().lowercase() &&
                    it.limitAmount == bud.limitAmount &&
                    it.startTimestamp == bud.startTimestamp
                }
                val newId = if (existing != null) {
                    existing.id
                } else {
                    budgetDao.insertBudget(bud.copy(id = 0))
                }
                newBudgetIdsByKey[budgetKey(index, bud)] = newId
                newBudgetIdsByKey["legacy-start:${bud.startTimestamp}"] = newId
            }

            val existingExpenses = expenseDao.getAllExpenses().first()
            val importedExpenseKeys = existingExpenses
                .mapTo(mutableSetOf(), ::expenseKey)
            for (backupExpense in expenses) {
                val exp = backupExpense.expense
                val mappedBudgetId = backupExpense.budgetKey?.let {
                    newBudgetIdsByKey[it] ?: error("Backup references an unknown budget")
                }
                if (importedExpenseKeys.add(expenseKey(exp))) {
                    expenseDao.insertExpense(exp.copy(id = 0, budgetId = mappedBudgetId))
                }
            }
        }
    }

    override fun getAllBudgets(): Flow<List<Budget>> = budgetDao.getAllBudgets()

    override fun getActiveBudget(): Flow<Budget?> = budgetDao.getActiveBudget()

    override suspend fun createBudget(title: String, limitAmount: Double, startTimestamp: Long): Long {
        val operation: suspend () -> Long = {
            budgetDao.getAllBudgets().first()
                .filter { it.endTimestamp == null }
                .forEach { budgetDao.updateBudget(it.copy(endTimestamp = startTimestamp)) }
            budgetDao.insertBudget(
                Budget(
                    title = title,
                    limitAmount = limitAmount,
                    startTimestamp = startTimestamp
                )
            )
        }
        return database.runInTransaction { operation() }
    }

    override suspend fun insertBudget(budget: Budget): Long = budgetDao.insertBudget(budget)

    override suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)

    override suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    private fun budgetKey(index: Int, budget: Budget): String =
        "$index:${budget.title.trim().lowercase()}:${budget.startTimestamp}:${budget.limitAmount}"

    private fun expenseKey(expense: Expense): String =
        "${expense.title.trim().lowercase()}|${expense.amount}|${expense.category.trim().lowercase()}|${expense.timestamp}"
}
