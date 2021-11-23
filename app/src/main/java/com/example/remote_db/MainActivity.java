package com.example.remote_db;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/*
    需要修改的部分:
        1. 添加磁场数据、加速度计数据读入，放入.txt文件中，数据格式为： 时间戳 磁场强度 加速度计Ax Ay Az
        暂时完成，不过不确定是否能保证写入内容的顺序，需要后期检查。另外可以把所有写入的内容放到另一个类中
        2. 修改写入的信息，只需要Wi-Fi的Rssi、BSSID 蜂窝信号的RSS、基站ID，并且区分开GSM和LTE

        3. 取消区域位置的录入，只需要一个label，即地点
        4. 可以考虑将不同的模块放入新的文件中，简化Main函数
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {
    //UI界面控件
    private Button setupBtn;//开始数据库
    private Button closeBtn;//关闭数据库
    private Button startBtn;//开始按钮
    private Button stopBtn;//停止按钮
    static EditText nameEdit;//停车场名称
    static EditText interval;//广播间隔
    private TextView show_1;//网络制式
    private TextView show_2;//基站ID
    private TextView show_3;//RSSI
    private TextView show_4;//MAC地址
    private TextView show_5;//wifi强度
    private TextView show_6;//已收集数据的信息
    private TextView show_7;//wifi名称
    private TextView show_8;//磁场强度
    public int count;//记录收集数据的次数
    CheckBox cell, wifi;
    //数据库控件
    private Cursor cursor;
    public SQLiteDatabase db;
    public DBconnection helper;
    public String name;
    private String time;
    //蜂窝控件
    private int inTime;//time interval
    private TelephonyManager tManager;
    private ArrayList<String> cell_rssi;
    private ArrayList<String> cellType;
    private ArrayList<String> cell_id;
    private ArrayList<String> cell_pci;//如果lte获取不到cellid只能用pci作为替代
    List<CellInfo> list2;
    String operator = "";
    //wifi控件
    private WifiManager mWifi;
    private WifiInfo wifiInfo;
    private ArrayList<String> ssid;//wifi ssid
    private ArrayList<String> bssid;//wifi bssid
    private ArrayList<String> wifi_rssi;//wifi rssi
    private List<ScanResult> list;
    private String strLevel;
    private String strBssid;
    private String strSsid;
    //第二线程部分，控制循环操作
    private ControlRunnable onDoRunnable;
    private Handler mainHandler;
    private int i = 0;
    public boolean isclicked;
    private boolean isSimAvail = Boolean.TRUE;
    private File myFile;
    private ArrayList<String> Buffer;
    private String absolutePath;
    private SimpleDateFormat timeFormat1,timeFormat2;
    private DecimalFormat df = new DecimalFormat("#.####");
    private float[] r = new float[9];
    private float[] I = new float[9];
    private float[] gravity = null;
    private float[] geomagnetic = null;
    private Integer seqControl = 0;
    //数据库构建
    Bundle savedInstanceState;
    static String ID = "_id";
    static String TIME_REC = "time";
    static String OPERATOR_NAME = "operator";
    static String NETWORK_TYPE = "networkType";
    static String CELL_INFO = "lac_Cellid";
    static String CELL_PCI = "PCI";
    static String CELL_RSSI = "cell_rssi";
    static String MAC = "mac";
    static String SSID = "wifi_name";
    static String WIFI_RSSI = "wifi_rssi";
    static String TABLE_NAME = "test";
    static String PARKING_NAME = "PARK";
    /*  传感器数据的读取  */
    private SensorManager sManager;
    private Sensor mMegSensor, mAcSensor; //磁场传感器和加速度传感器
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isclicked = false;
        bindview();
        tManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        sManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mMegSensor = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAcSensor = sManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Buffer = new ArrayList<String>();
        absolutePath = getFilesDir().getAbsolutePath();
        timeFormat1 = new SimpleDateFormat("MM-dd_HH:mm:ss:SSS"); //设置时间格式
        timeFormat2 = new SimpleDateFormat("YY-MM-dd HH:mm:ss:SSS");
        wifiInfo = mWifi.getConnectionInfo();
        mainHandler = new Handler();
        onDoRunnable = new ControlRunnable();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Date curTime = new Date(System.currentTimeMillis()); //获取当前时间
        String rec = "";
        rec += timeFormat1.format(curTime) + " ";
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            rec += df.format(event.values[0]) + " " + df.format(event.values[1])
                    + " " + df.format(event.values[2]) + " ";
            seqControl = 0;
            gravity = event.values;
        }
        if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            geomagnetic = event.values; // 获取地磁数据
            if(sManager.getRotationMatrix(r, I, gravity, geomagnetic)){
                float h = (I[3]*r[0] + I[4]*r[3] + I[5]*r[6])*geomagnetic[0]+
                        (I[3]*r[1] + I[4]*r[4] + I[5]*r[7])*geomagnetic[1]+
                        (I[3]*r[2] + I[4]*r[5] + I[5]*r[8])*geomagnetic[2];
                rec += df.format(h); // 向缓冲区内写入磁场强度
                show_8.setText(Float.toString(h));
            }
            seqControl = 1;
        }


        Buffer.add(rec);
        if(Buffer.size() == 100){ //100组数据
            try {
                RandomAccessFile raf = new RandomAccessFile(myFile, "rw");
                for(String data : Buffer){
                    data += "\r\n";
                    raf.seek(myFile.length());
                    raf.write(data.getBytes());
                }
                raf.close(); // 关闭写入流
                Buffer.clear(); // 清空字符缓冲
            } catch (FileNotFoundException e) {
                Log.e("Raf Init","Can't Find File path");
            } catch (IOException e) {
                Log.e("WriteFile","Can't Write Data in File");
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //按钮触发事件的声明
    class Click implements View.OnClickListener {
        //设置需要用到的字符串
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn1://建立数据库
                    openOp();
                    break;
                case R.id.btn2://关闭数据库
                    closeOp();
                    break;
                case R.id.btn3:
                    onDoRunnable.flag = true;//一摁开始就为true
                    if(tManager.getSimState() == TelephonyManager.SIM_STATE_ABSENT)
                        isSimAvail = Boolean.FALSE;
                    else
                        isSimAvail = Boolean.TRUE;

                    sManager.registerListener(MainActivity.this,mAcSensor,SensorManager.SENSOR_DELAY_GAME);
                    sManager.registerListener(MainActivity.this,mMegSensor,SensorManager.SENSOR_DELAY_GAME);
                    try{
                        myFile =CREndOpenFile(absolutePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (!interval.getText().toString().isEmpty()) {//假如广播间隔给定，则可建立监听
                        inTime = Integer.parseInt(interval.getText().toString());
                    } else inTime = 100;//如果没有给定间隔默认0.1s收集一次

                    onDoRunnable.run();
                    break;
                case R.id.btn4:
                    if (!isclicked) {
                        onDoRunnable.stop(false);//stopButton使得线程停止进行
                        isclicked = true;
                        stopBtn.setText("停止记录");
                    }else {onDoRunnable.reset();onDoRunnable.flag=false;isclicked = false;stopBtn.setText("暂停记录");}
                    sManager.unregisterListener(MainActivity.this);
                    if(Buffer.size() != 0){
                        try {
                            RandomAccessFile raf = new RandomAccessFile(myFile, "rw");
                            for(String data : Buffer){
                                data += "\r\n";
                                raf.seek(myFile.length());
                                raf.write(data.getBytes());
                            }
                            raf.close(); // 关闭写入流
                            Buffer.clear(); // 清空字符缓冲
                        } catch (FileNotFoundException e) {
                            Log.e("Raf Init","Can't Find File path");
                        } catch (IOException e) {
                            Log.e("WriteFile","Can't Write Data in File");
                        }
                    }

                    break;
                default:
                    break;
            }
        }
    }
    //Runnable类声明
    class ControlRunnable implements Runnable{
        private boolean flag;
        public void run() {
            if(flag)
                onDoSth();//设置需要做的事情
        }
        public void stop(boolean flag){
            this.flag = flag;//停止应该只需要改变flag
        }
        public void reset(){
            //this.flag = true;//重置内容再好好想一想
            //onDoSth();//设置需要做的事情
            i = 0;
            show_1.setText("");show_2.setText("");
            show_3.setText("");show_4.setText("");
            show_5.setText("");show_7.setText("");
            show_6.setText("共收集" + "该位置" + i + "条数据");show_8.setText("");
        }
    }
    BroadcastReceiver wifiScanner = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false);
            if (success){
                get_wifi();
            }else{
                Log.e("WiFi Scan","Can't scan");
                get_fail();
            }
        }
    };
    //UI绑定与设置
    private void bindview() {
        //复选框设置
        cell = (CheckBox) findViewById(R.id.choose_1);
        wifi = (CheckBox) findViewById(R.id.choose_2);
        //按钮设置
        setupBtn = (Button) findViewById(R.id.btn1);
        setupBtn.setOnClickListener(new Click());
        closeBtn = (Button) findViewById(R.id.btn2);
        closeBtn.setOnClickListener(new Click());
        closeBtn.setClickable(false);
        startBtn = (Button) findViewById(R.id.btn3);
        startBtn.setOnClickListener(new Click());
        startBtn.setClickable(false);
        stopBtn = (Button)findViewById(R.id.btn4);
        stopBtn.setOnClickListener(new Click());
        stopBtn.setClickable(false);//默认不可点击，直到输入了表单名字
        //文本编辑框设置
        nameEdit = (EditText) findViewById(R.id.edit1);//表单名获取优先输入
        interval = (EditText) findViewById(R.id.edit3);
        //展示文本设置
        show_1 = (TextView) findViewById(R.id.show_1); // 网络制式 3G/4G
        show_2 = (TextView) findViewById(R.id.show_2); // 基站ID
        show_3 = (TextView) findViewById(R.id.show_3); // RSS
        show_4 = (TextView) findViewById(R.id.show_4); // MAC地址
        show_5 = (TextView) findViewById(R.id.show_5); // Wi-Fi RSSI
        show_6 = (TextView) findViewById(R.id.show_6); // 已收集数据的条数
        show_7 = (TextView) findViewById(R.id.show_7); // Wi-Fi名称 (用于过滤)
        show_8 = (TextView) findViewById(R.id.show_8); // 磁场强度
        //定义
        wifi_rssi = new ArrayList<>();
        ssid = new ArrayList<>();
        bssid = new ArrayList<>();
        cell_rssi = new ArrayList<>();
        cell_pci = new ArrayList<>();
        cellType = new ArrayList<>();
        cell_id = new ArrayList<>();
    }
    private File CREndOpenFile(String absolutePath) throws IOException { //生成并打开文件
        String storagePath = absolutePath, file = " ";
        SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-HH-mm"); //设置时间格式
        formatter.setTimeZone(TimeZone.getTimeZone("GMT+08")); //设置时区
        Date curDate = new Date(System.currentTimeMillis()); //获取当前时间
        file = nameEdit.getText().toString() + '_' + formatter.format(curDate);   //格式转换
        storagePath += "/"+ file + ".txt";
        File myData = new File(storagePath);
        if(myData.exists()) // 不存在则删除
            myData.delete();
        Log.d("CREndOpenFile","Create File:" + file);
        myData.getParentFile().mkdirs();
        myData.createNewFile();
        return myData;
    }
    //监听的内容
    private void onDoSth(){
        i += 1;
        if(isSimAvail){
            operator = tManager.getNetworkOperator(); // 获取手机
            tManager.getPhoneType();
            get_cellRssi();
        }else{
            cellType.clear();cell_rssi.clear();cell_id.clear();cell_pci.clear();
            cellType.add("/");
            cell_rssi.add("/");
            cell_id.add("/");
            cell_pci.add("/");
        }
        time = timeFormat2.format(Calendar.getInstance().getTime());
        //用如下intent进行替换
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(mWifi.SCAN_RESULTS_AVAILABLE_ACTION);
        getApplicationContext().registerReceiver(wifiScanner,intentFilter);
        boolean success = mWifi.startScan();
        //if(!success){
        //    Log.e("WiFi Scan","Can't scan");
        //    get_fail();
        //}
        //替换如上
        setDataBase();
        setView();
        mainHandler.postDelayed(onDoRunnable,inTime);
        cellType.clear();
        cell_pci.clear();
        cell_id.clear();
        cell_rssi.clear();
        wifi_rssi.clear();
        bssid.clear();
        ssid.clear();
    }
    //监听时设置实时显示
    public void setView(){
        if(!operator.isEmpty()){
            show_1.setText(cellType.get(0));
            if(cell_id.get(0) != "/") show_2.setText(cell_id.get(0));
            else show_2.setText("pci:" + cell_pci.get(0));
            show_3.setText(cell_rssi.get(0));
        }
        else {
            show_1.setText("/");
            show_2.setText("/");
            show_3.setText("/");
        }
        if(!bssid.isEmpty()) {
            show_7.setText(bssid.get(0));
            show_5.setText(wifi_rssi.get(0));
            show_4.setText(ssid.get(0));
            show_6.setText("共收集该位置" + i + "条数据");
        }
        else{
            show_4.setText("/");
            show_5.setText("/");
            show_7.setText("/");
        }
    }
      //导入数据库
    private void setDataBase(){
        ContentValues value_1 = new ContentValues();
        value_1.put(TIME_REC,time);
        value_1.put(OPERATOR_NAME,operator);
        value_1.put(NETWORK_TYPE,cellType.toString());
        value_1.put(CELL_INFO,cell_id.toString());
        value_1.put(CELL_PCI,cell_pci.toString());
        value_1.put(CELL_RSSI,cell_rssi.toString());
        value_1.put(MAC,bssid.toString());
        value_1.put(SSID,ssid.toString());
        value_1.put(WIFI_RSSI,wifi_rssi.toString());
        value_1.put(PARKING_NAME,name);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.insert(TABLE_NAME,null,value_1);
        db.close();
    }

    //建立数据库的操作
    private void openOp(){
        if (nameEdit.getText() != null) {
            name = nameEdit.getText().toString();//1.获取表单名
            helper = new DBconnection(MainActivity.this);//2.构造数据库
            SQLiteDatabase db = helper.getWritableDatabase();//3.获取可读的表
            closeBtn.setClickable(true);
            startBtn.setClickable(true);
            stopBtn.setClickable(true);//4.将三个按钮设置为true
            interval.setFocusable(true);
            interval.setCursorVisible(true);
            cursor = db.query("test", null, null, null, null, null, null);
            cursor.moveToNext();//自动指针移动到下一个
            count = 0;//记录的初始化次数为0
            Toast.makeText(getApplicationContext(), "成功指定", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "还未给定名称！", Toast.LENGTH_SHORT).show();
        }
    }
    //关闭数据库时的操作
    private void closeOp(){
        cursor.close();
        interval.setClickable(false);
        interval.setFocusable(false);
        startBtn.setClickable(false);
        stopBtn.setClickable(false);
        nameEdit.setText("");//清空
        Toast.makeText(getApplicationContext(), "数据库已经关闭", Toast.LENGTH_SHORT).show();
    }
    //获取蜂窝数据
      //监听类的声明
    private void get_cellRssi(){

        int lac = 0;
        int cid = 0;
        int pci = 0;
        int cellRssi = 0;
        String type = "";
        cellType.clear();
        cell_rssi.clear();
        cell_id.clear();
        cell_pci.clear();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { return; }
        list2 = tManager.getAllCellInfo(); // 获取所有蜂窝数据
        if(list2 != null && list2.size() > 0){
            for(CellInfo cellInfo : list2){
                if (cellInfo instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                    lac = cellInfoGsm.getCellIdentity().getLac(); // 蜂窝区码
                    cid = cellInfoGsm.getCellIdentity().getCid(); // 基站编号
                    pci = -1; // 没有定义pci，默认加入-1
                    cellRssi = cellInfoGsm.getCellSignalStrength().getDbm(); // 获取信号强度
                    type = "Gsm"; // 记录网络制式
                } else if (cellInfo instanceof CellInfoLte) {
                    CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                    lac = cellInfoLte.getCellIdentity().getTac();
                    cid = cellInfoLte.getCellIdentity().getCi();
                    pci = cellInfoLte.getCellIdentity().getPci();
                    cellRssi = cellInfoLte.getCellSignalStrength().getDbm();
                    type = "Lte";
                } else if (cellInfo instanceof CellInfoCdma) {
                    CellInfoCdma cellInfoCdma = (CellInfoCdma) cellInfo;
                    lac = cellInfoCdma.getCellIdentity().getNetworkId();
                    cid = cellInfoCdma.getCellIdentity().getBasestationId();
                    cellRssi = cellInfoCdma.getCellSignalStrength().getDbm();
                    pci = -1;
                    type = "Cdma";
                }
                if(!(lac ==  2147483647 || lac == 0)  && !(cid == 2147483647 || cid == -1)) {
                    cellType.add(type);
                    cell_rssi.add(String.valueOf(cellRssi));
                    cell_id.add(String.valueOf(lac) + ',' + cid);
                    cell_pci.add(String.valueOf(pci));
                }
                else{
                    if(type == "Lte"){
                        cellType.add(type);
                        cell_rssi.add(String.valueOf(cellRssi));
                        cell_id.add(String.valueOf(lac) + ',' +  cid);
                        cell_pci.add(String.valueOf(pci));
                    }
                }
            }
        }
    }
    private void get_fail(){
        //接收失败则设为-1
        ssid.clear();
        bssid.clear();
        wifi_rssi.clear();
        ssid.add("/");
        bssid.add("/");
        wifi_rssi.add("/");
    }
    //获取WiFi数据
    private void get_wifi(){
        mWifi.startScan();
        ssid.clear();
        bssid.clear();
        wifi_rssi.clear();
        list =mWifi.getScanResults();
        if(!list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                strSsid = list.get(i).SSID;
                strBssid = list.get(i).BSSID;
                strLevel = String.valueOf(list.get(i).level);
                ssid.add(strSsid);
                bssid.add(strBssid);
                wifi_rssi.add(strLevel);
            }
        }
        else{
            ssid.add("/");
            bssid.add("/");
            wifi_rssi.add("/");
        }
    }
}