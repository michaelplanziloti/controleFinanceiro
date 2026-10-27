package com.controlefinanceiro.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
        BudgetSettingsEntity::class,
        MonthEntity::class,
        IncomeEntity::class,
        AllowanceEntity::class,
        ExpenseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun monthDao(): MonthDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "controle_financeiro.db").build()
    }
}
