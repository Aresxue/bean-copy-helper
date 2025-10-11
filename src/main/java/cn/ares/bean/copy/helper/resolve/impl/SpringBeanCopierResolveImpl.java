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
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTypesUtil;
import java.util.Set;

/**
 * @author Aresxue
 */
public class SpringBeanCopierResolveImpl implements BeanCopyResolve {

  public static final String SPRING_BEAN_COPIER_CLASS_NAME = "org.springframework.cglib.beans.BeanCopier";

  @Override
  public String qualifiedName() {
    return SPRING_BEAN_COPIER_CLASS_NAME;
  }

  /**
   * @see org.springframework.cglib.beans.BeanCopier#create
   */
  @Override
  public boolean isSupport(PsiMethodCallExpression methodCallExpression) {
    // 先做父类的判断不满足直接返回false
    if (!qualifiedName().equals(BeanCopyHelper.resolveQualifiedName(methodCallExpression))) {
      return false;
    }
    // 忽略useConverter为true的场景因为不知道它内部的逻辑
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    if (expressions[2] != null && expressions[2] instanceof PsiLiteralExpression literalExpression
        && "true".equals(literalExpression.getText().replace("\"", ""))) {
      return false;
    }

    return true;
  }

  @Override
  public Result resolve(PsiMethodCallExpression methodCallExpression) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    PsiClass sourceClass = PsiTypesUtil.getPsiClass(((PsiTypeElement) expressions[0].getFirstChild()).getType());
    PsiClass targetClass = PsiTypesUtil.getPsiClass(((PsiTypeElement) expressions[1].getFirstChild()).getType());

    if (sourceClass == null || targetClass == null) {
      return null;
    }

    // 处理忽略属性
    Set<String> ignoreProperties = BeanCopyHelper.getIgnoreProperties(expressions);
    return buildResult(sourceClass, targetClass, ignoreProperties);
  }

}
