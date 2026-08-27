package vn.nhip2phut.data.storage

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = ClockStateEntity.TABLE_NAME)
data class ClockStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Long,
    @ColumnInfo(name = "crypto_version")
    val cryptoVersion: Int,
    @ColumnInfo(name = "key_version")
    val keyVersion: Int,
    @ColumnInfo(name = "payload_schema_version")
    val payloadSchemaVersion: Int,
    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB)
    val encryptedPayload: ByteArray,
) {
    init {
        require(singletonId == SINGLETON_ID) { "Clock state must use singleton ID 1." }
        require(cryptoVersion > 0) { "Crypto version must be positive." }
        require(keyVersion > 0) { "Key version must be positive." }
        require(payloadSchemaVersion > 0) { "Payload schema version must be positive." }
        require(encryptedPayload.isNotEmpty()) { "Encrypted payload is required." }
    }

    companion object {
        const val TABLE_NAME = "clock_state"
        const val ENCRYPTED_COLUMN = "encrypted_payload"
        const val SINGLETON_ID = 1L
    }
}

@Dao
interface ClockStateDao {
    @Query("SELECT * FROM clock_state WHERE singleton_id = 1")
    suspend fun read(): ClockStateEntity?

    @Query("SELECT * FROM clock_state")
    suspend fun readAll(): List<ClockStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(entity: ClockStateEntity)

    @Query("DELETE FROM clock_state")
    suspend fun delete()
}
