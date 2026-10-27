package com.controlefinanceiro.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observe(id: Long): Flow<UserEntity?>
}

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM people WHERE user_id = :userId ORDER BY sort_order, name")
    fun observePeople(userId: Long): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE user_id = :userId ORDER BY sort_order, name")
    suspend fun people(userId: Long): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY name")
    fun observeCategories(userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE user_id = :userId")
    suspend fun categories(userId: Long): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT * FROM payment_methods WHERE user_id = :userId ORDER BY name")
    fun observePaymentMethods(userId: Long): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE user_id = :userId")
    suspend fun paymentMethods(userId: Long): List<PaymentMethodEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentMethods(methods: List<PaymentMethodEntity>)

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethodEntity)

    @Query("SELECT * FROM cards WHERE user_id = :userId ORDER BY name")
    fun observeCards(userId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE user_id = :userId")
    suspend fun cards(userId: Long): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("SELECT * FROM budget_settings WHERE user_id = :userId")
    fun observeSettings(userId: Long): Flow<BudgetSettingsEntity?>

    @Upsert
    suspend fun upsertSettings(settings: BudgetSettingsEntity)
}

@Dao
interface MonthDao {
    @Query("SELECT * FROM months WHERE user_id = :userId ORDER BY year DESC, month DESC")
    fun observeMonths(userId: Long): Flow<List<MonthEntity>>

    @Query("SELECT * FROM months WHERE user_id = :userId AND year = :year AND month = :month")
    suspend fun find(userId: Long, year: Int, month: Int): MonthEntity?

    @Query("SELECT * FROM months WHERE user_id = :userId AND (year * 100 + month) < :key ORDER BY year DESC, month DESC LIMIT 1")
    suspend fun previous(userId: Long, key: Int): MonthEntity?

    @Insert
    suspend fun insert(month: MonthEntity): Long

    @Delete
    suspend fun delete(month: MonthEntity)

    // Incomes
    @Query("SELECT * FROM incomes WHERE month_id = :monthId ORDER BY id")
    fun observeIncomes(monthId: Long): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE month_id = :monthId ORDER BY id")
    suspend fun incomes(monthId: Long): List<IncomeEntity>

    @Upsert
    suspend fun upsertIncome(income: IncomeEntity)

    @Delete
    suspend fun deleteIncome(income: IncomeEntity)

    // Allowances
    @Query("SELECT * FROM allowances WHERE month_id = :monthId")
    fun observeAllowances(monthId: Long): Flow<List<AllowanceEntity>>

    @Query("SELECT * FROM allowances WHERE month_id = :monthId")
    suspend fun allowances(monthId: Long): List<AllowanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAllowance(allowance: AllowanceEntity)

    // Expenses
    @Query("SELECT * FROM expenses WHERE month_id = :monthId ORDER BY block, sort_order, id")
    fun observeExpenses(monthId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE month_id = :monthId ORDER BY block, sort_order, id")
    suspend fun expenses(monthId: Long): List<ExpenseEntity>

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpenses(expenses: List<ExpenseEntity>)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    // Year view
    @Query("SELECT * FROM months WHERE user_id = :userId AND year = :year ORDER BY month")
    fun observeYear(userId: Long, year: Int): Flow<List<MonthEntity>>

    @Query("SELECT i.* FROM incomes i JOIN months m ON m.id = i.month_id WHERE m.user_id = :userId AND m.year = :year")
    fun observeYearIncomes(userId: Long, year: Int): Flow<List<IncomeEntity>>

    @Query("SELECT a.* FROM allowances a JOIN months m ON m.id = a.month_id WHERE m.user_id = :userId AND m.year = :year")
    fun observeYearAllowances(userId: Long, year: Int): Flow<List<AllowanceEntity>>

    @Query("SELECT e.* FROM expenses e JOIN months m ON m.id = e.month_id WHERE m.user_id = :userId AND m.year = :year")
    fun observeYearExpenses(userId: Long, year: Int): Flow<List<ExpenseEntity>>
}
