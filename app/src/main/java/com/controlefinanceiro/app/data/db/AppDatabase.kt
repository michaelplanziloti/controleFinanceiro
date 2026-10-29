package com.controlefinanceiro.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun blockToString(block: ExpenseBlock): String = block.name

    @TypeConverter
    fun stringToBlock(value: String): ExpenseBlock = ExpenseBlock.valueOf(value)
}

@Database(
    entities = [
        UserEntity::class,
        PersonEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        CardEntity::class,
        BudgetSettingsEntity::class,
        MonthEntity::class,
        IncomeEntity::class,
        AllowanceEntity::class,
        ExpenseEntity::class,
        SubcategoryEntity::class,
        SubcategoryItemEntity::class,
        VacationEntity::class,
        PlrEntity::class,
        FundIncomeEntity::class,
        FundExpenseEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun monthDao(): MonthDao
    abstract fun vacationDao(): VacationDao
    abstract fun fundDao(): FundDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "controle_financeiro.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()

        /**
         * Adds cards and the expense link. Payment methods named like "Cartão Santander" become the
         * card "Santander", and their expenses move to the "Cartão de crédito" payment method.
         */
        /** PLRs, and the money and spending of each vacation and PLR, apart from the months. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plrs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, " +
                        "`person_id` INTEGER, `title` TEXT NOT NULL, `gross_cents` INTEGER NOT NULL, `pay_date` INTEGER, " +
                        "`earlier_cents` INTEGER NOT NULL, `other_discounts_cents` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`posted_month_id` INTEGER, `created_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plrs_user_id` ON `plrs` (`user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plrs_person_id` ON `plrs` (`person_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fund_incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`owner_id` INTEGER NOT NULL, `source` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_incomes_kind_owner_id` ON `fund_incomes` (`kind`, `owner_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fund_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`owner_id` INTEGER NOT NULL, `description` TEXT NOT NULL, `category_id` INTEGER, `payment_method_id` INTEGER, " +
                        "`card_id` INTEGER, `planned_cents` INTEGER NOT NULL, `paid_cents` INTEGER NOT NULL, `is_paid` INTEGER NOT NULL, " +
                        "`date` INTEGER, `created_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                        "FOREIGN KEY(`payment_method_id`) REFERENCES `payment_methods`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                        "FOREIGN KEY(`card_id`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_expenses_kind_owner_id` ON `fund_expenses` (`kind`, `owner_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_expenses_category_id` ON `fund_expenses` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_expenses_payment_method_id` ON `fund_expenses` (`payment_method_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_expenses_card_id` ON `fund_expenses` (`card_id`)")
            }
        }

        /** Vacations, with what their pay is calculated from. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vacations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, " +
                        "`person_id` INTEGER, `gross_cents` INTEGER NOT NULL, `start_date` INTEGER, `days_taken` INTEGER NOT NULL, " +
                        "`days_sold` INTEGER NOT NULL, `dependents` INTEGER NOT NULL, `advance_13th` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`posted_month_id` INTEGER, `created_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vacations_user_id` ON `vacations` (`user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vacations_person_id` ON `vacations` (`person_id`)")
            }
        }

        /** Whole value of entries paid in installments. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `installment_total_cents` INTEGER")
            }
        }

        /** Items paid in installments: which one, how many, and the whole value. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subcategory_items` ADD COLUMN `installment_number` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `subcategory_items` ADD COLUMN `installment_count` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `subcategory_items` ADD COLUMN `installment_total_cents` INTEGER")
            }
        }

        /** When each expense and item was added, to list the newest first. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `subcategory_items` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Subcategories (budgets like "Mercado"), their items, and the link from a month's row. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subcategories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`user_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `block` TEXT NOT NULL, `planned_cents` INTEGER NOT NULL, " +
                        "`category_id` INTEGER, `payment_method_id` INTEGER, `card_id` INTEGER, `person_id` INTEGER, " +
                        "`in_tracker` INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                        "FOREIGN KEY(`payment_method_id`) REFERENCES `payment_methods`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                        "FOREIGN KEY(`card_id`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                        "FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategories_user_id` ON `subcategories` (`user_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subcategories_user_id_name` ON `subcategories` (`user_id`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategories_category_id` ON `subcategories` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategories_payment_method_id` ON `subcategories` (`payment_method_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategories_card_id` ON `subcategories` (`card_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategories_person_id` ON `subcategories` (`person_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subcategory_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`expense_id` INTEGER NOT NULL, `description` TEXT NOT NULL, `date` INTEGER, `amount_cents` INTEGER NOT NULL, " +
                        "`on_day15` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`expense_id`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategory_items_expense_id` ON `subcategory_items` (`expense_id`)")
                db.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `subcategory_id` INTEGER DEFAULT NULL " +
                        "REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_subcategory_id` ON `expenses` (`subcategory_id`)")
            }
        }

        /** When each month last changed, and whether that was in the spreadsheet. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `months` ADD COLUMN `updated_at` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `months` ADD COLUMN `updated_in_sheet` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Sync state of each month with the Google Sheets tab. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `months` ADD COLUMN `sheet_version` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `months` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_user_id` ON `cards` (`user_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cards_user_id_name` ON `cards` (`user_id`, `name`)")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `card_id` INTEGER DEFAULT NULL REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_card_id` ON `expenses` (`card_id`)")

                val cardMethods = "SELECT id, user_id, TRIM(SUBSTR(name, 8)) AS card FROM payment_methods " +
                    "WHERE name LIKE 'Cartão %' AND name <> 'Cartão de crédito' AND TRIM(SUBSTR(name, 8)) <> ''"
                db.execSQL("INSERT OR IGNORE INTO cards (user_id, name) SELECT user_id, card FROM ($cardMethods)")
                db.execSQL(
                    "INSERT OR IGNORE INTO payment_methods (user_id, name) SELECT DISTINCT user_id, 'Cartão de crédito' FROM ($cardMethods)"
                )
                db.execSQL(
                    "UPDATE expenses SET " +
                        "card_id = (SELECT c.id FROM ($cardMethods) m JOIN cards c ON c.user_id = m.user_id AND c.name = m.card " +
                        "WHERE m.id = expenses.payment_method_id), " +
                        "payment_method_id = (SELECT p.id FROM ($cardMethods) m JOIN payment_methods p ON p.user_id = m.user_id " +
                        "AND p.name = 'Cartão de crédito' WHERE m.id = expenses.payment_method_id) " +
                        "WHERE payment_method_id IN (SELECT id FROM ($cardMethods))"
                )
                db.execSQL("DELETE FROM payment_methods WHERE id IN (SELECT id FROM ($cardMethods))")
            }
        }
    }
}
