package com.example.intentreceiver;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ReceiverActivity extends AppCompatActivity {

    private TextView receivedDataTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);

        receivedDataTextView = findViewById(R.id.receivedDataTextView);

        // Receive intent and check for data
        Intent intent = getIntent();
        String receivedData = intent.getStringExtra("data_key");

        if (receivedData != null) {
            if (receivedData.length() > 10) {
                // Additional condition after checking length > 10
                if (receivedData.contains("Android")) {
                    receivedDataTextView.setText("Valid data with 'Android': " + receivedData);
                } else {
                    receivedDataTextView.setText("Data is long but doesn't contain 'Android'.");
                }
            } else if (receivedData.length() < 7) {
                receivedDataTextView.setText("Valid data but too short to contain 'Android': " + receivedData);
            }
        } else {
            receivedDataTextView.setText("No data received.");
        }
    }
}
