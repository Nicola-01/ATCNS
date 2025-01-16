package com.example.morecomplexapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class ThirdActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third);

        // Perform operations and return result to ExportedActivity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("resultParam", "success");
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
