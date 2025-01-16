package com.example.morecomplexapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ExportedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exported);

        // Receive parameters from the Intent
        Intent intent = getIntent();
        String param1 = intent.getStringExtra("param1");
        int param2 = intent.getIntExtra("param2", -1);

        switch (param1) {
            case "value1":
                if (param2 == 42) {
                    // Log OK and proceed with the next activity
                    Log.i("ExportedActivity", "OK: Parameters match value1 and param2=42");
                    Intent newIntent = new Intent(this, ThirdActivity.class);
                    newIntent.putExtra("extraParam", "checkValue");
                    startActivityForResult(newIntent, 1);
                }
                break;

            case "value2":
                if (param2 == 100) {
                    // Log OK and proceed with the next activity
                    Log.i("ExportedActivity", "OK: Parameters match value2 and param2=100");
                    Intent newIntent = new Intent(this, ThirdActivity.class);
                    newIntent.putExtra("extraParam", "checkValue2");
                    startActivityForResult(newIntent, 2);
                }
                break;

            case "value3":
                if (param2 == 200) {
                    // Log OK for this case
                    Log.i("ExportedActivity", "OK: Parameters match value3 and param2=200");
                }
                break;

            default:
                Log.e("ExportedActivity", "Unhandled case for param1: " + param1);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            String resultParam = data.getStringExtra("resultParam");

            // Check the returned parameters
            if ("success".equals(resultParam)) {
                performPrivateOperation();
            } else {
                Log.e("ExportedActivity", "Parameter check failed.");
            }
        } else if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            String resultParam = data.getStringExtra("resultParam");

            // Check the returned parameters for case "value2"
            if ("success".equals(resultParam)) {
                performPrivateOperationForValue2();
            } else {
                Log.e("ExportedActivity", "Parameter check failed for value2.");
            }
        }
    }

    private void performPrivateOperation() {
        Log.i("ExportedActivity", "Private operation executed successfully.");
    }

    private void performPrivateOperationForValue2() {
        Log.i("ExportedActivity", "Private operation for value2 executed successfully.");
    }
}
