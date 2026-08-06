package com.devil1716.bluetoothmanet;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {
        ChatMessageEntity.class,
        PendingMessageEntity.class,
        MeshNeighborEntity.class,
        MeshRouteEntity.class,
        PacketHistoryEntity.class
}, version = 3, exportSchema = false)
@TypeConverters(MessageStatusConverter.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MessageDao messageDao();
    public abstract PendingMessageDao pendingMessageDao();
    public abstract MeshStateDao meshStateDao();

    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "manet_messages.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS pending_messages (id TEXT NOT NULL, destination TEXT NOT NULL, wire TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS mesh_neighbors (deviceId TEXT NOT NULL, displayName TEXT, rssi INTEGER NOT NULL, batteryPercent INTEGER, latencyMs INTEGER, hopCount INTEGER NOT NULL, lastSeen INTEGER NOT NULL, connected INTEGER NOT NULL, PRIMARY KEY(deviceId))");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_mesh_neighbors_lastSeen ON mesh_neighbors(lastSeen)");
            database.execSQL("CREATE TABLE IF NOT EXISTS mesh_routes (destinationId TEXT NOT NULL, nextHopId TEXT NOT NULL, hopCount INTEGER NOT NULL, score REAL NOT NULL, lastUpdated INTEGER NOT NULL, PRIMARY KEY(destinationId))");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_mesh_routes_lastUpdated ON mesh_routes(lastUpdated)");
            database.execSQL("CREATE TABLE IF NOT EXISTS packet_history (packetId TEXT NOT NULL, packetType TEXT NOT NULL, seenAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, PRIMARY KEY(packetId))");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_packet_history_expiresAt ON packet_history(expiresAt)");
        }
    };
}
