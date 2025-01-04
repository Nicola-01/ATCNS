package com.example.simplepasswordmanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class PasswordValidator extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String password = intent.getStringExtra("password");

        if (password != null && validatePassword(password)) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("password", password);
            setResult(RESULT_OK, resultIntent);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private boolean validatePassword(String password) {
        if (password.length() < 8)
            return false;

        boolean hasUpperCase = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c))
                hasUpperCase = true;
            if (!Character.isLetterOrDigit(c))
                hasSpecialChar = true;
        }

        return hasUpperCase && hasSpecialChar;
    }
}
