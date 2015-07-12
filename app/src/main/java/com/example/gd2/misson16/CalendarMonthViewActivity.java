package com.example.gd2.misson16;


        import java.io.InputStream;
        import java.net.HttpURLConnection;
        import java.net.URL;
        import java.util.ArrayList;

        import javax.xml.parsers.SAXParser;
        import javax.xml.parsers.SAXParserFactory;

        import org.xml.sax.InputSource;
        import org.xml.sax.XMLReader;

        import android.app.Activity;
        import android.app.AlertDialog;
        import android.app.Dialog;
        import android.app.ProgressDialog;
        import android.content.DialogInterface;
        import android.content.Intent;
        import android.database.Cursor;
        import android.os.Bundle;
        import android.os.Handler;
        import android.util.Log;
        import android.view.Menu;
        import android.view.MenuItem;
        import android.view.View;
        import android.view.View.OnClickListener;
        import android.widget.AdapterView;
        import android.widget.AdapterView.OnItemClickListener;
        import android.widget.Button;
        import android.widget.EditText;
        import android.widget.GridView;
        import android.widget.ListView;
        import android.widget.TextView;
        import android.widget.Toast;


public class CalendarMonthViewActivity extends Activity {
    public static final String TAG = "CalendarMonthViewActivity";

    GridView monthView;
    CalendarMonthAdapter monthViewAdapter;

    TextView monthText;

    int curYear;
    int curMonth;

    int curPosition;
    EditText scheduleInput;
    Button saveButton;

    ListView scheduleList;
    ScheduleListAdapter scheduleAdapter;
    ArrayList outScheduleList;

    public static final int REQUEST_CODE_SCHEDULE_INPUT = 1001;
    public static final int WEATHER_PROGRESS_DIALOG = 1002;
    public static final int WEATHER_SAVED_DIALOG = 1003;

    private static final String BASE_URL = "http://www.google.com";
    private static String WEATHER_URL = "http://www.google.com/ig/api?weather=";

    private static boolean weatherCanceled;

    WeatherCurrentCondition weather = null;

    Handler handler = new Handler();

    ScheduleDatabase database;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        monthView = (GridView) findViewById(R.id.monthView);
        monthViewAdapter = new CalendarMonthAdapter(this);
        monthView.setAdapter(monthViewAdapter);

        // set listener
        monthView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MonthItem curItem = (MonthItem) monthViewAdapter.getItem(position);
                int day = curItem.getDay();

                //Toast.makeText(getApplicationContext(), day + "일이 선택되었습니다.", 1000).show();

                monthViewAdapter.setSelectedPosition(position);
                monthViewAdapter.notifyDataSetChanged();

                outScheduleList = monthViewAdapter.getSchedule(position);
                if (outScheduleList == null) {
                    outScheduleList = new ArrayList<ScheduleListItem>();
                }
                scheduleAdapter.scheduleList = outScheduleList;

                scheduleAdapter.notifyDataSetChanged();

                // show ScheduleInputActivity if the position is already selected
                if (position == curPosition) {
                    showScheduleInput();
                }

