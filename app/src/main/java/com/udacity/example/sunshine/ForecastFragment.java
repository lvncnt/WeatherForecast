package com.udacity.example.sunshine;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;


/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> weatherAdapter;

    public ForecastFragment() {
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]>{

        @Override
        protected String[] doInBackground(String... params) {

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String forecastJsonStr;
            String[] forecastJsonPar = null;

            // default URL params
            String units = "metric";
            String format = "json";
            String zip = String.valueOf(R.string.pref_location_default);
            int numDays = 7;

            try{
                // Build the URL for the OpenWeatherMap query
                if(params.length != 0){
                      zip = params[0];
                }

                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("api.openweathermap.org")
                        .appendPath("data")
                        .appendPath("2.5")
                        .appendPath("forecast")
                        .appendPath("daily")
                        .appendQueryParameter(QUERY_PARAM, zip)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays));

                URL url = new URL(builder.build().toString());
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder sb = new StringBuilder();

                if(inputStream == null) return null;

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while((line = reader.readLine()) != null){
                    sb.append(line).append('\n');
                }

                if(sb.length() == 0) return null;
                forecastJsonStr = sb.toString();
                forecastJsonPar = getWeatherDataFromJson(forecastJsonStr, numDays);
            }
            catch(IOException e){
                Log.e("PlaceholderFragment", "Error ", e);
                return null;
            }
            catch(JSONException e){
                Log.e("PlaceholderFragment", "Error parsing JSON ", e);
                return null;
            }
            finally{
                if(urlConnection != null) urlConnection.disconnect();
                if(reader != null){
                    try{
                        reader.close();
                    }catch(final IOException e){
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }
            return forecastJsonPar;
        }

        /**
         * JSON parsing
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException
        {
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            TimeZone tz_utc = TimeZone.getTimeZone("UTC");
            Calendar calendar;

            String[] resultStrs = new String[numDays];

            for(int i = 0; i < weatherArray.length(); i ++ ){
                String day;
                String description; // description of weather condition
                String highAndLow; // temperature

                JSONObject dayForecast = weatherArray.getJSONObject(i);
                calendar = new GregorianCalendar(tz_utc);
                calendar.add(Calendar.DATE, i);
                long time = calendar.getTimeInMillis();
                day = getReadableDateString(time);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);
                highAndLow = formatTemperature(high, low);

                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }
            return resultStrs;
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatTemperature(double high, double low){
            // assume the user doesn't care about tenths of a degree.
            return Math.round(high) + "/" + Math.round(low);
        }

        /**
         * convert timestamp (measured in seconds) to valid date.
         */
        private String getReadableDateString(long time){
             DateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM d");
             return simpleDateFormat.format(time);
         }

        @Override
        protected void onPostExecute(String[] results) {
            if(results == null) return;
            weatherAdapter.clear();
            for(String s: results){
                weatherAdapter.add(s);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        /* Initialize the weatherAdapter */
        weatherAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item, R.id.list_item_textview,
               new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        ListView listView = (ListView) rootView.findViewById(R.id.list_item_textview);
        listView.setAdapter(weatherAdapter);

        return rootView;
    }

    /* Update Data on Activity Start */
    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    private void updateWeather(){
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    /* Add Refresh button */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            FetchWeatherTask weatherTask = new FetchWeatherTask();
            weatherTask.execute(); // zip code param passed to AsyncTask/doInBackground method
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}