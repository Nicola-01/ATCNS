package com.example.recursiveiterative;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class recursiveIterativeActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();

        String method = i.getStringExtra("method");
        int n = i.getIntExtra("n", 0);
        
        int methodUsed;

        switch (method) {
            case "recursive": n = recursive(n); methodUsed = 1; break;
            case "for": n = forMethod(n); methodUsed = 2; break;
            case "while": n = whileMethod(n); methodUsed = 3; break;
            case "doWhile": n = doWhileMethod(n); methodUsed = 3; break;
            default:
                Log.e("RECURSIVEITERATIVE", "Unknown operator");
                methodUsed = -1;
        }

        Log.i("RECURSIVEITERATIVE", "methodUsed: " + methodUsed);
        Log.i("RECURSIVEITERATIVE", "n: " + n);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("n", n);
        resultIntent.putExtra("methodUsed", methodUsed);
        setResult(RESULT_OK, resultIntent);
        finish();  // End the Calculator activity

    }

    // Recursive method for Fibonacci
    private int recursive(int n) {
        if (n <= 0)
            return 0; // Base case for n = 0
        if (n == 1)
            return 1; // Base case for n = 1
        return recursive(n - 1) + recursive(n - 2);
    }

    // Fibonacci using a for loop
    private int forMethod(int n) {
        if (n <= 0)
            return 0; // Base case for n = 0
        if (n == 1)
            return 1; // Base case for n = 1

        int fibPrev = 0;
        int fibCurr = 1;
        for (int i = 2; i <= n; i++) {
            int fibNext = fibPrev + fibCurr;
            fibPrev = fibCurr;
            fibCurr = fibNext;
        }
        return fibCurr;
    }

    // Fibonacci using a while loop
    private int whileMethod(int n) {
        if (n <= 0)
            return 0; // Base case for n = 0
        if (n == 1)
            return 1; // Base case for n = 1

        int fibPrev = 0;
        int fibCurr = 1;
        int i = 2;
        while (i <= n) {
            int fibNext = fibPrev + fibCurr;
            fibPrev = fibCurr;
            fibCurr = fibNext;
            i++;
        }
        return fibCurr;
    }

    // Fibonacci using a do-while loop
    private int doWhileMethod(int n) {
        if (n <= 0)
            return 0; // Base case for n = 0
        if (n == 1)
            return 1; // Base case for n = 1

        int fibPrev = 0;
        int fibCurr = 1;
        int i = 2;
        do {
            int fibNext = fibPrev + fibCurr;
            fibPrev = fibCurr;
            fibCurr = fibNext;
            i++;
        } while (i <= n);
        return fibCurr;
    }
}
