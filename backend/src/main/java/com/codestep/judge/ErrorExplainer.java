package com.codestep.judge;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ErrorExplainer {

    private static final Pattern EXCEPTION = Pattern.compile("(?:Exception in thread \"\\w+\" )?([\\w.$]+(?:Exception|Error))");
    private static final Pattern USER_LINE = Pattern.compile("\\((Solution|Main)\\.java:(\\d+)\\)");

    private static final Map<String, String> RUNTIME = Map.ofEntries(
            Map.entry("ArrayIndexOutOfBoundsException",
                    "You tried to read or write an array position that doesn't exist. Arrays start at index 0, so the last valid index is length - 1. Check your loop condition: `i < nums.length`, not `i <= nums.length`."),
            Map.entry("StringIndexOutOfBoundsException",
                    "You asked for a character position that isn't in the string. Valid positions go from 0 to length() - 1."),
            Map.entry("NullPointerException",
                    "You used a variable that holds `null` (nothing) as if it held an object. Check which variable on that line was never assigned."),
            Map.entry("ArithmeticException",
                    "A math operation is impossible, usually dividing an integer by zero. Check for a zero before you divide."),
            Map.entry("NumberFormatException",
                    "You tried to turn text into a number, but the text isn't a valid number. Check what you're passing to Integer.parseInt."),
            Map.entry("InputMismatchException",
                    "Your program expected one type of value (like an int) but found something else. Make sure you read the input in the order it's given."),
            Map.entry("NoSuchElementException",
                    "You tried to read more input than there is. Check how many values you read compared to how many the problem gives you."),
            Map.entry("StackOverflowError",
                    "A method called itself too many times without stopping. Make sure your recursion has a base case that is actually reached."),
            Map.entry("ClassCastException",
                    "You tried to treat an object as a type it isn't."),
            Map.entry("NegativeArraySizeException",
                    "You tried to create an array with a negative size.")
    );

    private static final Map<String, String> COMPILE = new LinkedHashMap<>();

    static {
        COMPILE.put("missing return statement",
                "Your method promises to return a value, but some path through it ends without a `return`. Add a return at the end of the method.");
        COMPILE.put("';' expected",
                "Java needs a semicolon `;` at the end of each statement. Look at the line shown, or the one just before it.");
        COMPILE.put("cannot find symbol",
                "You used a name Java doesn't recognize. This is usually a typo, a variable used before it's declared, or a variable used outside the `{ }` block it was created in.");
        COMPILE.put("incompatible types",
                "You put a value of one type where another type was expected, like storing a `double` in an `int`, or returning a `String` from a method that returns `int`.");
        COMPILE.put("reached end of file while parsing",
                "A `{` was never closed. Count your braces: every `{` needs a matching `}`.");
        COMPILE.put("might not have been initialized",
                "You read a variable before giving it a value. Give it a starting value when you declare it, like `int sum = 0;`.");
        COMPILE.put("should be declared in a file named",
                "A file can only have one public class. Remove `public` from the extra classes.");
        COMPILE.put("class, interface, enum, or record expected",
                "There is code outside a class, often from an extra `}` that closed the class too early.");
        COMPILE.put("is already defined",
                "You declared the same variable twice in the same scope. Remove the type the second time and just assign to it.");
        COMPILE.put("unreachable statement",
                "There is code after a `return`, `break` or `continue` that can never run.");
    }

    public String cleanCompileOutput(String output) {
        return output.replace("/tmp/work/src/", "");
    }

    public String explainCompile(String output) {
        for (Map.Entry<String, String> entry : COMPILE.entrySet()) {
            if (output.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "Java couldn't compile your code. Read the first error message: it names the line and what Java expected there.";
    }

    public String explainRuntime(String stderr) {
        if (stderr.contains("Main method not found") || stderr.contains("Could not find or load main class")) {
            return "Java couldn't find where your program starts. A full program needs a `public static void main(String[] args)` method inside a class.";
        }
        Matcher matcher = EXCEPTION.matcher(stderr);
        String explanation = "Your program crashed while running.";
        if (matcher.find()) {
            String fullName = matcher.group(1);
            String simpleName = fullName.substring(fullName.lastIndexOf('.') + 1);
            explanation = "Your program crashed with " + simpleName + ". "
                    + RUNTIME.getOrDefault(simpleName, "Read the message below to see what went wrong.");
        }
        Matcher line = USER_LINE.matcher(stderr);
        while (line.find()) {
            if (line.group(1).equals("Solution") || !stderr.contains("Solution.java")) {
                return explanation + " It happened on line " + line.group(2) + " of your code.";
            }
        }
        return explanation;
    }

    public String explainTimeLimit(int seconds) {
        return "Your code ran longer than " + seconds + " second(s). This usually means a loop never ends: check that the loop variable changes on every pass and that the condition eventually becomes false.";
    }

    public String explainMemoryLimit() {
        return "Your code used too much memory. Check for arrays or lists that grow without stopping.";
    }

    public String explainWrongAnswer() {
        return "Your code ran without errors but printed a different answer. Trace the input by hand with your code and compare each step to what you expected.";
    }
}
