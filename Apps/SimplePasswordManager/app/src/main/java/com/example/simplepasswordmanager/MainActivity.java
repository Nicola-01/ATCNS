package com.example.simplepasswordmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ArrayList<String> passwordList;
    private PasswordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        passwordList = new ArrayList<>();
        adapter = new PasswordAdapter(this, passwordList);

        ListView listView = findViewById(R.id.password_list);
        listView.setAdapter(adapter);

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddPasswordActivity.class);
            startActivityForResult(intent, 1);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            String password = data.getStringExtra("password");
            if (password != null) {
                if (isPasswordDuplicate(password))
                    Toast.makeText(this, "Password already exists!", Toast.LENGTH_SHORT).show();
                else {
                    passwordList.add(password);
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }

    private boolean isPasswordDuplicate(String password) {
        return passwordList.contains(password);
    }
}