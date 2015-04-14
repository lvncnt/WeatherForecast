package com.udacity.example.sunshine;


import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
public class ForecastFragment extends Fragment{

    private ArrayAdapter<String> weatherAdapter;

    public ForecastFragment() {
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]>{

        @Override
        protected String[] doInBackground(String... params) {

            /* Add the network code */
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String forecastJsonStr;
            String[] forecastJsonPar = null;

            String format = "json"; // default URL params
            String units = getString(R.string.pref_temperature_unit_metric);
            String zip = getString(R.string.pref_location_default);
            int numDays = 7;

            try{
                /* Construct the URL for the OpenWeatherMap query */
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

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a string
                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder sb = new StringBuilder();

                if(inputStream == null) return null;

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while((line = reader.readLine()) != null){
                    sb.append(line).append('\n');
                }

                if(sb.length() == 0) return null; // Stream empty, no point in parsing
                forecastJsonStr = sb.toString();

                /* Json Parsing */
                forecastJsonPar = getWeatherDataFromJson(forecastJsonStr, numDays);

            }
            catch(IOException e){
                Log.e("PlaceholderFragment", "Error ", e);
                // If the code didn't successfully get the weather data,
                // there's no point attempting to parse it.
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
            // These are the names of the JSON objects that need to be extracted
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            TimeZone tz_utc = TimeZone.getTimeZone("UTC"); // UTC timezone
            Calendar calendar;

            String unitType;
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            unitType = sharedPreferences.getString(
                    getString(R.string.pref_temperature_unit_key),
                    getString(R.string.pref_temperature_unit_metric));

            String[] resultStrs = new String[numDays];

            for(int i = 0; i < weatherArray.length(); i ++ ){
                String day;
                String description; // description of weather condition
                String highAndLow; // temperature

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                //The date/time is returned as a long: need to convert that into something human-readable
                calendar = new GregorianCalendar(tz_utc);
                calendar.add(Calendar.DATE, i);
                long time = calendar.getTimeInMillis();
                day = getReadableDateString(time);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);
                highAndLow = formatTemperature(high, low, unitType);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStrs;
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatTemperature(double high, double low, String unitType){
            if(unitType.equals(getString(R.string.pref_temperature_unit_imperial))){
                high = (high * 9/5) + 32;
                low = (low * 9/5) + 32;
            }
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
            weatherAdapter.clear(); //clear the ForecastAdapter of all the previous forecast entries
            for(String s: results){
                weatherAdapter.add(s); //Then add each new forecast entry one by one to the ForecastAdapter
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        weatherAdapter = new ArrayAdapter<String>(
                getActivity(), // The current context (this fragment's activity)
                R.layout.list_item,  // The name of the layout ID
                R.id.list_item_textview, // The ID of the textview to populate
                new ArrayList<String>() );  // Forecast Data


        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it
        final ListView listView = (ListView) rootView.findViewById(R.id.list_item_textview);
        listView.setAdapter(weatherAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

                String forecast = weatherAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailActivity.class);
                intent.putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });
        return rootView;
    }

    /* Update Data on Activity Start */
    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    private void updateWeather(){
        // modify the code so that when the Refresh menu item is selected,
        // it executes the FetchWeatherTask
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        // modify FetchWeatherTask to accept a location parameter
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(
                getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        weatherTask.execute(location); // postal code param passed to AsyncTask/doInBackground method
    }

    /* Add Refresh button */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add this line in order for this fragment to handle menu events
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
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}