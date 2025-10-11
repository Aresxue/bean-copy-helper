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
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.Set;

/**
 * @author Aresxue
 */
public class HutoolBeanCopyResolveImpl implements BeanCopyResolve {

  @Override
  public String qualifiedName() {
    return "cn.hutool.core.bean.BeanUtil";
  }

  /**
   * @see cn.hutool.core.bean.BeanUtil#copyProperties(Object source, Object target, String... ignoreProperties)
   * @see cn.hutool.core.bean.BeanUtil#copyProperties(Object source, Class<T> tClass, String... ignoreProperties)
   */
  @Override
  public Result resolve(PsiMethodCallExpression methodCallExpression) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    PsiClass sourceClass = PsiUtil.resolveClassInType(expressions[0].getType());

    PsiClass targetClass;
    // 处理Class<T> tClass 参数
    if (expressions[1] instanceof PsiClassObjectAccessExpression) {
      targetClass = PsiTypesUtil.getPsiClass(((PsiTypeElement) expressions[1].getFirstChild()).getType());
    } else {
      targetClass = PsiUtil.resolveClassInType(expressions[1].getType());
    }

    if (sourceClass == null || targetClass == null) {
      return null;
    }

    // 处理忽略属性
    Set<String> ignoreProperties = BeanCopyHelper.getIgnoreProperties(expressions);
    boolean ignoreCase;
    if (expressions.length >= 3 && expressions[2] instanceof PsiLiteralExpression literalExpression) {
      ignoreCase = "true".equals(literalExpression.getText().replace("\"", ""));
    } else {
      ignoreCase = false;
    }
    return buildResult(sourceClass, targetClass, ignoreProperties, ignoreCase);
  }

  @Override
  public boolean isSupport(PsiMethodCallExpression methodCallExpression) {
    // 先做父类的判断不满足直接返回false
    if (!qualifiedName().equals(BeanCopyHelper.resolveQualifiedName(methodCallExpression))) {
      return false;
    }
    PsiType[] expressionTypes = methodCallExpression.getArgumentList().getExpressionTypes();

    // 暂时排除cn.hutool.core.bean.BeanUtil#copyProperties(Object, Object, CopyOptions)
    if (expressionTypes.length == 3 && expressionTypes[2] != null
        && expressionTypes[2] instanceof PsiClassReferenceType classReferenceType
        && "CopyOptions".equals(classReferenceType.getClassName())) {
      return false;
    }

    return true;
  }

}
