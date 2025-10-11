/*
 * Copyright (c) 2025 Aresxue
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
 */
package cn.ares.bean.copy.helper.action;

import cn.ares.bean.copy.helper.BeanCopyHelper;
import cn.ares.bean.copy.helper.BeanCopyHelper.Result;
import cn.ares.bean.copy.helper.util.CommonUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

import static cn.ares.bean.copy.helper.BeanCopyHelper.BEAN_COPY_HELPER;
import static cn.ares.bean.copy.helper.BeanCopyHelper.METHOD_NOT_SUPPORTED_HTML;

/**
 * @author Aresxue
 */
public class GenerateMethodAction implements IntentionAction, PriorityAction {

  @Override
  public @IntentionName @NotNull String getText() {
    return "BeanCopyHelper - Generate method";
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return BEAN_COPY_HELPER;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return BeanCopyHelper.isBeanCopyHelperAvailable(editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // 实现添加注释的具体逻辑
    WriteCommandAction.runWriteCommandAction(project, () -> {
      Document document = editor.getDocument();
      int offset = editor.getCaretModel().getOffset();
      PsiElement elementAtCaret = file.findElementAt(offset);
      PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression.class);

      if (null == methodCallExpression) {
        return;
      }

      Result invoke = BeanCopyHelper.invoke(methodCallExpression);
      if (null == invoke) {
        return;
      }

      PsiClass sourceClass = invoke.sourceClass();
      PsiClass targetClass = invoke.targetClass();
      if (null == sourceClass || null == targetClass) {
        return;
      }

      // 计算要插入注释的位置
      int lineNum = document.getLineNumber(methodCallExpression.getTextRange().getStartOffset());
      int lineStartOffset = document.getLineStartOffset(lineNum);
      int lineEndOffset = document.getLineEndOffset(lineNum);
      // 缩进的位置
      String linePrefix = document.getText(
          new TextRange(lineStartOffset, methodCallExpression.getTextRange().getStartOffset()));

      String commentWithIndent = buildSetterMethod(linePrefix, methodCallExpression, invoke, false);

      document.replaceString(lineStartOffset, lineEndOffset, commentWithIndent);
      PsiDocumentManager.getInstance(project).commitDocument(document);
    });
  }

  private String buildSetterMethod(String linePrefix, PsiMethodCallExpression methodCallExpression, Result invoke, boolean html) {
    PsiClass targetClass = invoke.targetClass();
    String targetClassName = targetClass.getName();
    String referenceName = null == targetClassName ? "target" : CommonUtil.lowerFirst(targetClassName);
    String codeText = String.format("%s%s %s = new %s();\n", linePrefix, targetClassName, referenceName, targetClassName);
    int maxLength = codeText.length();

    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    String sourceArgsName = expressions[0].getText();

    Set<String> commonPropertyNameSet = BeanCopyHelper.findCommonPropertyNameSet(invoke);
    List<String> lineList = new ArrayList<>();
    for (String propertyName : commonPropertyNameSet) {
      // 将属性名的首字母大写
      String upperPropertyName = CommonUtil.upperFirst(propertyName);
      String line = String.format("%s%s.set%s(%s.get%s());", linePrefix, referenceName, upperPropertyName, sourceArgsName, upperPropertyName);
      lineList.add(line);
      if (line.length() > maxLength) {
        maxLength = line.length();
      }
    }

    if (html) {
      double fontSize = BeanCopyHelper.getFontSize(maxLength);
      return buildPropertyHtmlPrefix(codeText, fontSize) +
          lineList.stream()
              .map(line -> buildPropertyHtmlPrefix(line, fontSize))
              .collect(Collectors.joining("\n"));
    } else {
      return codeText + String.join("\n", lineList);
    }
  }


  private String buildPropertyHtmlPrefix(String line, double fontSize) {
    return CommonUtil.format("<p style=\"font-family: Fira Code, monospace; font-size: {}px;\">{}</p>", fontSize, line);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    Result result = BeanCopyHelper.invoke(editor, file);
    if (null == result) {
      return METHOD_NOT_SUPPORTED_HTML;
    }

    int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression.class);
    if (null == methodCallExpression) {
      return IntentionPreviewInfo.EMPTY;
    }

    Result invoke = BeanCopyHelper.invoke(methodCallExpression);
    if (null == invoke) {
      return IntentionPreviewInfo.EMPTY;
    }


    String commentWithIndent = buildSetterMethod("", methodCallExpression, invoke, true);
    return new Html(commentWithIndent);
  }


  @Override
  public @NotNull Priority getPriority() {
    return Priority.LOW;
  }

}
