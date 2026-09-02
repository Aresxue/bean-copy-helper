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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;

/**
 * @author Aresxue
 */
public class HutoolBeanCopyResolveImpl implements BeanCopyResolve {

  public static final String HUTOOL_BEAN_UTIL_CLASS_NAME = "cn.hutool.core.bean.BeanUtil";

  private static final String COPY_PROPERTIES_METHOD_NAME = "copyProperties";
  private static final String TO_BEAN_METHOD_NAME = "toBean";
  private static final String TO_BEAN_IGNORE_ERROR_METHOD_NAME = "toBeanIgnoreError";
  private static final String TO_BEAN_IGNORE_CASE_METHOD_NAME = "toBeanIgnoreCase";
  private static final String COPY_TO_LIST_METHOD_NAME = "copyToList";

  /**
   * copyProperties以外支持的方法名，供BeanCopyHelper注册方法名预筛集合
   */
  public static final List<String> HUTOOL_EXTRA_METHOD_NAME_LIST = List.of(TO_BEAN_METHOD_NAME,
      TO_BEAN_IGNORE_ERROR_METHOD_NAME, TO_BEAN_IGNORE_CASE_METHOD_NAME, COPY_TO_LIST_METHOD_NAME);

  @Override
  public String qualifiedName() {
    return HUTOOL_BEAN_UTIL_CLASS_NAME;
  }

  /**
   * @see cn.hutool.core.bean.BeanUtil#copyProperties(Object source, Object target, String... ignoreProperties)
   * @see cn.hutool.core.bean.BeanUtil#copyProperties(Object source, Class<T> tClass, String... ignoreProperties)
   * @see cn.hutool.core.bean.BeanUtil#copyProperties(Object source, Object target, boolean ignoreCase)
   * @see cn.hutool.core.bean.BeanUtil#copyProperties(Object source, Object target, CopyOptions copyOptions)
   * @see cn.hutool.core.bean.BeanUtil#toBean(Object source, Class<T> clazz)
   * @see cn.hutool.core.bean.BeanUtil#toBean(Object source, Class<T> clazz, CopyOptions options)
   * @see cn.hutool.core.bean.BeanUtil#toBeanIgnoreError(Object source, Class<T> clazz)
   * @see cn.hutool.core.bean.BeanUtil#toBeanIgnoreCase(Object source, Class<T> clazz, boolean ignoreError)
   * @see cn.hutool.core.bean.BeanUtil#copyToList(Collection collection, Class<T> targetType)
   * @see cn.hutool.core.bean.BeanUtil#copyToList(Collection collection, Class<T> targetType, CopyOptions copyOptions)
   */
  @Override
  public Result resolve(PsiMethodCallExpression methodCallExpression) {
    String methodName = methodCallExpression.getMethodExpression().getReferenceName();
    if (null == methodName) {
      return null;
    }
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    // 编辑中的代码可能参数还没写全，避免取参数时越界
    if (expressions.length < 2) {
      return null;
    }

    PsiClass targetClass = resolveTargetClass(expressions[1]);
    if (null == targetClass || isMapClass(targetClass)) {
      return null;
    }

    // copyToList的源是集合，取集合的元素类型作为源类型
    boolean sourceCollection = COPY_TO_LIST_METHOD_NAME.equals(methodName);
    PsiClass sourceClass = sourceCollection ? resolveCollectionElementClass(expressions[0])
        : PsiUtil.resolveClassInType(expressions[0].getType());
    if (null == sourceClass || isMapClass(sourceClass)) {
      return null;
    }

    if (COPY_PROPERTIES_METHOD_NAME.equals(methodName)) {
      return resolveCopyProperties(expressions, sourceClass, targetClass);
    }
    if (sourceCollection) {
      return buildResult(sourceClass, targetClass, Set.of(), false, true);
    }

    // toBeanIgnoreCase固定开启忽略大小写，其布尔参数控制的是ignoreError而非ignoreCase
    boolean ignoreCase = TO_BEAN_IGNORE_CASE_METHOD_NAME.equals(methodName);
    // toBean系列没有可静态解析的忽略属性
    return buildResult(sourceClass, targetClass, Set.of(), ignoreCase);
  }

  private Result resolveCopyProperties(PsiExpression[] expressions, PsiClass sourceClass, PsiClass targetClass) {
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

  /**
   * 取集合的元素类型，裸类型拿不到元素类型时返回null按不支持处理
   */
  private PsiClass resolveCollectionElementClass(PsiExpression sourceExpression) {
    PsiType elementType = PsiUtil.substituteTypeParameter(sourceExpression.getType(), JAVA_UTIL_COLLECTION, 0, false);
    return PsiUtil.resolveClassInType(elementType);
  }

  private PsiClass resolveTargetClass(PsiExpression expression) {
    // 处理Class<T> tClass 参数
    if (expression instanceof PsiClassObjectAccessExpression) {
      return PsiTypesUtil.getPsiClass(((PsiTypeElement) expression.getFirstChild()).getType());
    }
    return PsiUtil.resolveClassInType(expression.getType());
  }

  /**
   * Map的字段是table、size这些内部实现，拿它比对属性只会得到一堆无意义的差异
   */
  private boolean isMapClass(PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_MAP);
  }

  @Override
  public boolean isSupport(PsiMethodCallExpression methodCallExpression) {
    // 先做父类的判断不满足直接返回false
    if (!qualifiedName().equals(BeanCopyHelper.resolveQualifiedName(methodCallExpression))) {
      return false;
    }
    String methodName = methodCallExpression.getMethodExpression().getReferenceName();
    if (null == methodName) {
      return false;
    }

    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    return switch (methodName) {
      // copyProperties的重载都是源在前目标在后，无需再按参数形态区分
      case COPY_PROPERTIES_METHOD_NAME -> true;
      case TO_BEAN_METHOD_NAME -> isSupportToBean(expressions);
      case TO_BEAN_IGNORE_ERROR_METHOD_NAME -> expressions.length == 2;
      case TO_BEAN_IGNORE_CASE_METHOD_NAME -> expressions.length == 3;
      case COPY_TO_LIST_METHOD_NAME -> expressions.length == 2 || expressions.length == 3;
      // BeanUtil里还有大量非属性复制的方法
      default -> false;
    };
  }

  private boolean isSupportToBean(PsiExpression[] expressions) {
    // cn.hutool.core.bean.BeanUtil#toBean(Object, Class<T>)
    if (expressions.length == 2) {
      return true;
    }
    if (expressions.length != 3) {
      return false;
    }

    // 排除toBean(Class<T>, ValueProvider<String>, CopyOptions)，其首参是Class而非源对象
    if (isClassExpression(expressions[0])) {
      return false;
    }
    // 排除toBean(Object, Supplier<T>, CopyOptions)，Supplier推断不出可靠的目标类型
    return isClassExpression(expressions[1]);
  }

  private boolean isClassExpression(PsiExpression expression) {
    return expression instanceof PsiClassObjectAccessExpression
        || BeanCopyResolve.isAssignableFromClass(expression.getType());
  }

}
