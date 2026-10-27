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
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun monthDao(): MonthDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "controle_financeiro.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * Adds cards and the expense link. Payment methods named like "Cartão Santander" become the
         * card "Santander", and their expenses move to the "Cartão de crédito" payment method.
         */
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
