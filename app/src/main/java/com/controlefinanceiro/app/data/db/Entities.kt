package com.controlefinanceiro.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Relational model (all money values are stored in cents to avoid floating point errors).
 *
 * users 1─N people, categories, payment_methods, months
 * users 1─1 budget_settings
 * months 1─N incomes, allowances, expenses
 * people 1─N incomes, allowances, expenses (personal block)
 */

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "password_salt") val passwordSalt: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** A member of the household who has an allowance (e.g. Michael and Julia). */
@Entity(
    tableName = "people",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("user_id"), Index(value = ["user_id", "name"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("user_id"), Index(value = ["user_id", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
)

@Entity(
    tableName = "payment_methods",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("user_id"), Index(value = ["user_id", "name"], unique = true)],
)
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
)

/** Budget rule: tithe over gross income, the other three over income after tithe. Values in basis points (10000 = 100%). */
@Entity(
    tableName = "budget_settings",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)],
)
data class BudgetSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "tithe_bp") val titheBp: Int = 1000,
    @ColumnInfo(name = "fixed_bp") val fixedBp: Int = 5000,
    @ColumnInfo(name = "variable_bp") val variableBp: Int = 3000,
    @ColumnInfo(name = "investment_bp") val investmentBp: Int = 2000,
)

@Entity(
    tableName = "months",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("user_id"), Index(value = ["user_id", "year", "month"], unique = true)],
)
data class MonthEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val year: Int,
    /** 1..12 */
    val month: Int,
)

@Entity(
    tableName = "incomes",
    foreignKeys = [
        ForeignKey(entity = MonthEntity::class, parentColumns = ["id"], childColumns = ["month_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("month_id"), Index("person_id")],
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "month_id") val monthId: Long,
    /** Null means shared income (counts in the total but not in the per-person split). */
    @ColumnInfo(name = "person_id") val personId: Long?,
    val source: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

/** Monthly allowance ("mesada") of one person. */
@Entity(
    tableName = "allowances",
    foreignKeys = [
        ForeignKey(entity = MonthEntity::class, parentColumns = ["id"], childColumns = ["month_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["month_id", "person_id"], unique = true), Index("person_id")],
)
data class AllowanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "month_id") val monthId: Long,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

/** The five blocks of the spreadsheet, plus the tithe which is shown inside the fixed block. */
enum class ExpenseBlock { TITHE, FIXED, COUPLE, PERSONAL, INVESTMENT, EXTRA }

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(entity = MonthEntity::class, parentColumns = ["id"], childColumns = ["month_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PaymentMethodEntity::class, parentColumns = ["id"], childColumns = ["payment_method_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("month_id"), Index("category_id"), Index("payment_method_id"), Index("person_id")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "month_id") val monthId: Long,
    val block: ExpenseBlock,
    val description: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "payment_method_id") val paymentMethodId: Long? = null,
    /** Required for the PERSONAL block (whose allowance it uses). */
    @ColumnInfo(name = "person_id") val personId: Long? = null,
    /** Free text such as "15", "30" or "15/30". */
    @ColumnInfo(name = "due_day") val dueDay: String? = null,
    @ColumnInfo(name = "planned_cents") val plannedCents: Long = 0,
    /** When true the planned value is calculated (tithe = income x tithe %). */
    @ColumnInfo(name = "auto_planned") val autoPlanned: Boolean = false,
    @ColumnInfo(name = "paid_day30_cents") val paidDay30Cents: Long = 0,
    @ColumnInfo(name = "paid_day15_cents") val paidDay15Cents: Long = 0,
    @ColumnInfo(name = "is_paid") val isPaid: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
