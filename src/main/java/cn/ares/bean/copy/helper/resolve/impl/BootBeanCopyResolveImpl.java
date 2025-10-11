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
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.Set;
import kotlinx.html.SOURCE;

/**
 * @author: Aresxue
 * @time: 2025-07-07 11:38:43
 * @version: JDK 21
 */
public class BootBeanCopyResolveImpl implements BeanCopyResolve {

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
    String qualifiedName = BeanCopyHelper.resolveQualifiedName(methodCallExpression);
    if (null == qualifiedName || !qualifiedName.endsWith(qualifiedName())) {
      return false;
    }

    PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    PsiExpression[] expressions = argumentList.getExpressions();
    // 暂时排除BeanCopyUtil#copy(SOURCE, Class<TARGET>, BiConsumer<SOURCE,TARGET>)和BeanCopyUtil#copy(SOURCE, Class<TARGET>, Converter)
    if (expressions.length == 3) {
      // BeanCopyUtil#copy(SOURCE, Class<TARGET>, String...)
      if (expressions[2] instanceof PsiLiteralExpression) {
        return true;
      }
      return false;
    }

    // 暂时排除BeanCopyUtil#copy(SOURCE, Class<TARGET>, boolean, Converter)
    if (expressions.length == 4 && expressions[2] != null
        && expressions[2] instanceof PsiLiteralExpression literalExpression
        && "true".equals(literalExpression.getText().replace("\"", ""))) {
      return false;
    }
    return true;
  }

}
