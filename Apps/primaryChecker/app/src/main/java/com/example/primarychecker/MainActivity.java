package com.example.primarychecker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
    }

    public void check(View view) {
        Log.i("MainActivity", "Starting calculator...");

        Intent i = new Intent();
        i.setAction("com.example.primarychecker.action.PRIMARYCHECKER");
        i.setClass(this, PrimaryChecker.class);

        EditText number = findViewById(R.id.number);

        i.putExtra("number", Integer.parseInt(number.getText().toString()));

        startActivityForResult(i, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 0) {
            TextView resultView = findViewById(R.id.result);
            if (resultCode == RESULT_OK) {
                boolean result = data.getBooleanExtra("result", false);
                Log.i("MainActivity", "is primary: " + result);

                resultView.setText(String.valueOf(result));
            } else {
                Log.e("MainActivity", "Error");
                resultView.setText("Error");
            }
        }
    }
}
