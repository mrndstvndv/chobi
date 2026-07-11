package com.example.chobi.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupHelperTest {
    @Test
    fun exportUsesUniqueBudgetReferencesWhenStartTimesMatch() {
        val budgets = listOf(
            Budget(id = 1, title = "January", limitAmount = 100.0, startTimestamp = 1_000),
            Budget(id = 2, title = "February", limitAmount = 200.0, startTimestamp = 1_000)
        )
        val expenses = listOf(
            Expense(title = "Food", amount = 10.0, timestamp = 2_000, category = "Food", budgetId = 1),
            Expense(title = "Travel", amount = 20.0, timestamp = 3_000, category = "Transport", budgetId = 2)
        )

        val json = BackupHelper.exportToJson(emptyList(), expenses, budgets)
        val (_, importedExpenses, importedBudgets) = BackupHelper.importFromJson(json)

        assertEquals(2, importedBudgets.size)
        assertEquals("0:january:1000:100.0", importedExpenses[0].budgetKey)
        assertEquals("1:february:1000:200.0", importedExpenses[1].budgetKey)
    }

    @Test
    fun importRejectsUnsupportedVersions() {
        val error = runCatching {
            BackupHelper.importFromJson("""{"version":99}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
