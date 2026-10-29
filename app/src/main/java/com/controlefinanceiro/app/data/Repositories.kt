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
import com.controlefinanceiro.app.data.db.SubcategoryEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.data.db.UserEntity
import com.controlefinanceiro.app.data.sheets.MonthSnapshot
import com.controlefinanceiro.app.data.sheets.SheetMonth
import com.controlefinanceiro.app.data.sheets.cardFromPayment
import com.controlefinanceiro.app.data.sheets.nameKey
import com.controlefinanceiro.app.domain.Installments
import com.controlefinanceiro.app.domain.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
        /** Item created from what a row already had paid when its first item was added. */
        const val PREVIOUS_VALUE = "Valor já lançado"
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
    fun observeSubcategories(userId: Long) = ref.observeSubcategories(userId)
    fun observeItems(monthId: Long) = months.observeItems(monthId)
    fun observeAllExpenses(userId: Long) = months.observeAllExpenses(userId)
    fun observeAllItems(userId: Long) = months.observeAllItems(userId)

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
        // Every subcategory gets its row in the new month (rows copied from the previous month already count).
        val have = months.expenses(monthId).mapNotNull { it.subcategoryId }.toSet()
        ref.subcategories(userId).filter { it.id !in have }.forEach { months.insertExpense(newRow(monthId, it)) }
        // Purchases in installments bring their next installment from the month just before, copied or not.
        val (py, pm) = if (month == 1) year - 1 to 12 else year to month - 1
        months.find(userId, py, pm)?.let { before ->
            val rows = months.items(before.id).mapNotNull { item ->
                val row = months.expense(item.expenseId) ?: return@mapNotNull null
                val target = matchingRow(row, monthId)
                Installments.next(item, target)?.let { months.upsertItem(it); target }
            }
            rows.distinct().forEach { sumItems(it) }
        }
        return monthId
    }

    /** The row in [monthId] that continues [row]: same subcategory, or same block and description; created if missing. */
    private suspend fun matchingRow(row: ExpenseEntity, monthId: Long): Long {
        val rows = months.expenses(monthId)
        row.subcategoryId?.let { sid ->
            rows.firstOrNull { it.subcategoryId == sid }?.let { return it.id }
            ref.subcategory(sid)?.let { return months.insertExpense(newRow(monthId, it)) }
        }
        rows.firstOrNull { it.block == row.block && it.description.nameKey() == row.description.nameKey() }?.let { return it.id }
        return months.insertExpense(
            row.copy(id = 0, monthId = monthId, paidDay15Cents = 0, paidDay30Cents = 0, isPaid = false, createdAt = System.currentTimeMillis())
        )
    }

    /**
     * A new purchase in installments also goes to the months after it that already exist, one
     * installment per month, until a month is missing (creating that month brings the rest).
     */
    private suspend fun placeLaterInstallments(item: SubcategoryItemEntity, row: ExpenseEntity): Set<Long> {
        val touched = mutableSetOf<Long>()
        var month = months.byId(row.monthId) ?: return touched
        var current = item
        var currentRow = row
        while (current.installmentNumber < current.installmentCount) {
            val (y, m) = if (month.month == 12) month.year + 1 to 1 else month.year to month.month + 1
            month = months.find(month.userId, y, m) ?: break
            val targetId = matchingRow(currentRow, month.id)
            val target = months.expense(targetId) ?: break
            keepPaidValuesAsItems(target)
            current = Installments.next(current, targetId) ?: break
            months.upsertItem(current)
            sumItems(targetId)
            currentRow = target
            touched += month.id
        }
        return touched
    }

    // ---------- Subcategories ----------

    /**
     * Saves a subcategory. Rows with its name become its rows, and from the current month on every
     * month has one row that follows its block, target and defaults (a target changed in a single
     * month is kept). Past months are left as they are.
     */
    suspend fun saveSubcategory(sub: SubcategoryEntity): Long {
        val touched = mutableSetOf<Long>()
        val id = db.withTransaction {
            val old = if (sub.id != 0L) ref.subcategory(sub.id) else null
            val id = if (sub.id == 0L) ref.insertSubcategory(sub) else sub.id.also { ref.updateSubcategory(sub) }
            val saved = sub.copy(id = id)
            val userMonths = months.all(sub.userId)
            val monthKey = userMonths.associate { it.id to it.year * 100 + it.month }
            val current = LocalDate.now().let { it.year * 100 + it.monthValue }
            val key = saved.name.nameKey()
            val oldKey = old?.name?.nameKey()

            months.allExpenses(sub.userId)
                .filter { it.subcategoryId == id || (it.subcategoryId == null && it.description.nameKey().let { d -> d == key || d == oldKey }) }
                .forEach { row ->
                    val linked = row.copy(subcategoryId = id)
                    val updated = if ((monthKey[row.monthId] ?: 0) >= current) applyDefinition(linked, saved, old) else linked
                    if (updated != row) {
                        months.updateExpense(updated)
                        touched += row.monthId
                    }
                }
            val withRow = months.allExpenses(sub.userId).filter { it.subcategoryId == id }.map { it.monthId }.toSet()
            userMonths.filter { it.year * 100 + it.month >= current && it.id !in withRow }.forEach {
                months.insertExpense(newRow(it.id, saved))
                touched += it.id
            }
            id
        }
        touched.forEach { changed(it) }
        return id
    }

    /** The rows keep their values and items; they just stop being a subcategory. */
    suspend fun deleteSubcategory(sub: SubcategoryEntity) = ref.deleteSubcategory(sub)

    suspend fun setTracked(userId: Long, subcategoryIds: List<Long>) = ref.setTracked(userId, subcategoryIds)

    private suspend fun newRow(monthId: Long, sub: SubcategoryEntity) = ExpenseEntity(
        monthId = monthId,
        block = sub.block,
        description = sub.name,
        categoryId = sub.categoryId,
        paymentMethodId = sub.paymentMethodId,
        cardId = sub.cardId,
        personId = if (sub.block == ExpenseBlock.PERSONAL) sub.personId ?: ref.people(sub.userId).firstOrNull()?.id else null,
        plannedCents = sub.plannedCents,
        subcategoryId = sub.id,
        sortOrder = Int.MAX_VALUE,
    )

    /** A row follows the definition; a value that differed from the old definition was set for that month and is kept. */
    private fun applyDefinition(row: ExpenseEntity, sub: SubcategoryEntity, old: SubcategoryEntity?): ExpenseEntity {
        fun <T> follow(rowValue: T, oldValue: T?, newValue: T): T = if (old == null || rowValue == oldValue) newValue else rowValue
        return row.copy(
            description = sub.name,
            block = sub.block,
            personId = if (sub.block == ExpenseBlock.PERSONAL) sub.personId ?: row.personId else null,
            plannedCents = follow(row.plannedCents, old?.plannedCents, sub.plannedCents),
            categoryId = follow(row.categoryId, old?.categoryId, sub.categoryId ?: row.categoryId),
            paymentMethodId = follow(row.paymentMethodId, old?.paymentMethodId, sub.paymentMethodId ?: row.paymentMethodId),
            cardId = follow(row.cardId, old?.cardId, sub.cardId ?: row.cardId),
        )
    }

    /** The subcategory's row in the month, created if the month does not have one yet. */
    suspend fun rowFor(subcategoryId: Long, monthId: Long): Long? = db.withTransaction {
        months.expenses(monthId).firstOrNull { it.subcategoryId == subcategoryId }?.id
            ?: ref.subcategory(subcategoryId)?.let { months.insertExpense(newRow(monthId, it)) }
    }

    // ---------- Subcategory items ----------

    /**
     * Adds or edits a purchase; its row's paid values become the sum of the row's items. A new
     * purchase in installments also fills the later months that exist when [newPurchase] (not when an
     * item is moved or put back by undo, whose later installments are already there).
     */
    suspend fun saveItem(item: SubcategoryItemEntity, newPurchase: Boolean = false) {
        val row = months.expense(item.expenseId) ?: return
        val later = db.withTransaction {
            if (item.id == 0L) keepPaidValuesAsItems(row)
            months.upsertItem(item)
            sumItems(row.id)
            if (newPurchase && item.id == 0L && item.installmentCount > 1) placeLaterInstallments(item, row) else emptySet()
        }
        changed(row.monthId)
        later.forEach { changed(it) }
    }

    suspend fun deleteItem(item: SubcategoryItemEntity) {
        val row = months.expense(item.expenseId) ?: return
        db.withTransaction {
            months.deleteItem(item)
            sumItems(row.id)
        }
        changed(row.monthId)
    }

    /** Values typed in the row before it had items become items, so the first item does not wipe them. */
    private suspend fun keepPaidValuesAsItems(row: ExpenseEntity) {
        if (months.itemsOf(row.id).isNotEmpty()) return
        val before = listOf(true to row.paidDay15Cents, false to row.paidDay30Cents).filter { it.second != 0L }
        months.insertItems(before.map { (day15, cents) -> SubcategoryItemEntity(expenseId = row.id, description = AuthRepository.PREVIOUS_VALUE, amountCents = cents, onDay15 = day15) })
    }

    private suspend fun sumItems(rowId: Long) {
        val row = months.expense(rowId) ?: return
        val items = months.itemsOf(rowId)
        val summed = row.copy(
            paidDay15Cents = items.filter { it.onDay15 }.sumOf { it.amountCents },
            paidDay30Cents = items.filterNot { it.onDay15 }.sumOf { it.amountCents },
        )
        if (summed != row) months.updateExpense(summed)
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
    suspend fun markSent(monthId: Long, sheetVersion: Long) = months.markSent(monthId, sheetVersion)

    /**
     * Replaces the month with what its spreadsheet tab has (the sheet wins) and marks it in sync.
     * Names are matched ignoring case and accents; unknown categories, payment methods, cards and
     * people are created, so whatever was typed in the sheet shows up in the app.
     */
    suspend fun importFromSheet(userId: Long, sheet: SheetMonth): Long = db.withTransaction {
        val monthId = months.find(userId, sheet.year, sheet.month)?.id
            ?: months.insert(MonthEntity(userId = userId, year = sheet.year, month = sheet.month))
        // Rows and items that come back from the sheet keep the time they were first added.
        val oldRows = months.expenses(monthId)
        val rowTimes = oldRows.groupBy { it.block.name + "|" + it.description.nameKey() }.mapValues { (_, l) -> l.map { it.createdAt }.toMutableList() }
        val rowNames = oldRows.associate { it.id to it.description.nameKey() }
        val oldItems = months.items(monthId).groupBy { "${rowNames[it.expenseId]}|${it.description.nameKey()}|${it.amountCents}|${it.date}" }
            .mapValues { (_, l) -> l.toMutableList() }
        val now = System.currentTimeMillis()
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
        val subcategories = ref.subcategories(userId).associateBy { it.name.nameKey() }
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
                subcategoryId = subcategories[e.description.nameKey()]?.id,
                createdAt = rowTimes[e.block.name + "|" + e.description.nameKey()]?.removeFirstOrNull() ?: now,
            )
        }
        val ids = months.insertExpenses(expenses)

        // Items go to the row with their subcategory's name (a new row in the block of that
        // subcategory, or in "Variáveis do casal", if the sheet has items without one).
        val rows = expenses.zip(ids).associate { (row, id) -> row.description.nameKey() to id }.toMutableMap()
        sheet.items.groupBy { it.subcategory.nameKey() }.forEach { (key, items) ->
            val rowId = rows[key] ?: run {
                val sub = subcategories[key]
                months.insertExpense(
                    sub?.let { newRow(monthId, it) }
                        ?: ExpenseEntity(monthId = monthId, block = ExpenseBlock.COUPLE, description = items.first().subcategory, sortOrder = Int.MAX_VALUE)
                ).also { rows[key] = it }
            }
            months.insertItems(items.map {
                val old = oldItems["$key|${it.description.nameKey()}|${it.amountCents}|${it.date}"]?.removeFirstOrNull()
                SubcategoryItemEntity(
                    expenseId = rowId, description = it.description, date = it.date, amountCents = it.amountCents, onDay15 = it.onDay15,
                    createdAt = old?.createdAt ?: now,
                    installmentNumber = it.installmentNumber,
                    installmentCount = it.installmentCount,
                    // The sheet only shows "(1/2)"; the whole value comes from the app's copy when it matches.
                    installmentTotalCents = old?.installmentTotalCents?.takeIf { _ -> old.installmentCount == it.installmentCount },
                )
            })
        }
        val sumsChanged = rows.values.distinct().filter { months.itemsOf(it).isNotEmpty() }.count { rowId ->
            val before = months.expense(rowId)
            sumItems(rowId)
            months.expense(rowId) != before
        }
        months.markSynced(monthId, sheet.editedAt)
        // A row whose values did not match its items was corrected: send it back to the sheet.
        if (sumsChanged > 0) months.markDirty(monthId)
        // Same time and origin the tab shows in B3 (an old script without times: the download time).
        months.markUpdated(monthId, sheet.editedAt.takeIf { it > 0 } ?: System.currentTimeMillis(), inSheet = !sheet.lastChangeFromApp)
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
            items = months.items(monthId),
        )
    }

    suspend fun addPerson(userId: Long, name: String, order: Int) =
        ref.insertPerson(PersonEntity(userId = userId, name = name.trim(), sortOrder = order))
    suspend fun deletePerson(person: PersonEntity) = ref.deletePerson(person)
    suspend fun renamePerson(person: PersonEntity, name: String) = ref.updatePerson(person.copy(name = name.trim()))
    suspend fun renameCategory(category: CategoryEntity, name: String) = ref.updateCategory(category.copy(name = name.trim()))
    suspend fun renamePaymentMethod(method: PaymentMethodEntity, name: String) = ref.updatePaymentMethod(method.copy(name = name.trim()))
    suspend fun renameCard(card: CardEntity, name: String) = ref.updateCard(card.copy(name = name.trim()))
    suspend fun addCategory(userId: Long, name: String) = ref.insertCategories(listOf(CategoryEntity(userId = userId, name = name.trim())))
    suspend fun deleteCategory(category: CategoryEntity) = ref.deleteCategory(category)
    suspend fun addPaymentMethod(userId: Long, name: String) =
        ref.insertPaymentMethods(listOf(PaymentMethodEntity(userId = userId, name = name.trim())))
    suspend fun deletePaymentMethod(method: PaymentMethodEntity) = ref.deletePaymentMethod(method)
    suspend fun addCard(userId: Long, name: String) = ref.insertCards(listOf(CardEntity(userId = userId, name = name.trim())))
    suspend fun deleteCard(card: CardEntity) = ref.deleteCard(card)
    suspend fun saveSettings(settings: BudgetSettingsEntity) = ref.upsertSettings(settings)
}
