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
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
    // 先收集一遍
    List<Property> sourceProperties = Stream.of(sourceClass.getAllFields())
        .map(field -> new Property(field.getName(), field.getType())).toList();
    List<Property> targetProperties = Stream.of(targetClass.getAllFields())
        .map(field -> new Property(field.getName(), field.getType())).toList();

    Map<String, Property> sourcePropertyMap = CommonUtil.toIdentityMap(sourceProperties, Property::getName);
    Map<String, Property> targetPropertyMap = CommonUtil.toIdentityMap(targetProperties, Property::getName);

    Map<String, Property> lowerCaseSourcePropertyMap = buildLowerCasePropertyMap(ignoreCase, sourcePropertyMap);
    Map<String, Property> lowerCaseTargetPropertyMap = buildLowerCasePropertyMap(ignoreCase, targetPropertyMap);

    // 再标记一遍
    sourceProperties.forEach(property -> BeanCopyHelper.markProperties(ignoreProperties, targetPropertyMap, lowerCaseTargetPropertyMap, property));
    targetProperties.forEach(property -> BeanCopyHelper.markProperties(ignoreProperties, sourcePropertyMap, lowerCaseSourcePropertyMap, property));

    return new Result(sourceClass, targetClass, sourcePropertyMap, targetPropertyMap, lowerCaseSourcePropertyMap, lowerCaseTargetPropertyMap, ignoreProperties);
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
