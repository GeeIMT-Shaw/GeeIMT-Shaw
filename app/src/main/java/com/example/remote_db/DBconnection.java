package com.example.remote_db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBconnection extends SQLiteOpenHelper {
    static final String Database_name = "locData.db";
    static final int Database_Version = 1;
    SQLiteDatabase db;
    public int id_this;
    Cursor cursor;
    static String ID = "_id";
    static String TIME_REC = "time";
    static String OPERATOR_NAME = "operator";
    static String NETWORK_TYPE = "networkType";
    static String CELL_INFO = "lac_Cellid";
    static String CELL_PCI = "PCI";
    static String CELL_RSSI = "cell_rssi";
    static String SSID = "wifi_name";
    static String MAC = "mac";
    static String WIFI_RSSI = "wifi_rssi";
    static String TABLE_NAME = "test";
    static String PARKING_NAME = "PARK";
    DBconnection(Context ctx){
        super(ctx,Database_name,null,Database_Version);
    }
    public void onCreate(SQLiteDatabase database){
        String sql = "CREATE TABLE " + TABLE_NAME +" ("
                + ID + " INTEGER primary key autoincrement, "
                + PARKING_NAME + " text not null, "
                + TIME_REC + " text not null, "
                + OPERATOR_NAME + " text not null, "
                + NETWORK_TYPE + " text not null, "
                + CELL_INFO + " text not null, "
                + CELL_PCI + " text not null, "
                + CELL_RSSI + " text not null, "
                + SSID + " text not null, "
                + MAC + " text not null, "
                + WIFI_RSSI + " text not null " +");";
        database.execSQL(sql);
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
}
