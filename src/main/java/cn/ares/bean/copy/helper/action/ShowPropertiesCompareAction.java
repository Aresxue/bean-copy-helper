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
import cn.ares.bean.copy.helper.model.Property;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import cn.ares.bean.copy.helper.util.CommonUtil;
import cn.ares.bean.copy.helper.util.LocaleSupport;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

import static cn.ares.bean.copy.helper.BeanCopyHelper.BEAN_COPY_HELPER;
import static cn.ares.bean.copy.helper.BeanCopyHelper.METHOD_NOT_SUPPORTED_HTML;
import static cn.ares.bean.copy.helper.constant.Mark.TYPE_NOT_MATCH;

/**
 * @author Aresxue
 */
public class ShowPropertiesCompareAction implements IntentionAction, PriorityAction {

  private static final String DIFF_COMPARE_MESSAGE = LocaleSupport.formatMessage("diff.compare");

  @Override
  public @IntentionName @NotNull String getText() {
    return "BeanCopyHelper - Show properties compare";
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

      // 计算要插入注释的位置
      int lineNum = document.getLineNumber(methodCallExpression.getTextRange().getStartOffset());
      int lineStartOffset = document.getLineStartOffset(lineNum);
      // 缩进的位置
      String linePrefix = document.getText(new TextRange(lineStartOffset, methodCallExpression.getTextRange().getStartOffset()));

      Result result = BeanCopyHelper.invoke(editor, file);
      if (null == result) {
        return;
      }

      PsiClass sourceClass = result.sourceClass();
      PsiClass targetClass = result.targetClass();
      if (sourceClass == null || targetClass == null) {
        return;
      }

      List<Property> sameProperties = new ArrayList<>();
      List<Property> typeNotMatchProperties = new ArrayList<>();
      List<Property> ignoredProperties =  new ArrayList<>();
      List<Property> diffProperties = new ArrayList<>();

      Map<String, Property> sourcePropertyMap = result.sourcePropertyMap();
      Map<String, Property> targetPropertyMap = result.targetPropertyMap();
      Map<String, Property> lowerCaseSourcePropertyMap = result.lowerCaseSourcePropertyMap();
      Map<String, Property> lowerCaseTargetPropertyMap = result.lowerCaseTargetPropertyMap();

      int sourcePropertyMaxLength = -1;
      for (Property sourceProperty : sourcePropertyMap.values()) {
        sourcePropertyMaxLength = getSourcePropertyMaxLength(sourceProperty, targetPropertyMap, lowerCaseTargetPropertyMap, sourcePropertyMaxLength);
        switch (sourceProperty.getMark()) {
          case SAME -> sameProperties.add(sourceProperty);
          case TYPE_NOT_MATCH -> typeNotMatchProperties.add(sourceProperty);
          case IGNORED -> ignoredProperties.add(sourceProperty);
          case DIFF -> diffProperties.add(sourceProperty);
        }
      }
      for (Property targetProperty : targetPropertyMap.values()) {
        switch (targetProperty.getMark()) {
          case IGNORED -> {
            if (ignoredProperties.stream().noneMatch(property -> property.getName().equals(targetProperty.getName()))) {
              ignoredProperties.add(targetProperty);
            }
          }
          case DIFF -> diffProperties.add(targetProperty);
        }
      }

      int paddingLength = sourcePropertyMaxLength;

      String sourceClassName = sourceClass.getName();
      String targetClassName = targetClass.getName();
      // 同属性
      String commonPropertiesComment = sameProperties.stream()
          .map(property -> {
            Property targetProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, property.getName());
            return CommonUtil.rightPad(property.toString(), paddingLength, false) + property.getMark().getIcon() + targetProperty;
          })
          .collect(Collectors.joining("\n"));
      // 同属性不同类型
      String typeNotMatchPropertiesHtml = typeNotMatchProperties.stream()
          .map(property -> {
            Property targetProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, property.getName());
            // 类名相同,类路径不同
            if (property.getShortType().equals(targetProperty.getShortType())) {
              return CommonUtil.rightPad(property.toFullString(), paddingLength, false)
                  + property.getMark().getIcon()
                  + property.toFullString();
            }
            return CommonUtil.rightPad(property.toString(), paddingLength, false)
                + property.getMark().getIcon()
                + targetProperty;
          })
          .collect(Collectors.joining("\n"));
      // 忽略的属性
      String ignoredPropertiesComment = ignoredProperties.stream()
          .map(property -> {
            String propertyName = property.getName();
            StringBuilder htmlBuilder = new StringBuilder();
            Property sourceProperty = BeanCopyResolve.getProperty(sourcePropertyMap, lowerCaseSourcePropertyMap, propertyName);
            if (null == sourceProperty) {
              htmlBuilder.append(" ".repeat(paddingLength));
            } else {
              htmlBuilder.append("~").append(CommonUtil.rightPad(sourceProperty.toString(), paddingLength, false)).append("~");
            }
            htmlBuilder.append(property.getMark().getIcon());
            Property targetProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, propertyName);
            if (null != targetProperty) {
              htmlBuilder.append("~").append(targetProperty).append("~");
            }
            return htmlBuilder.toString();
          })
          .collect(Collectors.joining("\n"));
      // 不同的属性
      String diffPropertiesComment = diffProperties.stream()
          .map(property -> {
            String propertyName = property.getName();
            StringBuilder htmlBuilder = new StringBuilder();
            Property sourceProperty = BeanCopyResolve.getProperty(sourcePropertyMap, lowerCaseSourcePropertyMap, propertyName);
            if (null == sourceProperty) {
              htmlBuilder.append(" ".repeat(paddingLength));
            } else {
              htmlBuilder.append(CommonUtil.rightPad(sourceProperty.toString(), paddingLength, false));
            }
            htmlBuilder.append(property.getMark().getIcon());
            Property targetProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, propertyName);
            if (null != targetProperty) {
              htmlBuilder.append(targetProperty);
            }
            return htmlBuilder.toString();
          })
          .collect(Collectors.joining("\n"));
      String comment = CommonUtil.format("""
                    /*
                    {}
                    {} ➡️ {}
                    {}
                    {}
                    {}
                    {}
                    */
                    """,
          DIFF_COMPARE_MESSAGE,
          sourceClassName,
          targetClassName,
          commonPropertiesComment,
          typeNotMatchPropertiesHtml,
          ignoredPropertiesComment,
          diffPropertiesComment);
      // 将注释与原代码的缩进对齐
      String commentWithIndent = comment.lines()
          .filter(CommonUtil::isNotBlank)
          .map(commentLine -> linePrefix + commentLine)
          .collect(Collectors.joining("\n")) + "\n";
      document.insertString(lineStartOffset, commentWithIndent);
      PsiDocumentManager.getInstance(project).commitDocument(document);
    });
  }


  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    Result result = BeanCopyHelper.invoke(editor, file);
    if (null == result) {
      return METHOD_NOT_SUPPORTED_HTML;
    }

    PsiClass sourceClass = result.sourceClass();
    PsiClass targetClass = result.targetClass();

    if (sourceClass == null || targetClass == null) {
      return IntentionPreviewInfo.EMPTY;
    }

    List<Property> sameProperties = new ArrayList<>();
    List<Property> typeNotMatchProperties = new ArrayList<>();
    List<Property> ignoredProperties =  new ArrayList<>();
    List<Property> diffProperties = new ArrayList<>();

    Map<String, Property> sourcePropertyMap = result.sourcePropertyMap();
    Map<String, Property> targetPropertyMap = result.targetPropertyMap();
    Map<String, Property> lowerCaseSourcePropertyMap = result.lowerCaseSourcePropertyMap();
    Map<String, Property> lowerCaseTargetPropertyMap = result.lowerCaseTargetPropertyMap();

    int sourcePropertyMaxLength = -1;
    for (Property sourceProperty : sourcePropertyMap.values()) {
      sourcePropertyMaxLength = getSourcePropertyMaxLength(sourceProperty, targetPropertyMap, lowerCaseTargetPropertyMap, sourcePropertyMaxLength);
      switch (sourceProperty.getMark()) {
        case SAME -> sameProperties.add(sourceProperty);
        case TYPE_NOT_MATCH -> typeNotMatchProperties.add(sourceProperty);
        case IGNORED -> ignoredProperties.add(sourceProperty);
        case DIFF -> diffProperties.add(sourceProperty);
      }
    }

    int targetPropertyMaxLength = -1;
    for (Property targetProperty : targetPropertyMap.values()) {
      if (targetProperty.toString().length() > targetPropertyMaxLength) {
        targetPropertyMaxLength = targetProperty.toString().length();
      }
      switch (targetProperty.getMark()) {
        case IGNORED -> {
          if (ignoredProperties.stream().noneMatch(property -> property.getName().equals(targetProperty.getName()))) {
            ignoredProperties.add(targetProperty);
          }
        }
        case DIFF -> diffProperties.add(targetProperty);
      }
    }

    int paddingLength = sourcePropertyMaxLength;
    // 3是图标的宽度
    int maxLength = sourcePropertyMaxLength + targetPropertyMaxLength + 3;
    // 获取合适的字体大小
    double fontSize = BeanCopyHelper.getFontSize(maxLength);

    String sourceClassName = sourceClass.getName();
    String targetClassName = targetClass.getName();
    // 同属性
    String samePropertiesHtml = sameProperties.stream()
        .map(property -> buildPropertyHtmlPrefix(property, fontSize) +
            CommonUtil.rightPad(property.toString(), paddingLength, true) +
            property.getMark().getIcon() +
            CommonUtil.quote(BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, property.getName()).toString()) +
            "</p>")
        .collect(Collectors.joining("\n"));
    // 同属性不同类型
    String typeNotMatchPropertiesHtml = typeNotMatchProperties.stream()
        .map(property -> {
          String propertyName = property.getName();
          Property targerProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, propertyName);
          // 类名相同,类路径不同
          if (property.getShortType().equals(targerProperty.getShortType())) {
            return buildPropertyHtmlPrefix(property, fontSize) +
                CommonUtil.quote(CommonUtil.rightPad(property.toFullString(), paddingLength)) +
                property.getMark().getIcon() +
                CommonUtil.quote(property.toFullString()) +
                "</p>";
          }
          return buildPropertyHtmlPrefix(property, fontSize) +
              CommonUtil.rightPad(property.toString(), paddingLength, true) +
              property.getMark().getIcon() +
              CommonUtil.quote(targerProperty.toString()) +
              "</p>";
        })
        .collect(Collectors.joining("\n"));
    // 忽略的属性
    String ignoredPropertiesHtml = ignoredProperties.stream()
        .map(property -> {
          String propertyName = property.getName();
          Property sourceProperty = BeanCopyResolve.getProperty(sourcePropertyMap, lowerCaseSourcePropertyMap, propertyName);
          StringBuilder htmlBuilder = new StringBuilder(buildPropertyHtmlPrefix(property, fontSize));
          if (null == sourceProperty) {
            htmlBuilder.append("&nbsp;".repeat(paddingLength));
          } else {
            htmlBuilder.append("<s>").append(CommonUtil.rightPad(property.toString(), paddingLength, true)).append("</s>");
          }
          htmlBuilder.append(property.getMark().getIcon());
          Property targetProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, propertyName);
          if (null != targetProperty) {
            htmlBuilder.append("<s>").append(CommonUtil.quote(targetProperty.toString())).append("</s>");
          }
          htmlBuilder.append("</p>");
          return htmlBuilder.toString();
        })
        .collect(Collectors.joining("\n"));
    // 不同的属性
    String diffPropertiesHtml = diffProperties.stream()
        .map(property -> {
          String propertyName = property.getName();
          StringBuilder htmlBuilder = new StringBuilder(buildPropertyHtmlPrefix(property, fontSize));
          Property sourceProperty = BeanCopyResolve.getProperty(sourcePropertyMap, lowerCaseSourcePropertyMap, propertyName);
          if (null == sourceProperty) {
            htmlBuilder.append("&nbsp;".repeat(paddingLength));
          } else {
            htmlBuilder.append(CommonUtil.rightPad(property.toString(), paddingLength, true));
          }
          htmlBuilder.append(property.getMark().getIcon());
          Property targetProperty = BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, propertyName);
          if (null != targetProperty) {
            htmlBuilder.append(CommonUtil.quote(targetProperty.toString()));
          }
          htmlBuilder.append("</p>");
          return htmlBuilder.toString();
        })
        .collect(Collectors.joining("\n"));
    String title = sourceClassName + " ➡️ " + targetClassName;
    String titleStyle;
    if (title.length() > 40) {
      titleStyle = CommonUtil.format(" style=\"font-size: {}px;\"", BeanCopyHelper.getFontSize(title.length()));
    } else {
      titleStyle = "";
    }
    String html = CommonUtil.format("""
                <html lang="zh-CN">
                <body>
                <div>
                  <p{}>{}</p>
                  <br>
                  {}
                  {}
                  {}
                  {}
                  <br/>
                </div>
                </body>
                </html>
                """,
            titleStyle,
            title,
            samePropertiesHtml,
            typeNotMatchPropertiesHtml,
            ignoredPropertiesHtml,
            diffPropertiesHtml);
    return new Html(html);
  }

  private int getSourcePropertyMaxLength(Property sourceProperty, Map<String, Property> targetPropertyMap, Map<String, Property> lowerCaseTargetPropertyMap, int sourcePropertyMaxLength) {
    if (TYPE_NOT_MATCH.equals(sourceProperty.getMark())) {
      Property targerProperty = BeanCopyResolve.getProperty(targetPropertyMap,
          lowerCaseTargetPropertyMap, sourceProperty.getName());
      if (sourceProperty.getShortType().equals(targerProperty.getShortType())) {
        if (sourceProperty.toFullString().length() > sourcePropertyMaxLength) {
          sourcePropertyMaxLength = sourceProperty.toFullString().length();
        }
      } else {
        if (sourceProperty.toString().length() > sourcePropertyMaxLength) {
          sourcePropertyMaxLength = sourceProperty.toString().length();
        }
      }
    } else {
      if (sourceProperty.toString().length() > sourcePropertyMaxLength) {
        sourcePropertyMaxLength = sourceProperty.toString().length();
      }
    }
    return sourcePropertyMaxLength;
  }

  private String buildPropertyHtmlPrefix(Property property, double fontSize) {
    return CommonUtil.format("<p style=\"color: {}; font-family: Fira Code, monospace; font-size: {}px;\">",
        property.getMark().getColor(), fontSize);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.NORMAL;
  }

}
