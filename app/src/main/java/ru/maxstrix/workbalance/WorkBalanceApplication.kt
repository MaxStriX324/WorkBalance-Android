package ru.maxstrix.workbalance

import android.app.Application
import androidx.room.Room
import ru.maxstrix.workbalance.data.AppDatabase
import ru.maxstrix.workbalance.data.WorkRepository

class WorkBalanceApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "work-balance.db").build()
    }
    val repository by lazy { WorkRepository(database.workDao()) }
}
