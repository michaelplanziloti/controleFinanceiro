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
import com.controlefinanceiro.app.data.sheets.MonthSnapshot
import com.controlefinanceiro.app.data.sheets.SheetMonth
import com.controlefinanceiro.app.data.sheets.cardFromPayment
import com.controlefinanceiro.app.data.sheets.nameKey
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
        const val CREDIT_CARD = "Cartão de crédito"
        val DEFAULT_PAYMENT_METHODS = listOf("À vista/Pix", CREDIT_CARD, "Débito", "Dinheiro")
    }
}

/** [onMonthChanged] runs after every change to a month's data (used to send it to Google Sheets). */
class FinanceRepository(private val db: AppDatabase, private val onMonthChanged: (Long) -> Unit = {}) {
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
    suspend fun createMonth(userId: Long, year: Int, month: Int, copyPrevious: Boolean): Long {
        months.find(userId, year, month)?.let { return it.id }
        val monthId = db.withTransaction { insertMonth(userId, year, month, copyPrevious) }
        changed(monthId)
        return monthId
    }

    private suspend fun insertMonth(userId: Long, year: Int, month: Int, copyPrevious: Boolean): Long {
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
        return monthId
    }

    suspend fun deleteMonth(month: MonthEntity) = months.delete(month)

    suspend fun saveIncome(income: IncomeEntity) {
        months.upsertIncome(income)
        changed(income.monthId)
    }

    suspend fun deleteIncome(income: IncomeEntity) {
        months.deleteIncome(income)
        changed(income.monthId)
    }

    suspend fun setAllowance(monthId: Long, personId: Long, amountCents: Long) {
        db.withTransaction {
            val existing = months.allowances(monthId).firstOrNull { it.personId == personId }
            months.putAllowance(AllowanceEntity(id = existing?.id ?: 0, monthId = monthId, personId = personId, amountCents = amountCents))
        }
        changed(monthId)
    }

    suspend fun saveExpense(expense: ExpenseEntity) {
        if (expense.id == 0L) months.insertExpense(expense) else months.updateExpense(expense)
        changed(expense.monthId)
    }

    suspend fun restoreExpense(expense: ExpenseEntity) {
        months.insertExpense(expense)
        changed(expense.monthId)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        months.deleteExpense(expense)
        changed(expense.monthId)
    }

    suspend fun togglePaid(expense: ExpenseEntity) {
        months.updateExpense(expense.copy(isPaid = !expense.isPaid))
        changed(expense.monthId)
    }

    suspend fun updateExpenses(expenses: List<ExpenseEntity>) {
        months.updateExpenses(expenses)
        expenses.map { it.monthId }.distinct().forEach { changed(it) }
    }

    /** Marks the month as having changes the spreadsheet has not seen yet, then reports it. */
    private suspend fun changed(monthId: Long) {
        months.markDirty(monthId)
        onMonthChanged(monthId)
    }

    /** Ids of all the user's months, oldest first. */
    suspend fun monthIds(userId: Long): List<Long> = months.ids(userId)

    suspend fun userIds(): List<Long> = db.userDao().ids()
    suspend fun months(userId: Long): List<MonthEntity> = months.all(userId)
    suspend fun markSynced(monthId: Long, sheetVersion: Long) = months.markSynced(monthId, sheetVersion)

