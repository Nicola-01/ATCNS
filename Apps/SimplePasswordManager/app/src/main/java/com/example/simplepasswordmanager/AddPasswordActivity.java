package com.example.simplepasswordmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddPasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_password);

        EditText passwordInput = findViewById(R.id.password_input);
        Button saveButton = findViewById(R.id.save_button);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String password = passwordInput.getText().toString();
                Intent intent = new Intent(AddPasswordActivity.this, PasswordValidator.class);
                intent.putExtra("password", password);
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("password", data.getStringExtra("password"));
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            Toast.makeText(this, "Password validation failed", Toast.LENGTH_SHORT).show();
        }
    }
}
