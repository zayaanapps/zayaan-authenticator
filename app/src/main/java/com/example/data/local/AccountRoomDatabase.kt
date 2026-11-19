package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Local Room entity storing ONLY AES-256-GCM encrypted account payloads.
 * No plaintext TOTP secrets, issuers, or account labels are ever written to SQLite.
 */
@Entity(tableName = "encrypted_accounts")
data class EncryptedAccountEntity(
    @PrimaryKey val id: String,
    val encryptedPayloadBase64: String,
    val ivBase64: String,
    val sortOrder: Int,
    val updatedAtEpochMs: Long,
    val deleted: Boolean = false
)

@Dao
interface EncryptedAccountDao {
    @Query("SELECT * FROM encrypted_accounts WHERE deleted = 0 ORDER BY sortOrder ASC, updatedAtEpochMs ASC")
    fun observeActiveEncryptedAccounts(): Flow<List<EncryptedAccountEntity>>

    @Query("SELECT * FROM encrypted_accounts ORDER BY sortOrder ASC, updatedAtEpochMs ASC")
    suspend fun getAllEncryptedAccountsOnce(): List<EncryptedAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EncryptedAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EncryptedAccountEntity>)

    @Query("DELETE FROM encrypted_accounts")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(entities: List<EncryptedAccountEntity>) {
        clearAll()
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }
}

@Database(
    entities = [EncryptedAccountEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun encryptedAccountDao(): EncryptedAccountDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "zayan_encrypted_vault.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
