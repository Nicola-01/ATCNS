package com.example.primarychecker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.math.BigInteger;

public class PrimaryChecker extends Activity {

    private static final int MIN = 0;
    private static final int MAX = 50;
    private static int NEGATIVE = -2;
    private static final int MIDDLE = 25;
    public final String PROVA = "prova";
    public static String TEST = "test";
    private static final char CHAR = 'x';

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(getString(R.string.app_name), "Start primary checker");

        Intent i = getIntent();
        Intent resultIntent = new Intent();

        int n = i.getIntExtra("number", 0);
        String string = i.getStringExtra("string");

        assert string != null;
        if (string.equals(TEST))
            if (n > MIN && n <= MAX) { // Check if the number is between 1 and 49
                BigInteger number = new BigInteger(String.valueOf(n));
                Boolean result = number.isProbablePrime(10);
                Log.i(getString(R.string.app_name), "Result: " + result);
                resultIntent.putExtra("result", result);
                setResult(RESULT_OK, resultIntent);
            } else {
                Log.e(getString(R.string.app_name), "Error");
                setResult(RESULT_CANCELED);
            }

        finish();
    }
}
