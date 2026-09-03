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
import cn.ares.bean.copy.helper.model.IgnoreProperties;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import cn.ares.bean.copy.helper.util.PsiConstantUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.HashSet;
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

  private static final String COPY_OPTIONS_CLASS_NAME = "cn.hutool.core.bean.copier.CopyOptions";
  private static final String CREATE_METHOD_NAME = "create";
  private static final String SET_IGNORE_PROPERTIES_METHOD_NAME = "setIgnoreProperties";
  private static final String SET_IGNORE_CASE_METHOD_NAME = "setIgnoreCase";
  private static final String IGNORE_CASE_METHOD_NAME = "ignoreCase";

  /**
   * 链式配置层数有限，超过这个深度说明变量之间存在循环引用
   */
  private static final int COPY_OPTIONS_RESOLVE_MAX_DEPTH = 16;

  /**
   * 不影响属性名匹配也不产生忽略属性的链式方法，链上出现其他方法说明属性可能被改名或过滤，忽略属性集不再可信
   */
  private static final Set<String> HARMLESS_COPY_OPTIONS_METHOD_SET = Set.of("setIgnoreNullValue",
      "ignoreNullValue", "setIgnoreError", "ignoreError", "setTransientSupport", "setOverride",
      "setConverter", "setFieldValueEditor", "setAutoTransformCollection");

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
      return resolveCopyProperties(methodCallExpression, sourceClass, targetClass);
    }

    // toBeanIgnoreCase固定开启忽略大小写，其布尔参数控制的是ignoreError而非ignoreCase
    boolean ignoreCase = TO_BEAN_IGNORE_CASE_METHOD_NAME.equals(methodName);
    // toBean和copyToList的忽略属性只能来自CopyOptions
    CopyOptionsContext copyOptionsContext = resolveCopyOptions(expressions, sourceClass, targetClass);
    if (null == copyOptionsContext) {
      return buildResult(sourceClass, targetClass, IgnoreProperties.RESOLVED_EMPTY, ignoreCase, sourceCollection);
    }
    return buildResult(sourceClass, targetClass, copyOptionsContext.toIgnoreProperties(),
        ignoreCase || copyOptionsContext.ignoreCase, sourceCollection);
  }

  private Result resolveCopyProperties(PsiMethodCallExpression methodCallExpression, PsiClass sourceClass, PsiClass targetClass) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    CopyOptionsContext copyOptionsContext = resolveCopyOptions(expressions, sourceClass, targetClass);
    if (null != copyOptionsContext) {
      return buildResult(sourceClass, targetClass, copyOptionsContext.toIgnoreProperties(), copyOptionsContext.ignoreCase);
    }

    // 处理忽略属性
    IgnoreProperties ignoreProperties = BeanCopyHelper.getIgnoreProperties(methodCallExpression, sourceClass, targetClass);
    // 第三个参数是ignoreCase的重载，求不出值时按不忽略大小写处理
    boolean ignoreCase = expressions.length >= 3 && Boolean.TRUE.equals(PsiConstantUtil.evaluateBoolean(expressions[2]));
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

  /**
   * CopyOptions里的忽略属性沿链式调用逐层配置，不解析会把已被忽略的属性当成已复制
   * 返回null表示这个重载没有CopyOptions参数
   */
  private CopyOptionsContext resolveCopyOptions(PsiExpression[] expressions, PsiClass sourceClass, PsiClass targetClass) {
    if (expressions.length < 3 || !InheritanceUtil.isInheritor(expressions[2].getType(), COPY_OPTIONS_CLASS_NAME)) {
      return null;
    }
    CopyOptionsContext context = new CopyOptionsContext();
    collectCopyOptions(expressions[2], context, sourceClass, targetClass, 0);
    return context;
  }

  private void collectCopyOptions(PsiExpression expression, CopyOptionsContext context,
      PsiClass sourceClass, PsiClass targetClass, int depth) {
    if (null == expression || depth > COPY_OPTIONS_RESOLVE_MAX_DEPTH) {
      context.resolved = false;
      return;
    }
    if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      collectCopyOptionsByMethodCall(methodCallExpression, context, sourceClass, targetClass, depth);
      return;
    }
    if (expression instanceof PsiNewExpression newExpression) {
      // new CopyOptions(Class, boolean, String...)的参数和CopyOptions#create一致
      collectCopyOptionsArguments(newExpression.getArgumentList(), context, sourceClass, targetClass);
      return;
    }
    if (expression instanceof PsiReferenceExpression referenceExpression) {
      collectCopyOptionsByReference(referenceExpression, context, sourceClass, targetClass, depth);
      return;
    }
    // 三元、方法形参传入等形态确定不了实际配置
    context.resolved = false;
  }

  private void collectCopyOptionsByMethodCall(PsiMethodCallExpression methodCallExpression, CopyOptionsContext context,
      PsiClass sourceClass, PsiClass targetClass, int depth) {
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    String methodName = methodExpression.getReferenceName();
    if (null == methodName) {
      context.resolved = false;
      return;
    }
    collectCopyOptionsByMethodName(methodName, methodCallExpression.getArgumentList(), context, sourceClass, targetClass);
    // 链式配置的上一层是当前调用的调用者，继续往内层解析
    collectCopyOptions(methodExpression.getQualifierExpression(), context, sourceClass, targetClass, depth + 1);
  }

  private void collectCopyOptionsByMethodName(String methodName, PsiExpressionList argumentList,
      CopyOptionsContext context, PsiClass sourceClass, PsiClass targetClass) {
    if (SET_IGNORE_PROPERTIES_METHOD_NAME.equals(methodName)) {
      collectIgnoreProperties(argumentList.getExpressions(), 0, context, sourceClass, targetClass);
      return;
    }
    if (CREATE_METHOD_NAME.equals(methodName)) {
      collectCopyOptionsArguments(argumentList, context, sourceClass, targetClass);
      return;
    }
    // 无参的ignoreCase等价于setIgnoreCase(true)，setIgnoreCase的值求不出时和布尔参数重载一样按不忽略处理
    if (IGNORE_CASE_METHOD_NAME.equals(methodName)) {
      context.ignoreCase = true;
      return;
    }
    if (SET_IGNORE_CASE_METHOD_NAME.equals(methodName)) {
      PsiExpression[] expressions = argumentList.getExpressions();
      context.ignoreCase |= expressions.length >= 1 && Boolean.TRUE.equals(PsiConstantUtil.evaluateBoolean(expressions[0]));
      return;
    }
    // setFieldMapping、setFieldNameEditor这类会改变属性名的匹配关系，属性比对结果不再可信
    if (!HARMLESS_COPY_OPTIONS_METHOD_SET.contains(methodName)) {
      context.resolved = false;
    }
  }

  /**
   * @see cn.hutool.core.bean.copier.CopyOptions#create(Class, boolean, String...)
   */
  private void collectCopyOptionsArguments(PsiExpressionList argumentList, CopyOptionsContext context,
      PsiClass sourceClass, PsiClass targetClass) {
    if (null == argumentList) {
      context.resolved = false;
      return;
    }
    PsiExpression[] expressions = argumentList.getExpressions();
    // CopyOptions#create()没有参数
    if (expressions.length == 0) {
      return;
    }
    if (expressions.length == 1) {
      context.resolved = false;
      return;
    }
    // editable限定了可复制的属性范围，非null时属性可能被它挡掉
    if (!PsiTypes.nullType().equals(expressions[0].getType())) {
      context.resolved = false;
    }
    collectIgnoreProperties(expressions, 2, context, sourceClass, targetClass);
  }

  private void collectIgnoreProperties(PsiExpression[] expressions, int startIndex, CopyOptionsContext context,
      PsiClass sourceClass, PsiClass targetClass) {
    for (int i = startIndex; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      if (expression instanceof PsiMethodReferenceExpression methodReferenceExpression) {
        collectIgnorePropertyByMethodReference(methodReferenceExpression, context, sourceClass, targetClass);
        continue;
      }
      // Lambda等无法静态求值的形态由它标记为未解析
      context.resolved &= BeanCopyHelper.collectIgnoreProperty(expression, context.ignorePropertyNameSet, sourceClass, targetClass);
    }
  }

  /**
   * @see cn.hutool.core.bean.copier.CopyOptions#setIgnoreProperties(cn.hutool.core.lang.func.Func1[])
   */
  private void collectIgnorePropertyByMethodReference(PsiMethodReferenceExpression methodReferenceExpression,
      CopyOptionsContext context, PsiClass sourceClass, PsiClass targetClass) {
    String getterName = methodReferenceExpression.getReferenceName();
    String propertyName = BeanCopyHelper.resolvePropertyNameByGetter(getterName, sourceClass);
    if (null == propertyName) {
      propertyName = BeanCopyHelper.resolvePropertyNameByGetter(getterName, targetClass);
    }
    if (null == propertyName) {
      context.resolved = false;
      return;
    }
    context.ignorePropertyNameSet.add(propertyName);
  }

  private void collectCopyOptionsByReference(PsiReferenceExpression referenceExpression, CopyOptionsContext context,
      PsiClass sourceClass, PsiClass targetClass, int depth) {
    PsiElement element = referenceExpression.resolve();
    // 链的起点是CopyOptions类名，走到这里说明整条链都已解析完
    if (element instanceof PsiClass) {
      return;
    }
    if (element instanceof PsiVariable variable) {
      collectCopyOptions(variable.getInitializer(), context, sourceClass, targetClass, depth + 1);
      return;
    }
    context.resolved = false;
  }

  /**
   * 链式配置沿qualifier逐层收集，resolved为false表示链上有无法静态求出的配置
   */
  private static final class CopyOptionsContext {

    private final Set<String> ignorePropertyNameSet = new HashSet<>();
    private boolean resolved = true;
    private boolean ignoreCase;

    private IgnoreProperties toIgnoreProperties() {
      return new IgnoreProperties(ignorePropertyNameSet, resolved);
    }

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
