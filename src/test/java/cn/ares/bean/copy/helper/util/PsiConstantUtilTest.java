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
package cn.ares.bean.copy.helper.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author: Aresxue
 * @time: 2026-09-03 10:30:00
 * @version: JDK 21
 */
public class PsiConstantUtilTest extends LightJavaCodeInsightFixtureTestCase {

  /**
   * 默认的mock JDK缺少完整的java.lang，注解与字段类型都解析不出来
   */
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // 工程没有依赖lombok，按注解全限名匹配的判定只需要一个同名注解桩
    myFixture.addClass("package lombok.experimental;"
        + "public @interface FieldNameConstants { String innerTypeName() default \"\"; }");
  }

  /**
   * lombok生成的常量是没有初始值的light element，求值拿不到，只能按注解的结构还原出属性名
   */
  public void testEvaluateFieldNameConstant() {
    addLombokSource("@lombok.experimental.FieldNameConstants", "Fields");

    assertEquals("secret", PsiConstantUtil.evaluateFieldNameConstant(findConstant("Fields", "secret")));
  }

  /**
   * innerTypeName显式指定时按指定的内部类名判定
   */
  public void testEvaluateFieldNameConstantWithCustomInnerTypeName() {
    addLombokSource("@lombok.experimental.FieldNameConstants(innerTypeName = \"Names\")", "Names");

    assertEquals("secret", PsiConstantUtil.evaluateFieldNameConstant(findConstant("Names", "secret")));
  }

  /**
   * 外层类没有@FieldNameConstants时内部类常量只是普通常量，不能当成属性名
   */
  public void testEvaluateFieldNameConstantWithoutAnnotation() {
    addLombokSource("", "Fields");

    assertNull(PsiConstantUtil.evaluateFieldNameConstant(findConstant("Fields", "secret")));
  }

  /**
   * 内部类名与innerTypeName不一致说明它不是lombok生成的，避免把无关常量类的常量当成属性名
   */
  public void testEvaluateFieldNameConstantWithMismatchedInnerTypeName() {
    addLombokSource("@lombok.experimental.FieldNameConstants", "Columns");

    assertNull(PsiConstantUtil.evaluateFieldNameConstant(findConstant("Columns", "secret")));
  }

  /**
   * 外层类没有同名字段的常量不是属性名，lombok生成的内部类里也可能有其他常量
   */
  public void testEvaluateFieldNameConstantWithoutMatchedField() {
    addLombokSource("@lombok.experimental.FieldNameConstants", "Fields");

    assertNull(PsiConstantUtil.evaluateFieldNameConstant(findConstant("Fields", "unknown")));
  }

  /**
   * 内部类的常量不写初始值，模拟lombok生成的light element求值失败的现场
   */
  private void addLombokSource(String annotationText, String innerTypeName) {
    myFixture.addClass(annotationText + " public class LombokSource { private String secret;"
        + " public String getSecret() { return secret; }"
        + " public static final class " + innerTypeName + " {"
        + " public static final String secret; public static final String unknown; } }");
  }

  private PsiField findConstant(String innerTypeName, String fieldName) {
    PsiClass innerClass = myFixture.findClass("LombokSource").findInnerClassByName(innerTypeName, false);
    assertNotNull(innerTypeName + " not found", innerClass);
    PsiField field = innerClass.findFieldByName(fieldName, false);
    assertNotNull(fieldName + " not found", field);
    return field;
  }

}
