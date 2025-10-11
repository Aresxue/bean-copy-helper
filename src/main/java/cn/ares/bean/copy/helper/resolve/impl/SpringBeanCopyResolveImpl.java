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
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import java.util.Optional;
import java.util.Set;

/**
 * @author Aresxue
 */
public class SpringBeanCopyResolveImpl implements BeanCopyResolve {

  private static final String SPRING_BEAN_UTILS_CLASS_NAME = "org.springframework.beans.BeanUtils";

  @Override
  public String qualifiedName() {
    return SPRING_BEAN_UTILS_CLASS_NAME;
  }

  /**
   * @see org.springframework.beans.BeanUtils#copyProperties(java.lang.Object, java.lang.Object)
   * @see org.springframework.beans.BeanUtils#copyProperties(java.lang.Object, java.lang.Object, java.lang.Class<?>)
   */
  @Override
  public boolean isSupport(PsiMethodCallExpression methodCallExpression) {
    // 先做父类的判断不满足直接返回false
    if (!qualifiedName().equals(BeanCopyHelper.resolveQualifiedName(methodCallExpression))) {
      return false;
    }
    PsiType[] expressionTypes = methodCallExpression.getArgumentList().getExpressionTypes();
    //  org.springframework.beans.BeanUtils#copyProperties(Object, Object)
    if (expressionTypes.length == 2) {
      return true;
    }

    // 暂时排除org.springframework.beans.BeanUtils#copyProperties(Object, Object, Class<?>)和org.springframework.beans.BeanUtils#copyProperties(Object, Object, Class<?>, String...)
    if (expressionTypes.length >= 3 && expressionTypes[2] != null && BeanCopyResolve.isAssignableFromClass(expressionTypes[2])) {
      return false;
    }

    return true;
  }

  public static boolean checkMethod(PsiMethodCallExpression expression) {
    String qualifiedName = Optional.ofNullable(expression.resolveMethod())
        .map(PsiMethod::getContainingClass)
        .map(PsiClass::getQualifiedName).orElse("");
    if (SPRING_BEAN_UTILS_CLASS_NAME.equals(qualifiedName)) {
      // 判断第二个参数是Class类型说明业务不了解spring的copyProperties方法使用错误
      PsiType type = expression.getArgumentList().getExpressionTypes()[1];
      if (BeanCopyResolve.isAssignableFromClass(type)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Result resolve(PsiMethodCallExpression methodCallExpression) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    PsiClass sourceClass = PsiUtil.resolveClassInType(expressions[0].getType());
    PsiClass targetClass = PsiUtil.resolveClassInType(expressions[1].getType());

    if (sourceClass == null || targetClass == null) {
      return null;
    }

    // 处理忽略属性
    Set<String> ignoreProperties = BeanCopyHelper.getIgnoreProperties(expressions);
    return buildResult(sourceClass, targetClass, ignoreProperties);
  }

}