    /**
     * Replaces the month with what its spreadsheet tab has (the sheet wins) and marks it in sync.
     * Names are matched ignoring case and accents; unknown categories, payment methods, cards and
     * people are created, so whatever was typed in the sheet shows up in the app.
     */
    suspend fun importFromSheet(userId: Long, sheet: SheetMonth): Long = db.withTransaction {
        val monthId = months.find(userId, sheet.year, sheet.month)?.id
            ?: months.insert(MonthEntity(userId = userId, year = sheet.year, month = sheet.month))
        months.deleteIncomes(monthId)
        months.deleteAllowances(monthId)
        months.deleteExpenses(monthId)

        val names = NameResolver(userId)
        sheet.incomes.forEach {
            months.upsertIncome(IncomeEntity(monthId = monthId, personId = it.person?.let { p -> names.person(p) }, source = it.source, amountCents = it.amountCents))
        }
        sheet.allowances.forEach {
            months.putAllowance(AllowanceEntity(monthId = monthId, personId = names.person(it.person), amountCents = it.amountCents))
        }
        val expenses = sheet.expenses.mapIndexed { index, e ->
            val (methodId, cardId) = names.payment(e.payment)
            ExpenseEntity(
                monthId = monthId,
                block = e.block,
                description = e.description,
                categoryId = e.category?.let { names.category(it) },
                paymentMethodId = methodId,
                cardId = cardId,
                // Personal items always belong to someone; the first person is the fallback.
                personId = e.person?.let { names.person(it) } ?: if (e.block == ExpenseBlock.PERSONAL) names.firstPerson() else null,
                dueDay = e.dueDay,
                plannedCents = if (e.autoPlanned) 0 else e.plannedCents,
                autoPlanned = e.autoPlanned,
                paidDay30Cents = e.paid30Cents,
                paidDay15Cents = e.paid15Cents,
                isPaid = e.paid,
                sortOrder = index,
            )
        }
        months.insertExpenses(expenses)
        months.markSynced(monthId, sheet.editedAt)
        monthId
    }

    /** Finds people, categories, payment methods and cards by name, creating the missing ones. */
    private inner class NameResolver(private val userId: Long) {
        private val people = mutableMapOf<String, Long>()
        private val categories = mutableMapOf<String, Long>()
        private val methods = mutableMapOf<String, Long>()
        private val cards = mutableMapOf<String, Long>()
        private var loaded = false

        private suspend fun load() {
            if (loaded) return
            ref.people(userId).forEach { people.putIfAbsent(it.name.nameKey(), it.id) }
            ref.categories(userId).forEach { categories.putIfAbsent(it.name.nameKey(), it.id) }
            ref.paymentMethods(userId).forEach { methods.putIfAbsent(it.name.nameKey(), it.id) }
            ref.cards(userId).forEach { cards.putIfAbsent(it.name.nameKey(), it.id) }
            loaded = true
        }

        suspend fun person(name: String): Long {
            load()
            return people[name.nameKey()]
                ?: ref.insertPerson(PersonEntity(userId = userId, name = name.trim(), sortOrder = people.size)).also { people[name.nameKey()] = it }
        }

        suspend fun firstPerson(): Long? = ref.people(userId).firstOrNull()?.id

        suspend fun category(name: String): Long {
            load()
            return categories[name.nameKey()]
                ?: ref.insertCategory(CategoryEntity(userId = userId, name = name.trim())).also { categories[name.nameKey()] = it }
        }

        private suspend fun method(name: String): Long {
            load()
            return methods[name.nameKey()]
                ?: ref.insertPaymentMethod(PaymentMethodEntity(userId = userId, name = name.trim())).also { methods[name.nameKey()] = it }
        }

        /** Payment method and card ids for a spreadsheet "Forma de pagamento". */
        suspend fun payment(name: String?): Pair<Long?, Long?> {
            if (name.isNullOrBlank()) return null to null
            val card = cardFromPayment(name) ?: return method(name) to null
            load()
            val cardId = cards[card.nameKey()]
                ?: ref.insertCard(CardEntity(userId = userId, name = card)).also { cards[card.nameKey()] = it }
            return method(AuthRepository.CREDIT_CARD) to cardId
        }
    }

    /** The month with everything the spreadsheet needs, or null if it was deleted. */
    suspend fun snapshot(monthId: Long): MonthSnapshot? = db.withTransaction {
        val month = months.byId(monthId) ?: return@withTransaction null
        MonthSnapshot(
            month = month,
            incomes = months.incomes(monthId),
            allowances = months.allowances(monthId),
            expenses = months.expenses(monthId),
            people = ref.people(month.userId),
            categories = ref.categories(month.userId),
            paymentMethods = ref.paymentMethods(month.userId),
            cards = ref.cards(month.userId),
            settings = ref.settings(month.userId) ?: BudgetSettingsEntity(userId = month.userId),
        )
    }

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
