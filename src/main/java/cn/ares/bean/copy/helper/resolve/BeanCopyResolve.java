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
package cn.ares.bean.copy.helper.resolve;

import cn.ares.bean.copy.helper.BeanCopyHelper;
import cn.ares.bean.copy.helper.BeanCopyHelper.Result;
import cn.ares.bean.copy.helper.model.Property;
import cn.ares.bean.copy.helper.util.CommonUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.TypeConversionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;

/**
 * @author Aresxue
 */
public interface BeanCopyResolve {

  /**
   * 是否支持
   */
  default boolean isSupport(PsiMethodCallExpression methodCallExpression) {
    return qualifiedName().equals(BeanCopyHelper.resolveQualifiedName(methodCallExpression));
  }

  /**
   * 全限类名
   */
  String qualifiedName();


  /**
   * 从 PsiMethodCallExpression 解析出结果
   */
  Result resolve(PsiMethodCallExpression methodCallExpression);

  default Result buildResult(PsiClass sourceClass, PsiClass targetClass, Set<String> ignoreProperties) {
    return buildResult(sourceClass, targetClass, ignoreProperties, false);
  }

  default Result buildResult(PsiClass sourceClass, PsiClass targetClass, Set<String> ignoreProperties, boolean ignoreCase) {
    return buildResult(sourceClass, targetClass, ignoreProperties, ignoreCase, false);
  }

  /**
   * sourceCollection标识源是集合，用于生成方法时按批量转换渲染
   */
  default Result buildResult(PsiClass sourceClass, PsiClass targetClass, Set<String> ignoreProperties, boolean ignoreCase, boolean sourceCollection) {
    // 先收集一遍
    List<Property> sourceProperties = collectProperties(sourceClass);
    List<Property> targetProperties = collectProperties(targetClass);

    Map<String, Property> sourcePropertyMap = CommonUtil.toIdentityMap(sourceProperties, Property::getName);
    Map<String, Property> targetPropertyMap = CommonUtil.toIdentityMap(targetProperties, Property::getName);

    Map<String, Property> lowerCaseSourcePropertyMap = buildLowerCasePropertyMap(ignoreCase, sourcePropertyMap);
    Map<String, Property> lowerCaseTargetPropertyMap = buildLowerCasePropertyMap(ignoreCase, targetPropertyMap);

    // 再标记一遍
    sourceProperties.forEach(property -> BeanCopyHelper.markProperties(ignoreProperties, targetPropertyMap, lowerCaseTargetPropertyMap, property));
    targetProperties.forEach(property -> BeanCopyHelper.markProperties(ignoreProperties, sourcePropertyMap, lowerCaseSourcePropertyMap, property));

    return new Result(sourceClass, targetClass, sourcePropertyMap, targetPropertyMap, lowerCaseSourcePropertyMap, lowerCaseTargetPropertyMap, ignoreProperties, sourceCollection);
  }

  /**
   * 收集类及其父类的属性，同名字段按Java的遮蔽规则只取最派生的那个，
   * 继承自泛型父类的字段用父类替换器还原出实际类型，否则拿到的是T这样的类型变量
   */
  private static List<Property> collectProperties(PsiClass psiClass) {
    List<Property> propertyList = new ArrayList<>();
    Set<String> handledNameSet = new HashSet<>();
    for (PsiField declaredField : psiClass.getAllFields()) {
      String propertyName = declaredField.getName();
      if (!handledNameSet.add(propertyName)) {
        continue;
      }
      // 用findFieldByName而非直接取遍历到的字段，子类遮蔽父类同名字段时它返回最派生的声明
      PsiField field = psiClass.findFieldByName(propertyName, true);
      if (null == field) {
        field = declaredField;
      }
      propertyList.add(new Property(propertyName, resolveFieldType(psiClass, field)));
    }
    return propertyList;
  }

  private static PsiType resolveFieldType(PsiClass psiClass, PsiField field) {
    PsiType fieldType = field.getType();
    PsiClass declaringClass = field.getContainingClass();
    if (null == declaringClass) {
      return fieldType;
    }
    // 用可返回null的getClassSubstitutor，getSuperClassSubstitutor在两者无继承关系时会记录错误日志
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(declaringClass, psiClass, PsiSubstitutor.EMPTY);
    return null == substitutor ? fieldType : substitutor.substitute(fieldType);
  }

  private static Map<String, Property> buildLowerCasePropertyMap(boolean ignoreCase, Map<String, Property> propertyMap) {
    Map<String, Property> lowerCasePropertyMap;
    if (ignoreCase) {
      lowerCasePropertyMap = new HashMap<>((int) ((float) propertyMap.size() / 0.75F + 1.0F));
      propertyMap.forEach((key, value) -> lowerCasePropertyMap.put(key.toLowerCase(), value));
    } else {
      lowerCasePropertyMap = Collections.emptyMap();
    }
    return lowerCasePropertyMap;
  }


  static boolean isAssignableFromClass(PsiType type) {
    return type instanceof PsiImmediateClassType immediateClassType
        && immediateClassType.rawType().getCanonicalText().equals(JAVA_LANG_CLASS);
  }

  static Property getProperty(Map<String, Property> propertyMap, Map<String, Property> lowerCasePropertyMap, String propertyName) {
    Property targetProperty = propertyMap.get(propertyName);
    if (null == targetProperty) {
      // 使用小写的属性Map做兜底
      targetProperty = lowerCasePropertyMap.get(propertyName.toLowerCase());
    }
    return targetProperty;
  }

}
