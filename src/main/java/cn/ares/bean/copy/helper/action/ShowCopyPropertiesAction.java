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
import cn.ares.bean.copy.helper.util.LocaleSupport;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

import static cn.ares.bean.copy.helper.BeanCopyHelper.BEAN_COPY_HELPER;
import static cn.ares.bean.copy.helper.BeanCopyHelper.METHOD_NOT_SUPPORTED_HTML;

/**
 * @author Aresxue
 */
public class ShowCopyPropertiesAction implements IntentionAction, PriorityAction {

  private static final String NO_COMMON_PROPERTIES_FOUND = LocaleSupport.formatMessage("common.properties.not.found");

  @Override
  public @IntentionName @NotNull String getText() {
    return "BeanCopyHelper - Show copy properties";
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

      if (methodCallExpression != null) {
        Result result = BeanCopyHelper.invoke(methodCallExpression);
        if (null == result) {
          return;
        }

        PsiClass sourceClass = result.sourceClass();
        PsiClass targetClass = result.targetClass();
        if (sourceClass == null || targetClass == null) {
          return;
        }

        Set<String> commonPropertyNames = findCommonPropertyNames(sourceClass, targetClass, result);
        if (commonPropertyNames.isEmpty()) {
          HintManager.getInstance().showInformationHint(editor, NO_COMMON_PROPERTIES_FOUND);
          return;
        }

        // 计算要插入注释的位置
        int lineNum = document.getLineNumber(methodCallExpression.getTextRange().getStartOffset());
        int lineStartOffset = document.getLineStartOffset(lineNum);
        // 缩进的位置
        String linePrefix = document.getText(
            new TextRange(lineStartOffset, methodCallExpression.getTextRange().getStartOffset()));
        String commentText;
        // 共有属性超过四个使用块注释显示
        String copyPropertiesFromMessage = LocaleSupport.formatMessage("copy.properties.from", sourceClass.getName());
        String copyPropertiesToMessage = LocaleSupport.formatMessage("copy.properties.to", targetClass.getName());
        commentText = commonPropertyNames.stream()
            .map(propertyName -> linePrefix + "\t\t" + propertyName)
            .collect(Collectors.joining(",\n",
                String.format("""
                      /*
                      %s   %s:
                      """, linePrefix, copyPropertiesFromMessage),
                String.format("""
                      
                      %s   %s
                      %s*/""", linePrefix, copyPropertiesToMessage, linePrefix)));
        // 将注释与原代码的缩进对齐
        String commentWithIndent = linePrefix + commentText + '\n';
        document.insertString(lineStartOffset, commentWithIndent);
        PsiDocumentManager.getInstance(project).commitDocument(document);
      }
    });
  }

  private Set<String> findCommonPropertyNames(PsiClass sourceClass, PsiClass targetClass, Result result) {
    Set<String> sourceClassFieldSet = Arrays.stream(sourceClass.getAllFields())
        .map(NavigationItem::getName)
        .collect(Collectors.toSet());
    Set<String> targetClassFieldSet = Arrays.stream(targetClass.getAllFields())
        .map(NavigationItem::getName)
        .collect(Collectors.toSet());
    return BeanCopyHelper.findCommonPropertyNameSet(sourceClassFieldSet, targetClassFieldSet, result.ignoredProperties());
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

    PsiClass sourceClass = result.sourceClass();
    PsiClass targetClass = result.targetClass();
    String commentText;
    Set<String> commonPropertyNameSet = BeanCopyHelper.findCommonPropertyNameSet(result);
    if (commonPropertyNameSet.isEmpty()) {
      return new Html(NO_COMMON_PROPERTIES_FOUND);
    }

    // 共有属性超过四个使用块注释显示
    String copyPropertiesFromMessage = LocaleSupport.formatMessage("copy.properties.from", sourceClass.getName());
    String copyPropertiesToMessage = LocaleSupport.formatMessage("copy.properties.to", targetClass.getName());
    commentText = commonPropertyNameSet.stream()
        .collect(Collectors.joining(",<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;",
            String.format("""
                  /*
                    %s:<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                  """, copyPropertiesFromMessage),
            String.format("""
                  
                    <br/>%s
                  */""", copyPropertiesToMessage)));
    return new Html(commentText + "<br>&nbsp;</br>");
  }

  @Override
  public @NotNull PriorityAction.Priority getPriority() {
    return Priority.LOW;
  }

}
