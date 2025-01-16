package com.example.morecomplexapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editTextParam1;
    private EditText editTextParam2;
    private TextView parametersPassedMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize EditTexts and Button
        editTextParam1 = findViewById(R.id.editTextParam1);
        editTextParam2 = findViewById(R.id.editTextParam2);
        Button startButton = findViewById(R.id.startButton);
        parametersPassedMessage = findViewById(R.id.parametersPassedMessage);

        //parametersPassedMessage.setText("These are the parameters that passed the constraints");

        // Set up Button click listener
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String param1 = editTextParam1.getText().toString();
                String param2String = editTextParam2.getText().toString();

                // Validate the inputs
                if (param1.isEmpty() || param2String.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please fill in both parameters", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Try to parse param2 as an integer
                int param2;
                try {
                    param2 = Integer.parseInt(param2String);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Invalid number for param2", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Create an Intent to call the ExportedActivity
                Intent intent = new Intent(MainActivity.this, ExportedActivity.class);
                intent.putExtra("param1", param1);
                intent.putExtra("param2", param2);
                startActivity(intent);
            }
        });
    }
}