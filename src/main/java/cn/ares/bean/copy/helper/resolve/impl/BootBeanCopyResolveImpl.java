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
package cn.ares.bean.copy.helper.resolve.impl;

import cn.ares.bean.copy.helper.BeanCopyHelper;
import cn.ares.bean.copy.helper.BeanCopyHelper.Result;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
 * @author: Aresxue
 * @time: 2025-07-07 11:38:43
 * @version: JDK 21
 */
public class BootBeanCopyResolveImpl implements BeanCopyResolve {

  /**
   * 支持的属性复制方法名，同名工具类里可能还有其他非属性复制的方法
   */
  private static final Set<String> SUPPORT_METHOD_NAME_SET = Set.of("copy", "copyPropertiesIgnoreNull");

  @Override
  public String qualifiedName() {
    return "BeanCopyUtil";
  }

  @Override
  public Result resolve(PsiMethodCallExpression methodCallExpression) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    PsiClass sourceClass = PsiUtil.resolveClassInType(expressions[0].getType());
    PsiClass targetClass;
    // 处理Class<T> tClass 参数
    if (expressions[1] instanceof PsiClassObjectAccessExpression) {
      targetClass = PsiTypesUtil.getPsiClass(
          ((PsiTypeElement) expressions[1].getFirstChild()).getType());
    } else {
      targetClass = PsiUtil.resolveClassInType(expressions[1].getType());
    }

    if (sourceClass == null || targetClass == null) {
      return null;
    }

    // 处理忽略属性
    Set<String> ignoreProperties = BeanCopyHelper.getIgnoreProperties(expressions);
    return buildResult(sourceClass, targetClass, ignoreProperties);
  }

  /**
   * @see BeanCopyUtil#copy(SOURCE, TARGET, boolean, Converter)
   * @see BeanCopyUtil#copy(SOURCE, TARGET)
   * @see BeanCopyUtil#copy(SOURCE, T)
   */
  @Override
  public boolean isSupport(PsiMethodCallExpression methodCallExpression) {
    // 先做父类的判断不满足直接返回false
    PsiMethod method = methodCallExpression.resolveMethod();
    if (null == method || !matchesBeanCopyUtil(method)) {
      return false;
    }

    PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    PsiExpression[] expressions = argumentList.getExpressions();
    if (!validArgumentShape(method, expressions)) {
      return false;
    }

    PsiParameter[] parameters = method.getParameterList().getParameters();
    // 暂时排除BeanCopyUtil#copy(SOURCE, Class<TARGET>, BiConsumer<SOURCE,TARGET>)和BeanCopyUtil#copy(SOURCE, Class<TARGET>, Converter)
    // 按形参类型而非实参形态判断，忽略属性常量化或用数组传入时同样是String
    if (parameters.length == 3) {
      return isIgnorePropertiesParameter(parameters[2]);
    }

    // 暂时排除BeanCopyUtil#copy(SOURCE, Class<TARGET>, boolean, Converter)
    if (parameters.length == 4 && expressions.length >= 3
        && expressions[2] instanceof PsiLiteralExpression literalExpression
        && Boolean.TRUE.equals(literalExpression.getValue())) {
      return false;
    }
    return true;
  }

  /**
   * String...和String[]都是忽略属性，BiConsumer和Converter则不是
   */
  private boolean isIgnorePropertiesParameter(PsiParameter parameter) {
    return JAVA_LANG_STRING.equals(parameter.getType().getDeepComponentType().getCanonicalText());
  }

  /**
   * 该工具类不在依赖中只能按类名识别，补上包分隔符并叠加签名校验以缩小误识别范围
   */
  private boolean matchesBeanCopyUtil(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (null == containingClass) {
      return false;
    }
    String qualifiedName = containingClass.getQualifiedName();
    if (null == qualifiedName) {
      return false;
    }
    // 排除MyBeanCopyUtil这类以它结尾但并非同一个类的工具类
    if (!qualifiedName.equals(qualifiedName()) && !qualifiedName.endsWith("." + qualifiedName())) {
      return false;
    }
    // 属性复制工具方法都是静态的，名为copy的实例方法极为常见
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    return SUPPORT_METHOD_NAME_SET.contains(method.getName());
  }

  private boolean validArgumentShape(PsiMethod method, PsiExpression[] expressions) {
    // 源和目标是必须的，实参个数不设上限，String...形式的忽略属性可以传任意多个
    if (expressions.length < 2) {
      return false;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length < 2 || parameters.length > 4) {
      return false;
    }
    // 源和目标都必须是引用类型
    if (parameters[0].getType() instanceof PsiPrimitiveType
        || parameters[1].getType() instanceof PsiPrimitiveType) {
      return false;
    }

    // 目标传Class时复制结果只能由返回值带出，返回void说明不是属性复制
    boolean targetIsClass = expressions[1] instanceof PsiClassObjectAccessExpression
        || BeanCopyResolve.isAssignableFromClass(expressions[1].getType());
    if (targetIsClass && PsiTypes.voidType() == method.getReturnType()) {
      return false;
    }
    return true;
  }

}
