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

import cn.ares.bean.copy.helper.BeanCopyHelper.Result;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Aresxue
 */
public class ApacheBeanCopyResolveImpl implements BeanCopyResolve {

  private static final Set<String> APACHE_COPY_PROPERTY_METHOD_SET = new HashSet<>();

  static {
    APACHE_COPY_PROPERTY_METHOD_SET.add("BeanUtils.copyProperty");
    APACHE_COPY_PROPERTY_METHOD_SET.add("org.apache.commons.beanutils.BeanUtils.copyProperty");
  }


  public static boolean isBeanCopyPropertyMethod(String canonicalText) {
    return APACHE_COPY_PROPERTY_METHOD_SET.contains(canonicalText);
  }

  @Override
  public String qualifiedName() {
    return "org.apache.commons.beanutils.BeanUtils";
  }

  /**
   * @see org.apache.commons.beanutils.BeanUtils#copyProperties
   */
  @Override
  public Result resolve(PsiMethodCallExpression methodCallExpression) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    // apache的源类型和目标类型是反着来的
    PsiClass sourceClass = PsiUtil.resolveClassInType(expressions[1].getType());
    PsiClass targetClass = PsiUtil.resolveClassInType(expressions[0].getType());

    if (sourceClass == null || targetClass == null) {
      return null;
    }

    // apache没有ignoreProperties参数
    Set<String> ignoreProperties = Set.of();
    return buildResult(sourceClass, targetClass, ignoreProperties);
  }


}
