package com.example.chobi.data

import org.json.JSONArray
import org.json.JSONObject

object BackupHelper {
    fun exportToJson(categories: List<Category>, expenses: List<Expense>): String {
        val root = JSONObject()
        root.put("version", 1)
        
        val categoriesArray = JSONArray()
        for (cat in categories) {
            val catObj = JSONObject()
            catObj.put("name", cat.name)
            catObj.put("iconName", cat.iconName)
            catObj.put("colorHex", cat.colorHex)
            categoriesArray.put(catObj)
        }
        root.put("categories", categoriesArray)
        
        val expensesArray = JSONArray()
        for (exp in expenses) {
            val expObj = JSONObject()
            expObj.put("title", exp.title)
            expObj.put("amount", exp.amount)
            expObj.put("timestamp", exp.timestamp)
            expObj.put("category", exp.category)
            expensesArray.put(expObj)
        }
        root.put("expenses", expensesArray)
        
        return root.toString(2)
    }
    
    fun importFromJson(jsonString: String): Pair<List<Category>, List<Expense>> {
        val root = JSONObject(jsonString)
        val categories = mutableListOf<Category>()
        val expenses = mutableListOf<Expense>()
        
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
                expenses.add(
                    Expense(
                        title = expObj.getString("title"),
                        amount = expObj.getDouble("amount"),
                        timestamp = expObj.getLong("timestamp"),
                        category = expObj.getString("category")
                    )
                )
            }
        }
        
        return Pair(categories, expenses)
    }
}
