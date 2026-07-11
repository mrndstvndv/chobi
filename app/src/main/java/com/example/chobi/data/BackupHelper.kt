package com.example.chobi.data

import org.json.JSONArray
import org.json.JSONObject

object BackupHelper {
    data class BackupExpense(
        val expense: Expense,
        val budgetKey: String?
    )

    fun exportToJson(categories: List<Category>, expenses: List<Expense>, budgets: List<Budget>): String {
        val root = JSONObject()
        root.put("version", 2)
        
        val categoriesArray = JSONArray()
        for (cat in categories) {
            val catObj = JSONObject()
            catObj.put("name", cat.name)
            catObj.put("iconName", cat.iconName)
            catObj.put("colorHex", cat.colorHex)
            categoriesArray.put(catObj)
        }
        root.put("categories", categoriesArray)
        
        val budgetsMap = budgets.withIndex().associate { (index, budget) ->
            budget.id to budgetKey(index, budget)
        }
        val expensesArray = JSONArray()
        for (exp in expenses) {
            val expObj = JSONObject()
            expObj.put("title", exp.title)
            expObj.put("amount", exp.amount)
            expObj.put("timestamp", exp.timestamp)
            expObj.put("category", exp.category)
            val budgetKey = exp.budgetId?.let { budgetsMap[it] }
            if (budgetKey != null) {
                expObj.put("budgetKey", budgetKey)
            }
            expensesArray.put(expObj)
        }
        root.put("expenses", expensesArray)

        val budgetsArray = JSONArray()
        budgets.forEachIndexed { index, bud ->
            val budObj = JSONObject()
            budObj.put("backupKey", budgetKey(index, bud))
            budObj.put("title", bud.title)
            budObj.put("limitAmount", bud.limitAmount)
            budObj.put("startTimestamp", bud.startTimestamp)
            if (bud.endTimestamp != null) {
                budObj.put("endTimestamp", bud.endTimestamp)
            }
            budgetsArray.put(budObj)
        }
        root.put("budgets", budgetsArray)
        
        return root.toString(2)
    }
    
    fun importFromJson(jsonString: String): Triple<List<Category>, List<BackupExpense>, List<Budget>> {
        val root = JSONObject(jsonString)
        require(root.optInt("version", 0) in 1..2) { "Unsupported backup version" }
        val categories = mutableListOf<Category>()
        val expenses = mutableListOf<BackupExpense>()
        val budgets = mutableListOf<Budget>()
        
        if (root.has("categories")) {
            val categoriesArray = root.getJSONArray("categories")
            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.getJSONObject(i)
                categories.add(
                    Category(
                        name = catObj.getString("name"),
                        iconName = catObj.getString("iconName"),
                        colorHex = catObj.getString("colorHex")
                    )
                )
            }
        }
        
        if (root.has("expenses")) {
            val expensesArray = root.getJSONArray("expenses")
            for (i in 0 until expensesArray.length()) {
                val expObj = expensesArray.getJSONObject(i)
                val budgetKey = expObj.optString("budgetKey", null)
                    ?: expObj.optLong("budgetStartTimestamp", Long.MIN_VALUE)
                        .takeUnless { it == Long.MIN_VALUE }
                        ?.let { "legacy-start:$it" }
                val expense = Expense(
                    title = expObj.getString("title"),
                    amount = expObj.getDouble("amount"),
                    timestamp = expObj.getLong("timestamp"),
                    category = expObj.getString("category")
                )
                require(expense.title.isNotBlank()) { "Expense title cannot be blank" }
                require(expense.amount.isFinite()) { "Expense amount must be finite" }
                expenses.add(BackupExpense(expense, budgetKey))
            }
        }

        if (root.has("budgets")) {
            val budgetsArray = root.getJSONArray("budgets")
            for (i in 0 until budgetsArray.length()) {
                val budObj = budgetsArray.getJSONObject(i)
                val endTimestamp = if (budObj.has("endTimestamp")) budObj.getLong("endTimestamp") else null
                val budget = Budget(
                    title = budObj.getString("title"),
                    limitAmount = budObj.getDouble("limitAmount"),
                    startTimestamp = budObj.getLong("startTimestamp"),
                    endTimestamp = endTimestamp
                )
                require(budget.title.isNotBlank()) { "Budget title cannot be blank" }
                require(budget.limitAmount.isFinite() && budget.limitAmount >= 0.0) {
                    "Budget limit must be a non-negative finite number"
                }
                budgets.add(budget)
            }
        }
        
        return Triple(categories, expenses, budgets)
    }

    private fun budgetKey(index: Int, budget: Budget): String =
        "$index:${budget.title.trim().lowercase()}:${budget.startTimestamp}:${budget.limitAmount}"
}
