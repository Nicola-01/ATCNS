package com.example.primarychecker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.math.BigInteger;

public class PrimaryChecker extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(getString(R.string.app_name), "Start primary checker");

        Intent i = getIntent();
        Intent resultIntent = new Intent();

        int n = i.getIntExtra("number", 0);

        if (n > 0 && n < 50) { // Check if the number is between 1 and 49
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
