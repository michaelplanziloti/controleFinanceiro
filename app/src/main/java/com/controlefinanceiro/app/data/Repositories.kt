package com.controlefinanceiro.app.data

import androidx.room.withTransaction
import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.AppDatabase
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.UserEntity
import com.controlefinanceiro.app.domain.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class Success(val userId: Long) : AuthResult
    data class Error(val message: String) : AuthResult
}

class AuthRepository(private val db: AppDatabase, private val session: SessionStore) {

    suspend fun register(name: String, email: String, password: String, partnerName: String): AuthResult =
        withContext(Dispatchers.IO) {
            DemoSeed.ensure(db)
            val normalizedEmail = email.trim().lowercase()
            when {
                name.isBlank() -> return@withContext AuthResult.Error("Informe seu nome.")
                !normalizedEmail.contains("@") -> return@withContext AuthResult.Error("E-mail inválido.")
                password.length < 6 -> return@withContext AuthResult.Error("A senha precisa ter pelo menos 6 caracteres.")
                db.userDao().findByEmail(normalizedEmail) != null ->
                    return@withContext AuthResult.Error("Já existe uma conta com esse e-mail.")
            }
            val salt = PasswordHasher.newSalt()
            val userId = db.withTransaction {
                val id = db.userDao().insert(
                    UserEntity(
                        name = name.trim(),
                        email = normalizedEmail,
                        passwordHash = PasswordHasher.hash(password, salt),
                        passwordSalt = salt,
                    )
                )
                seedDefaults(id, name.trim(), partnerName.trim())
                id
            }
            session.signIn(userId)
            AuthResult.Success(userId)
        }

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        DemoSeed.ensure(db)
        val user = db.userDao().findByEmail(email.trim().lowercase())
        if (user == null || !PasswordHasher.matches(password, user.passwordSalt, user.passwordHash)) {
            AuthResult.Error("E-mail ou senha incorretos.")
        } else {
            session.signIn(user.id)
            AuthResult.Success(user.id)
        }
    }

    suspend fun logout() = session.signOut()

    private suspend fun seedDefaults(userId: Long, name: String, partnerName: String) {
        val ref = db.referenceDao()
        ref.insertPerson(PersonEntity(userId = userId, name = name, sortOrder = 0))
        if (partnerName.isNotBlank()) ref.insertPerson(PersonEntity(userId = userId, name = partnerName, sortOrder = 1))
        ref.insertCategories(DEFAULT_CATEGORIES.map { CategoryEntity(userId = userId, name = it) })
        ref.insertPaymentMethods(DEFAULT_PAYMENT_METHODS.map { PaymentMethodEntity(userId = userId, name = it) })
        ref.upsertSettings(BudgetSettingsEntity(userId = userId))
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Dízimo", "Moradia", "Contas da casa", "Mercado", "Transporte", "Saúde", "Assinaturas", "Serviços",
            "Restaurantes", "Lazer", "Beleza", "Roupas", "Presentes", "Educação", "Eletrônicos", "Viagem",
            "Investimento", "Tarifas e juros", "Livre", "Despesas eventuais",
        )
        val DEFAULT_PAYMENT_METHODS = listOf("À vista/Pix", "Cartão de crédito", "Débito", "Dinheiro")
    }
}

class FinanceRepository(private val db: AppDatabase) {
    private val months = db.monthDao()
    private val ref = db.referenceDao()

    fun observeMonths(userId: Long) = months.observeMonths(userId)
    fun observeIncomes(monthId: Long) = months.observeIncomes(monthId)
    fun observeAllowances(monthId: Long) = months.observeAllowances(monthId)
    fun observeExpenses(monthId: Long) = months.observeExpenses(monthId)
    fun observePeople(userId: Long) = ref.observePeople(userId)
    fun observeCategories(userId: Long) = ref.observeCategories(userId)
    fun observePaymentMethods(userId: Long) = ref.observePaymentMethods(userId)
    fun observeCards(userId: Long) = ref.observeCards(userId)
    fun observeSettings(userId: Long) = ref.observeSettings(userId)
    fun observeYear(userId: Long, year: Int) = months.observeYear(userId, year)
    fun observeYearIncomes(userId: Long, year: Int) = months.observeYearIncomes(userId, year)
    fun observeYearAllowances(userId: Long, year: Int) = months.observeYearAllowances(userId, year)
    fun observeYearExpenses(userId: Long, year: Int) = months.observeYearExpenses(userId, year)

