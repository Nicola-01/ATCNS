package com.example.calculator;

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

    public void calculate(View view) {
        Log.i("MainActivity", "Starting calculator...");

        Intent i = new Intent();
        i.setAction("com.example.calculator.action.CALCULATE");
        i.setClass(this, Calculator.class);

        EditText number1 = findViewById(R.id.number1);
        EditText number2 = findViewById(R.id.number2);

        RadioGroup operationsGroup = findViewById(R.id.operations);

        int selectedId = operationsGroup.getCheckedRadioButtonId();

        RadioButton selectedButton = findViewById(selectedId);

        Bundle b = new Bundle();
        b.putInt("n1", Integer.parseInt(number1.getText().toString()));
        b.putInt("n2", Integer.parseInt(number2.getText().toString()));
        b.putString("Op", selectedButton.getText().toString());

        i.putExtras(b);

        startActivityForResult(i, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                int result = data.getIntExtra("result", 0);
                Log.i("MainActivity", "Calculation result: " + result);

                TextView resultView = findViewById(R.id.result);
                resultView.setText(String.valueOf(result));
            } else {
                Log.e("MainActivity", "Failed to get result from Calculator");
            }
        }
    }

}
