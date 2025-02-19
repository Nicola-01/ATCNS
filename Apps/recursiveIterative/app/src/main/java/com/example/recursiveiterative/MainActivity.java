package com.example.recursiveiterative;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = new Intent(this, recursiveIterativeActivity.class);
        i.setAction("com.example.recursiveiterative.action.RECURSIVEITERATIVE");

        i.putExtra("method", "recursive");
        i.putExtra("n", 10);

        startActivityForResult(i, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
