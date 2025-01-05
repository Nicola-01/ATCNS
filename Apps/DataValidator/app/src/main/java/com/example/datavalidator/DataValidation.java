package com.example.datavalidator;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataValidation extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.data_validation);

        TextView resultText = findViewById(R.id.resultText);

        String inputData = getIntent().getStringExtra("inputData");

        if (isValidData(inputData)) {
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            if (inputData.equals(todayDate)) {
                resultText.setText("The input matches today's date: " + todayDate);
            } else {
                resultText.setText("The input does not match today's date. Today's date is: " + todayDate);
            }
        } else {
            Toast.makeText(this, "Invalid data format. Please use yyyy-MM-dd.", Toast.LENGTH_SHORT).show();
            resultText.setText("Invalid data format.");
        }
    }

    private boolean isValidData(String data) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            format.setLenient(false);
            format.parse(data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}