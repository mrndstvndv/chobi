package com.example.chobi.data

import org.json.JSONArray
import org.json.JSONObject

object BackupHelper {
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
        
        val budgetsMap = budgets.associateBy { it.id }
        val expensesArray = JSONArray()
        for (exp in expenses) {
            val expObj = JSONObject()
            expObj.put("title", exp.title)
            expObj.put("amount", exp.amount)
            expObj.put("timestamp", exp.timestamp)
            expObj.put("category", exp.category)
            val budget = exp.budgetId?.let { budgetsMap[it] }
            if (budget != null) {
                expObj.put("budgetStartTimestamp", budget.startTimestamp)
            }
            expensesArray.put(expObj)
        }
        root.put("expenses", expensesArray)

        val budgetsArray = JSONArray()
        for (bud in budgets) {
            val budObj = JSONObject()
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
    
    fun importFromJson(jsonString: String): Triple<List<Category>, List<Pair<Expense, Long?>>, List<Budget>> {
        val root = JSONObject(jsonString)
        val categories = mutableListOf<Category>()
        val expenses = mutableListOf<Pair<Expense, Long?>>()
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
                val budgetStartTimestamp = if (expObj.has("budgetStartTimestamp")) expObj.getLong("budgetStartTimestamp") else null
                val expense = Expense(
                    title = expObj.getString("title"),
                    amount = expObj.getDouble("amount"),
                    timestamp = expObj.getLong("timestamp"),
                    category = expObj.getString("category")
                )
                expenses.add(Pair(expense, budgetStartTimestamp))
            }
        }

        if (root.has("budgets")) {
            val budgetsArray = root.getJSONArray("budgets")
            for (i in 0 until budgetsArray.length()) {
                val budObj = budgetsArray.getJSONObject(i)
                val endTimestamp = if (budObj.has("endTimestamp")) budObj.getLong("endTimestamp") else null
                budgets.add(
                    Budget(
                        title = budObj.getString("title"),
                        limitAmount = budObj.getDouble("limitAmount"),
                        startTimestamp = budObj.getLong("startTimestamp"),
                        endTimestamp = endTimestamp
                    )
                )
            }
        }
        
        return Triple(categories, expenses, budgets)
    }
}
