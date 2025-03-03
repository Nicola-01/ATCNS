package com.example.complexcalculatorexternalmethods;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class Calculator extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(getString(R.string.app_name), "Start calculator");

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            int num1 = extras.getInt("n1");
            int num2 = extras.getInt("n2");
            String operator = extras.getString("Op");

            int result = 0;

            // Perform the calculation based on the operator
            switch (operator) {
                case "+":
                    result = Operations.add(num1, num2);;
                    break;
                case "-":
                    result = Operations.subtract(num1, num2);
                    break;
                case "*":
                    result = Operations.multiply(num1, num2);
                    break;
                case "/":
                    result = Operations.divide(num1, num2);
                    break;
                default:
                    // Handle invalid operator if necessary
                    Log.e("Calculator", "Unknown operator");
            }

            // Log the result
            Log.i("Calculator", "Result: " + result);

            Intent resultIntent = new Intent();
            resultIntent.putExtra("result", result);
            setResult(RESULT_OK, resultIntent);
            finish();  // End the Calculator activity
        }
    }
}
