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
package cn.ares.bean.copy.helper;

import cn.ares.bean.copy.helper.model.CopyPropertiesReferenceIndex;
import cn.ares.bean.copy.helper.resolve.impl.BootBeanCopyResolveImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * @author: Aresxue
 * @time: 2026-09-02 10:30:00
 * @version: JDK 21
 */
public class BeanCopyHelperTest extends LightJavaCodeInsightFixtureTestCase {

  /**
   * 默认的mock JDK缺少完整的java.lang，装箱类型和继承关系都解析不出来，属性判定依赖这些语义
   */
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.springframework.beans;"
        + "public class BeanUtils {"
        + " public static void copyProperties(Object source, Object target) {}"
        + " public static void copyProperties(Object source, Object target, String[] ignoreProperties) {} }");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // 索引是静态的，用例之间必须隔离
      CopyPropertiesReferenceIndex.clear();
    } finally {
      super.tearDown();
    }
  }

  /**
   * 属性类型与Setter入参类型不兼容的重载不应被关联，这是右键关联到不相关方法的主因之一
   */
  public void testMatchesPropertyCopyWithOverloadedSetter() {
    PsiFile file = configureCopyCase(
        "class Source { private Integer age; public Integer getAge() { return age; } }",
        "class Target { private Integer age;"
            + " public void setAge(Integer age) {}"
            + " public void setAge(String age) {} }");
    PsiMethodCallExpression call = findBeanCopyCall(file);

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setAge", "Integer"), call));
    assertFalse(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setAge", "String"), call));
  }

  /**
   * 手写的宽化Setter仍然承接了该属性的复制，不能因为类型不完全相等就漏掉
   * 用项目内的类型做父子关系，测试用的mock JDK没有java.lang的继承关系可供判定
   */
  public void testMatchesPropertyCopyWithWidenedSetter() {
    myFixture.addClass("public class Animal {}");
    myFixture.addClass("public class Dog extends Animal {}");
    PsiFile file = configureCopyCase(
        "class Source { private Dog pet; public Dog getPet() { return pet; } }",
        "class Target { private Dog pet; public void setPet(Animal pet) {} }");

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setPet", "Animal"),
        findBeanCopyCall(file)));
  }

  /**
   * 父类声明子类未覆写的访问器同样承接了复制，原实现只取本类方法会漏掉
   */
  public void testMatchesPropertyCopyWithInheritedAccessor() {
    myFixture.addClass("public class BaseTarget { private Long id; public void setId(Long id) {} }");
    PsiFile file = configureCopyCase(
        "class Source { private Long id; public Long getId() { return id; } }",
        "class Target extends BaseTarget {}");

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("BaseTarget", "setId", "Long"),
        findBeanCopyCall(file)));
  }

  /**
   * 与本次复制无关的类上的同名Setter不应被关联，这是索引指针漂移后的最后一道防线
   */
  public void testMatchesPropertyCopyWithUnrelatedOwner() {
    myFixture.addClass("public class Unrelated { private Integer age; public void setAge(Integer age) {} }");
    PsiFile file = configureCopyCase(
        "class Source { private Integer age; public Integer getAge() { return age; } }",
        "class Target { private Integer age; public void setAge(Integer age) {} }");

    assertFalse(BeanCopyHelper.matchesPropertyCopy(findMethod("Unrelated", "setAge", "Integer"),
        findBeanCopyCall(file)));
  }

  /**
   * 源类的Getter是复制的引用方，目标类的Getter不是
   */
  public void testMatchesPropertyCopyWithGetter() {
    PsiFile file = configureCopyCase(
        "class Source { private Integer age; public Integer getAge() { return age; } }",
        "class Target { private Integer age;"
            + " public Integer getAge() { return age; }"
            + " public void setAge(Integer age) {} }");
    PsiMethodCallExpression call = findBeanCopyCall(file);

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Source", "getAge", null), call));
    assertFalse(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "getAge", null), call));
  }

  /**
   * 属性名相同但类型不一致时不会被复制，其访问器也不应被关联
   */
  public void testMatchesPropertyCopyWithTypeNotMatchProperty() {
    PsiFile file = configureCopyCase(
        "class Source { private Integer age; public Integer getAge() { return age; } }",
        "class Target { private String age; public void setAge(String age) {} }");

    assertFalse(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setAge", "String"),
        findBeanCopyCall(file)));
  }

  /**
   * 属性复制工具会自动装拆箱，int和Integer之间的复制会成功，原实现按类型名严格相等判为不匹配
   */
  public void testMatchesPropertyCopyWithBoxedType() {
    PsiFile file = configureCopyCase(
        "class Source { private int age; public int getAge() { return age; } }",
        "class Target { private Integer age; public void setAge(Integer age) {} }");
    PsiMethodCallExpression call = findBeanCopyCall(file);

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setAge", "Integer"), call));
    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Source", "getAge", null), call));
  }

  /**
   * boolean属性的Getter是is前缀，原实现只拼get导致这类属性完全索引不到
   */
  public void testMatchesPropertyCopyWithBooleanIsGetter() {
    PsiFile file = configureCopyCase(
        "class Source { private boolean active; public boolean isActive() { return active; } }",
        "class Target { private boolean active; public void setActive(boolean active) {} }");
    PsiMethodCallExpression call = findBeanCopyCall(file);

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Source", "isActive", null), call));
    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setActive", "boolean"), call));
  }

  /**
   * 子类遮蔽父类同名字段时应取最派生的声明，取到父类的会让类型判定用错类型
   */
  public void testMatchesPropertyCopyWithShadowedField() {
    myFixture.addClass("public class Animal {}");
    myFixture.addClass("public class Dog extends Animal {}");
    myFixture.addClass("public class BaseSource { protected Animal pet; }");
    PsiFile file = configureCopyCase(
        "class Source extends BaseSource { private Dog pet; public Dog getPet() { return pet; } }",
        "class Target { private Dog pet; public void setPet(Dog pet) {} }");

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setPet", "Dog"),
        findBeanCopyCall(file)));
  }

  /**
   * 继承自泛型父类的字段要还原成实际类型，拿到类型变量T会与任何类型都判不匹配
   */
  public void testMatchesPropertyCopyWithGenericSuperClass() {
    myFixture.addClass("public class BaseDto<T> { private T value; public T getValue() { return value; } }");
    myFixture.addClass("public class Holder {}");
    PsiFile file = configureCopyCase(
        "class Source extends BaseDto<Holder> {}",
        "class Target { private Holder value; public void setValue(Holder value) {} }");

    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setValue", "Holder"),
        findBeanCopyCall(file)));
  }

  /**
   * 忽略属性写成常量或数组时同样要生效，否则被忽略的属性会被误判为已复制
   */
  public void testMatchesPropertyCopyWithIgnorePropertiesConstant() {
    myFixture.addClass("public class Const { public static final String[] IGNORE = new String[]{\"secret\"}; }");
    myFixture.addClass("public class Source { private String secret; private String name;"
        + " public String getSecret() { return secret; } public String getName() { return name; } }");
    myFixture.addClass("public class Target { private String secret; private String name;"
        + " public void setSecret(String secret) {} public void setName(String name) {} }");
    PsiFile file = myFixture.configureByText("Demo.java",
        "class Demo { void run() {"
            + " org.springframework.beans.BeanUtils.copyProperties(new Source(), new Target(), Const.IGNORE);"
            + " } }");
    PsiMethodCallExpression call = findBeanCopyCall(file);

    assertFalse(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setSecret", "String"), call));
    assertTrue(BeanCopyHelper.matchesPropertyCopy(findMethod("Target", "setName", "String"), call));
  }

  /**
   * findCommonPropertyNameSet不能就地剪掉入参，否则调用方后续基于同一Map的类型告警会丢失
   */
  public void testFindCommonPropertyNameSet() {
    Set<String> sourceFieldSet = new HashSet<>(Set.of("id", "name", "secret"));
    Set<String> targetFieldSet = new HashSet<>(Set.of("id", "name"));

    Set<String> commonPropertyNameSet = BeanCopyHelper.findCommonPropertyNameSet(sourceFieldSet, targetFieldSet, Set.of("name"));

    assertEquals(Set.of("id"), commonPropertyNameSet);
    assertEquals(3, sourceFieldSet.size());
    assertEquals(2, targetFieldSet.size());
  }

  /**
   * 同名但不同包的工具类以及以BeanCopyUtil结尾的异构类都不应被识别为属性复制
   */
  public void testIsSupportWithForeignBeanCopyUtil() {
    myFixture.addClass("package com.foo;"
        + "public class MyBeanCopyUtil { public static <T> T copy(Object source, Class<T> targetType) { return null; } }");
    myFixture.addClass("package com.bar;"
        + "public class BeanCopyUtil { public <T> T copy(Object source, Class<T> targetType) { return null; } }");
    myFixture.addClass("package com.baz;"
        + "public class BeanCopyUtil { public static void doSomethingElse(Object source, Object target) {} }");
    myFixture.addClass("public class Target {}");

    PsiFile file = myFixture.configureByText("Demo.java",
        "class Demo { void run(Object source) {"
            + " com.foo.MyBeanCopyUtil.copy(source, Target.class);"
            + " new com.bar.BeanCopyUtil().copy(source, Target.class);"
            + " com.baz.BeanCopyUtil.doSomethingElse(source, new Target());"
            + " } }");

    BootBeanCopyResolveImpl resolve = new BootBeanCopyResolveImpl();
    Collection<PsiMethodCallExpression> callColl = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression.class);
    assertFalse(callColl.isEmpty());
    callColl.forEach(call -> assertFalse(call.getText(), resolve.isSupport(call)));
  }

  /**
   * 构造一次Spring属性复制的调用现场，返回承载调用的文件
   */
  private PsiFile configureCopyCase(String sourceClassText, String targetClassText) {
    myFixture.addClass(sourceClassText.replace("class Source", "public class Source"));
    myFixture.addClass(targetClassText.replace("class Target", "public class Target"));
    return myFixture.configureByText("Demo.java",
        "class Demo { void run() {"
            + " org.springframework.beans.BeanUtils.copyProperties(new Source(), new Target());"
            + " } }");
  }

  private PsiMethodCallExpression findBeanCopyCall(PsiFile file) {
    PsiMethodCallExpression call = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression.class).stream()
        .filter(expression -> BeanCopyHelper.isBeanCopyMethod(expression.getMethodExpression().getCanonicalText()))
        .findFirst().orElse(null);
    assertNotNull("bean copy call not found", call);
    return call;
  }

  /**
   * parameterType为null时取无参重载，否则按首个参数的类型名区分重载
   * 用presentableText而非canonicalText，避免依赖测试用mock JDK能否解析出全限定名
   */
  private PsiMethod findMethod(String className, String methodName, String parameterType) {
    PsiClass psiClass = myFixture.findClass(className);
    assertNotNull(className + " not found", psiClass);
    for (PsiMethod method : psiClass.findMethodsByName(methodName, false)) {
      if (null == parameterType) {
        if (method.getParameterList().isEmpty()) {
          return method;
        }
        continue;
      }
      if (method.getParameterList().getParametersCount() == 1
          && parameterType.equals(method.getParameterList().getParameters()[0].getType().getPresentableText())) {
        return method;
      }
    }
    throw new AssertionError(className + "#" + methodName + " not found");
  }

}
