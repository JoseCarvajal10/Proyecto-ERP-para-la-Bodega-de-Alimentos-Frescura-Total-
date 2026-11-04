package com.frescuratotal.erp.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Reads and validates what the user types in the console.
 * A single shared Scanner is used so that every view reads from the same input.
 */
public final class ConsoleReader {

    private static final Scanner SCANNER = new Scanner(System.in);

    private ConsoleReader() {
    }

    public static String readLine(String prompt) {
        System.out.print(prompt);
        if (!SCANNER.hasNextLine()) {
            throw new IllegalStateException("Input closed");
        }
        return SCANNER.nextLine().trim();
    }

    /** Keeps asking until the user types something. */
    public static String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field is required.");
        }
    }

    public static int readInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readLine(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    public static BigDecimal readPositiveDecimal(String prompt) {
        while (true) {
            try {
                BigDecimal value = new BigDecimal(readLine(prompt).replace(',', '.'));
                if (value.signum() > 0) {
                    return value;
                }
                System.out.println("The value must be greater than zero.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    /** Reads a date in yyyy-MM-dd format. */
    public static LocalDate readDate(String prompt) {
        while (true) {
            try {
                return LocalDate.parse(readLine(prompt));
            } catch (DateTimeParseException e) {
                System.out.println("Use the format yyyy-MM-dd, for example 2026-12-31.");
            }
        }
    }

    public static boolean confirm(String prompt) {
        String answer = readLine(prompt + " (y/n): ").toLowerCase();
        return answer.equals("y") || answer.equals("yes") || answer.equals("s")
                || answer.equals("si") || answer.equals("sí");
    }
}
