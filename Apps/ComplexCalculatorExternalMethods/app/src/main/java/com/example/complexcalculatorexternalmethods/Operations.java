package com.example.complexcalculatorexternalmethods;

public class Operations {

    public static int add(int num1, int num2) {
        return num1 + num2;
    }

    public static int subtract(int num1, int num2) {
        return num1 - num2;
    }

    public static int multiply(int num1, int num2) {
        return num1 * num2;
    }

    public static int divide(int num1, int num2) {
        if (num2 != 0) {
            return num1 / num2;
        } else {
            // Handle division by zero
            System.err.println("Error: Division by zero");
            return 0; // Or throw an exception as per your application's error handling
        }
    }

}
