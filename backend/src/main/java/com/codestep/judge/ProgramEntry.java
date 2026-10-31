package com.codestep.judge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ProgramEntry(String fileName, String mainClass) {

    private static final Pattern PUBLIC_CLASS = Pattern.compile("\\bpublic\\s+(?:final\\s+|abstract\\s+)*class\\s+(\\w+)");
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\bclass\\s+(\\w+)");
    private static final Pattern MAIN_METHOD = Pattern.compile("\\bstatic\\s+void\\s+main\\s*\\(");

    static ProgramEntry detect(String code) {
        Matcher publicClass = PUBLIC_CLASS.matcher(code);
        String primaryClass = publicClass.find() ? publicClass.group(1) : null;

        String mainClass = null;
        Matcher main = MAIN_METHOD.matcher(code);
        if (main.find()) {
            Matcher declaration = CLASS_DECLARATION.matcher(code);
            while (declaration.find() && declaration.start() < main.start()) {
                mainClass = declaration.group(1);
            }
        }

        if (primaryClass == null) {
            primaryClass = mainClass != null ? mainClass : "Main";
        }
        return new ProgramEntry(primaryClass + ".java", mainClass != null ? mainClass : primaryClass);
    }
}
