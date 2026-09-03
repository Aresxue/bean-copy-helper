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
package cn.ares.bean.copy.helper.util;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNewExpression;

/**
 * @author: Aresxue
 * @time: 2026-09-03 10:00:00
 * @version: JDK 21
 */
public class PsiConstantUtil {

  private static final String FIELD_NAME_CONSTANTS_ANNOTATION = "lombok.experimental.FieldNameConstants";
  private static final String INNER_TYPE_NAME_ATTRIBUTE = "innerTypeName";
  /**
   * innerTypeName的默认值是空串，此时lombok生成的内部类名是Fields
   */
  private static final String DEFAULT_INNER_TYPE_NAME = "Fields";

  /**
   * 常量求值，求不出来返回null
   * 平台的求值覆盖字面量、final变量引用（含class文件里的常量）、字符串拼接、括号、三元和强转，
   * 数组常量不是编译期常量必然求不出来，需要调用方展开数组初始化器
   */
  public static Object evaluate(PsiExpression expression) {
    if (null == expression) {
      return null;
    }
    return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
        .computeConstantExpression(expression);
  }

  /**
   * 求值为字符串，求不出来返回null由调用方判定不可解析
   */
  public static String evaluateString(PsiExpression expression) {
    return evaluate(expression) instanceof String value ? value : null;
  }

  /**
   * 返回Boolean而非boolean，null表示求不出来，与求出了false区分开
   */
  public static Boolean evaluateBoolean(PsiExpression expression) {
    return evaluate(expression) instanceof Boolean value ? value : null;
  }

  /**
   * 数组常量不是编译期常量，只能按结构取到初始化器再逐个元素求值
   */
  public static PsiArrayInitializerExpression findArrayInitializer(PsiExpression expression) {
    if (expression instanceof PsiArrayInitializerExpression arrayInitializer) {
      return arrayInitializer;
    }
    if (expression instanceof PsiNewExpression newExpression) {
      return newExpression.getArrayInitializer();
    }
    return null;
  }

  /**
   * lombok的@FieldNameConstants生成的常量是没有初始值的light element，求值拿不到，
   * 但该注解没有prefix和suffix参数，常量名恰是字段名，据此还原出属性名
   */
  public static String evaluateFieldNameConstant(PsiField field) {
    PsiClass innerClass = field.getContainingClass();
    if (null == innerClass) {
      return null;
    }
    PsiClass outerClass = innerClass.getContainingClass();
    if (null == outerClass) {
      return null;
    }
    PsiAnnotation annotation = outerClass.getAnnotation(FIELD_NAME_CONSTANTS_ANNOTATION);
    if (null == annotation) {
      return null;
    }
    if (!resolveInnerTypeName(annotation).equals(innerClass.getName())) {
      return null;
    }

    // 外层类必须真有这个字段，避免把内部类里的无关常量当成属性名
    String propertyName = field.getName();
    return null == outerClass.findFieldByName(propertyName, true) ? null : propertyName;
  }

  private static String resolveInnerTypeName(PsiAnnotation annotation) {
    PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue(INNER_TYPE_NAME_ATTRIBUTE);
    if (attributeValue instanceof PsiExpression expression) {
      String innerTypeName = evaluateString(expression);
      if (null != innerTypeName && !innerTypeName.isEmpty()) {
        return innerTypeName;
      }
    }
    return DEFAULT_INNER_TYPE_NAME;
  }

}