    /**
     * Creates a month. When [copyPrevious] is true it brings incomes, allowances and the recurring
     * blocks (tithe, fixed, couple variable, investments) from the latest earlier month, unpaid.
     * Personal purchases and extras are one-off, so they are not copied.
     */
    suspend fun createMonth(userId: Long, year: Int, month: Int, copyPrevious: Boolean): Long = db.withTransaction {
        months.find(userId, year, month)?.let { return@withTransaction it.id }
        val monthId = months.insert(MonthEntity(userId = userId, year = year, month = month))
        val previous = if (copyPrevious) months.previous(userId, year * 100 + month) else null
        if (previous != null) {
            months.incomes(previous.id).forEach { months.upsertIncome(it.copy(id = 0, monthId = monthId)) }
            months.allowances(previous.id).forEach { months.putAllowance(it.copy(id = 0, monthId = monthId)) }
            val recurring = setOf(ExpenseBlock.TITHE, ExpenseBlock.FIXED, ExpenseBlock.COUPLE, ExpenseBlock.INVESTMENT)
            months.insertExpenses(
                months.expenses(previous.id).filter { it.block in recurring }.map {
                    it.copy(id = 0, monthId = monthId, paidDay30Cents = 0, paidDay15Cents = 0, isPaid = false)
                }
            )
        } else {
            ref.people(userId).forEach { months.putAllowance(AllowanceEntity(monthId = monthId, personId = it.id, amountCents = 0)) }
            months.insertExpense(
                ExpenseEntity(monthId = monthId, block = ExpenseBlock.TITHE, description = "Dízimo", autoPlanned = true, dueDay = "15/30")
            )
        }
        monthId
    }

    suspend fun deleteMonth(month: MonthEntity) = months.delete(month)

    suspend fun saveIncome(income: IncomeEntity) = months.upsertIncome(income)
    suspend fun deleteIncome(income: IncomeEntity) = months.deleteIncome(income)

    suspend fun setAllowance(monthId: Long, personId: Long, amountCents: Long) =
        db.withTransaction {
            val existing = months.allowances(monthId).firstOrNull { it.personId == personId }
            months.putAllowance(AllowanceEntity(id = existing?.id ?: 0, monthId = monthId, personId = personId, amountCents = amountCents))
        }

    suspend fun saveExpense(expense: ExpenseEntity) {
        if (expense.id == 0L) months.insertExpense(expense) else months.updateExpense(expense)
    }

    suspend fun restoreExpense(expense: ExpenseEntity) = months.insertExpense(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = months.deleteExpense(expense)

    suspend fun togglePaid(expense: ExpenseEntity) = months.updateExpense(expense.copy(isPaid = !expense.isPaid))
    suspend fun updateExpenses(expenses: List<ExpenseEntity>) = months.updateExpenses(expenses)

    suspend fun addPerson(userId: Long, name: String, order: Int) =
        ref.insertPerson(PersonEntity(userId = userId, name = name.trim(), sortOrder = order))
    suspend fun deletePerson(person: PersonEntity) = ref.deletePerson(person)
    suspend fun addCategory(userId: Long, name: String) = ref.insertCategories(listOf(CategoryEntity(userId = userId, name = name.trim())))
    suspend fun deleteCategory(category: CategoryEntity) = ref.deleteCategory(category)
    suspend fun addPaymentMethod(userId: Long, name: String) =
        ref.insertPaymentMethods(listOf(PaymentMethodEntity(userId = userId, name = name.trim())))
    suspend fun deletePaymentMethod(method: PaymentMethodEntity) = ref.deletePaymentMethod(method)
    suspend fun addCard(userId: Long, name: String) = ref.insertCards(listOf(CardEntity(userId = userId, name = name.trim())))
    suspend fun deleteCard(card: CardEntity) = ref.deleteCard(card)
    suspend fun saveSettings(settings: BudgetSettingsEntity) = ref.upsertSettings(settings)
}