                // set schedule to the TextView
                curPosition = position;

            }
        });

        monthText = (TextView) findViewById(R.id.monthText);
        setMonthText();

        Button monthPrevious = (Button) findViewById(R.id.monthPrevious);
        monthPrevious.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                monthViewAdapter.setPreviousMonth();
                monthViewAdapter.notifyDataSetChanged();

                setMonthText();
            }
        });

        Button monthNext = (Button) findViewById(R.id.monthNext);
        monthNext.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                monthViewAdapter.setNextMonth();
                monthViewAdapter.notifyDataSetChanged();

                setMonthText();
            }
        });


        curPosition = -1;

        scheduleList = (ListView)findViewById(R.id.scheduleList);
        scheduleAdapter = new ScheduleListAdapter(this);
        scheduleList.setAdapter(scheduleAdapter);


        // open database
        if (database != null) {
            database.close();
            database = null;
        }

        database = ScheduleDatabase.getInstance(this);
        boolean isOpen = database.open();
        if (isOpen) {
            Log.d(TAG, "Schedule database is open.");
        } else {
            Log.d(TAG, "Schedule database is not open.");
        }

        // load all items from the database
        loadAllItems();

    }


    private void loadAllItems() {
        // load all weather items
        String SQL = "select _id, WCONDITION, WICON, WYEAR, WMONTH, WPOSITION from " + ScheduleDatabase.TABLE_WEATHER_INFO;
        Cursor cursor = database.rawQuery(SQL);

        int weatherCount = cursor.getCount();
        Log.d(TAG, "count of all weather items : " + weatherCount);
        if (weatherCount < 1) {
            return;
        }

        int curIndex = 0;
        monthViewAdapter.clearWeatherHash();
        while(cursor.moveToNext()) {
            int _id = cursor.getInt(0);
            String wCondition = cursor.getString(1);
            String wIconUrl = cursor.getString(2);
            int wYear = cursor.getInt(3);
            int wMonth = cursor.getInt(4);
            int wPosition = cursor.getInt(5);

            Log.d(TAG, "_id : " + _id + ", " + wCondition + ", " + wIconUrl + ", " + wYear + ", " + wMonth + ", " + wPosition);

            // make a WeatherCurrentCondition instance
            WeatherCurrentCondition aWeather = new WeatherCurrentCondition();
            aWeather.setCondition(wCondition);
            aWeather.setIconURL(wIconUrl);

            monthViewAdapter.putWeather(wYear, wMonth, wPosition, aWeather);

            curIndex++;
        }

        cursor.close();

        // load all schedule items
        SQL = "select _id, STIME, SMESSAGE, SYEAR, SMONTH, SPOSITION from " + ScheduleDatabase.TABLE_SCHEDULE_INFO;
        cursor = database.rawQuery(SQL);

        int scheduleCount = cursor.getCount();
        Log.d(TAG, "count of all schedule items : " + scheduleCount);
        if (scheduleCount < 1) {
            return;
        }

        curIndex = 0;
        monthViewAdapter.clearScheduleHash();
        if (outScheduleList == null) {
            outScheduleList = new ArrayList();
        } else {
            outScheduleList.clear();
        }

        while(cursor.moveToNext()) {
            int _id = cursor.getInt(0);
            String sTime = cursor.getString(1);
            String sMessage = cursor.getString(2);
            int sYear = cursor.getInt(3);
            int sMonth = cursor.getInt(4);
            int sPosition = cursor.getInt(5);

            Log.d(TAG, "_id : " + _id + ", " + sTime + ", " + sMessage + ", " + sYear + ", " + sMonth + ", " + sPosition);

            // make a ScheduleListItem instance
            ScheduleListItem aSchedule = new ScheduleListItem();
            aSchedule.setTime(sTime);
            aSchedule.setMessage(sMessage);

            ArrayList<ScheduleListItem> curScheduleList = monthViewAdapter.getSchedule(sYear, sMonth, sPosition);
            if (curScheduleList == null) {
                curScheduleList = new ArrayList<ScheduleListItem>();
            }
            curScheduleList.add(aSchedule);
            monthViewAdapter.putSchedule(sYear, sMonth, sPosition, curScheduleList);


            curIndex++;
        }

        cursor.close();


    }


    private void setMonthText() {
        curYear = monthViewAdapter.getCurYear();
        curMonth = monthViewAdapter.getCurMonth();

        monthText.setText(curYear + "Y " + (curMonth+1) + "M");
    }


    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        addOptionMenuItems(menu);

        return true;
    }

    private void addOptionMenuItems(Menu menu) {
        int id = Menu.FIRST;
        menu.clear();

        menu.add(id, id, Menu.NONE, "일정 추가");

        id = Menu.FIRST+1;
        menu.add(id, id, Menu.NONE, "오늘날씨 저장");
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case Menu.FIRST:
                showScheduleInput();

                return true;
            case Menu.FIRST+1:
                getCurrentWeather();

                return true;
            default:
                break;
        }

        return false;
    }

    /**
     * get current weather
     */
    private void getCurrentWeather() {
        weatherCanceled = false;

        showDialog(WEATHER_PROGRESS_DIALOG);

        CurrentWeatherSaveThread thread = new CurrentWeatherSaveThread();
        thread.start();

    }


    class CurrentWeatherSaveThread extends Thread {
        public CurrentWeatherSaveThread() {

        }

        public void run() {
            try {
                SAXParserFactory parserFactory = SAXParserFactory.newInstance();
                SAXParser parser = parserFactory.newSAXParser();
                XMLReader reader = parser.getXMLReader();

                WeatherHandler whandler = new WeatherHandler();
                reader.setContentHandler(whandler);

                String queryStr = WEATHER_URL + "Seoul,Korea";
                URL urlForHttp = new URL(queryStr);

                InputStream instream = getInputStreamUsingHTTP(urlForHttp);

                if (instream != null) {
                    reader.parse(new InputSource(instream));

                    weather = whandler.getWeather();

                    handler.post(completedRunnable);
                } else {
                    removeDialog(WEATHER_PROGRESS_DIALOG);
                }

            } catch(Exception ex) {
                ex.printStackTrace();
            }

        }
    }

    private InputStream getInputStreamUsingHTTP(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);

        int resCode = conn.getResponseCode();
        Log.d(TAG, "Response Code : " + resCode);

        if (weatherCanceled) {
            return null;
        }

        InputStream instream = conn.getInputStream();
        return instream;
    }

    Runnable completedRunnable = new Runnable() {
        public void run() {
            removeDialog(WEATHER_PROGRESS_DIALOG);

            if (weatherCanceled) {
                weather = null;
            } else {
                Toast.makeText(getApplicationContext(), "Current Weather : " + weather.getCondition() + ", " + weather.getIconURL(), Toast.LENGTH_LONG).show();

                // set today weather
                int todayPosition = monthViewAdapter.getTodayPosition();
                Log.d(TAG, "today position : " + todayPosition);

                monthViewAdapter.putWeather(monthViewAdapter.todayYear, monthViewAdapter.todayMonth, todayPosition, weather);

                // save also to the database
                saveWeather(monthViewAdapter.todayYear, monthViewAdapter.todayMonth, todayPosition, weather);

                monthViewAdapter.notifyDataSetChanged();

                showDialog(WEATHER_SAVED_DIALOG);
            }
        }
    };


    private void saveWeather(int year, int month, int position, WeatherCurrentCondition weather) {
        try {
            String wCondition = weather.getCondition();
            String wIconUrl = weather.getIconURL();

            database.execSQL( "insert into " + ScheduleDatabase.TABLE_WEATHER_INFO + "(WCONDITION, WICON, WYEAR, WMONTH, WPOSITION) values ('" + wCondition + "', '" + wIconUrl + "', " + year + ", " + month + ", " + position + ");" );
        } catch(Exception ex) {
            Log.e(TAG, "Exception in executing insert SQL.", ex);
        }
    }



    private void saveSchedule(int year, int month, int position, ScheduleListItem aItem) {
        try {
            String sTime = aItem.getTime();
            String sMessage = aItem.getMessage();

            database.execSQL( "insert into " + ScheduleDatabase.TABLE_SCHEDULE_INFO + "(STIME, SMESSAGE, SYEAR, SMONTH, SPOSITION) values ('" + sTime + "', '" + sMessage + "', " + year + ", " + month + ", " + position + ");" );
        } catch(Exception ex) {
            Log.e(TAG, "Exception in executing insert SQL.", ex);
        }
    }


    protected void onDestroy() {
        // open database
        if (database != null) {
            database.close();
            database = null;
        }

        super.onDestroy();
    }

    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case WEATHER_PROGRESS_DIALOG:
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("날씨정보 가져오는 중...");
                progressDialog.setCancelable(true);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        weatherCanceled = true;
                    }
                });

                return progressDialog;
            case WEATHER_SAVED_DIALOG:
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                alertBuilder.setMessage("날씨정보를 저장하였습니다.");
                alertBuilder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alertDialog = alertBuilder.create();
                return alertDialog;
        }

        return null;
    }


    private void showScheduleInput() {
        Intent intent = new Intent(this, ScheduleInputActivity.class);

        int todayPosition = monthViewAdapter.getTodayPosition();
        WeatherCurrentCondition weather = monthViewAdapter.getWeather(monthViewAdapter.todayYear, monthViewAdapter.todayMonth, todayPosition);
        if (weather != null) {
            String weatherIconUrl = weather.getIconURL();
            intent.putExtra("weatherIconUrl", weatherIconUrl);
        }

        startActivityForResult(intent, REQUEST_CODE_SCHEDULE_INPUT);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CODE_SCHEDULE_INPUT) {
            if (intent == null) {
                return;
            }

            String time = intent.getStringExtra("time");
            String message = intent.getStringExtra("message");
            int selectedWeather = intent.getIntExtra("weather", 0);

            if (message != null) {
                Toast toast = Toast.makeText(getBaseContext(), "time : " + time + ", message : " + message + ", selectedWeather : " + selectedWeather, Toast.LENGTH_LONG);
                toast.show();

                ScheduleListItem aItem = new ScheduleListItem(time, message);


                if (outScheduleList == null) {
                    outScheduleList = new ArrayList();
                }
                outScheduleList.add(aItem);

                monthViewAdapter.putSchedule(curPosition, outScheduleList);

                // save also to the database
                saveSchedule(monthViewAdapter.getCurYear(), monthViewAdapter.getCurMonth(), curPosition, aItem);

                scheduleAdapter.scheduleList = outScheduleList;
                scheduleAdapter.notifyDataSetChanged();

                // put weather
                WeatherCurrentCondition aWeather = new WeatherCurrentCondition();
                if (selectedWeather == 0) {
                    aWeather.setCondition("Sunny");
                    aWeather.setIconURL("/ig/images/weather/sunny.gif");
                } else if (selectedWeather == 1) {
                    aWeather.setCondition("Cloudy");
                    aWeather.setIconURL("/ig/images/weather/cloudy.gif");
                } else if (selectedWeather == 2) {
                    aWeather.setCondition("Rain");
                    aWeather.setIconURL("/ig/images/weather/rain.gif");
                } else if (selectedWeather == 3) {
                    aWeather.setCondition("Snow");
                    aWeather.setIconURL("/ig/images/weather/snow.gif");
                }

                monthViewAdapter.putWeather(curPosition, aWeather);

                // save also to the database
                saveWeather(monthViewAdapter.getCurYear(), monthViewAdapter.getCurMonth(), curPosition, aWeather);

                monthViewAdapter.notifyDataSetChanged();

            }
        }

    }

}