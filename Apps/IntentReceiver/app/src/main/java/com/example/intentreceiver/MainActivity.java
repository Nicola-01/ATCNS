package com.example.intentreceiver;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editTextData;
    private Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextData = findViewById(R.id.editTextData);
        sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(view -> {
            String data = editTextData.getText().toString();
            if (!data.isEmpty()) {
                Intent intent = new Intent(MainActivity.this, ReceiverActivity.class);
                intent.putExtra("data_key", data); // Sending data
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Please enter some data", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
