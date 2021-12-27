package com.tyron.completion.java.rewrite;

import com.sun.source.tree.Scope;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.tyron.completion.java.CompilerProvider;
import com.tyron.completion.java.ParseTask;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.model.Range;
import com.tyron.completion.model.TextEdit;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public class IntroduceLocalVariable implements Rewrite {

    private static final Pattern DIGITS_PATTERN = Pattern.compile("^(.+?)(\\d+)$");

    private final Path file;
    private final String methodName;
    private final TypeMirror type;
    private final long position;

    public IntroduceLocalVariable(Path file, String methodName, TypeMirror type, long position) {
        this.file = file;
        this.methodName = methodName;
        this.type = type;
        this.position = position;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        Map<Path, TextEdit[]> map = new TreeMap<>();
        ParseTask task = compiler.parse(file);

        Range range = new Range(position, position);
        String variableType = EditHelper.printType(type, true);
        String variableName = ActionUtil.guessNameFromMethodName(methodName);
        if (variableName == null) {
            variableName = ActionUtil.guessNameFromType(type);
        }
        if (variableName == null) {
            variableName = "variable";
        }
        while (containsVariableAtScope(variableName, task)) {
            variableName = getVariableName(variableName);
        }
        TextEdit edit = new TextEdit(range,
                ActionUtil.getSimpleName(variableType) + " " + variableName + " = ");
        map.put(file, new TextEdit[]{edit});

        if (!ActionUtil.hasImport(task.root, variableType)) {
            AddImport addImport = new AddImport(file.toFile(), variableType);
            map.putAll(addImport.rewrite(compiler));
        }
        return map;
    }

    private boolean containsVariableAtScope(String name, ParseTask parse) {
        TreePath scan = new FindCurrentPath(parse.task).scan(parse.root, position);
        Scope scope = Trees.instance(parse.task).getScope(scan);
        Iterable<? extends Element> localElements = scope.getLocalElements();
        for (Element element : localElements) {
            if (name.contentEquals(element.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private String getVariableName(String name) {
        Matcher matcher = DIGITS_PATTERN.matcher(name);
        if (matcher.matches()) {
            String variableName = matcher.group(1);
            String stringNumber = matcher.group(2);
            if (stringNumber == null) {
                stringNumber = "0";
            }
            int number = Integer.parseInt(stringNumber) + 1;
            return variableName + number;
        }
        return name + "1";
    }
}
