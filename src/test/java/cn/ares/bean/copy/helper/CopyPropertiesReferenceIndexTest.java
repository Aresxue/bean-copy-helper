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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * 索引生命周期的回归，覆盖重扫膨胀和拷贝调用被删除后的残留
 *
 * @author: Aresxue
 * @time: 2026-09-02 10:30:00
 * @version: JDK 21
 */
public class CopyPropertiesReferenceIndexTest extends LightJavaCodeInsightFixtureTestCase {

  /**
   * 默认的mock JDK缺少完整的java.lang，装箱类型和继承关系都解析不出来，属性判定依赖这些语义
   */
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  private static final String COPY_FILE_NAME = "Demo.java";

  private static final String COPY_TEXT = "class Demo { void run() {"
      + " org.springframework.beans.BeanUtils.copyProperties(new Source(), new Target());"
      + " } }";

  private static final String NO_COPY_TEXT = "class Demo { void run() { } }";

  private final BeanCopyHelper beanCopyHelper = new BeanCopyHelper();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.springframework.beans;"
        + "public class BeanUtils { public static void copyProperties(Object source, Object target) {} }");
    myFixture.addClass("public class Source { private Integer age; public Integer getAge() { return age; } }");
    myFixture.addClass("public class Target { private Integer age; public void setAge(Integer age) {} }");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CopyPropertiesReferenceIndex.clear();
    } finally {
      super.tearDown();
    }
  }

  public void testAddReference() {
    scan(myFixture.configureByText(COPY_FILE_NAME, COPY_TEXT));

    assertTrue(CopyPropertiesReferenceIndex.hasReference(targetSetter()));
    assertEquals(1, CopyPropertiesReferenceIndex.getReferenceList(targetSetter()).size());
  }

  public void testRemoveByFile() {
    PsiFile file = myFixture.configureByText(COPY_FILE_NAME, COPY_TEXT);
    scan(file);
    assertTrue(CopyPropertiesReferenceIndex.hasReference(targetSetter()));

    CopyPropertiesReferenceIndex.removeByFile(file.getVirtualFile().getUrl());

    assertFalse(CopyPropertiesReferenceIndex.hasReference(targetSetter()));
    assertTrue(CopyPropertiesReferenceIndex.getReferenceList(targetSetter()).isEmpty());
  }

  /**
   * 同一文件重复扫描不应让索引膨胀，原实现只追加不清除
   */
  public void testAddReferenceAfterRescan() {
    PsiFile file = myFixture.configureByText(COPY_FILE_NAME, COPY_TEXT);
    scan(file);
    scan(file);
    scan(file);

    assertEquals(1, CopyPropertiesReferenceIndex.getReferenceList(targetSetter()).size());
  }

  /**
   * 文件内容改为不再包含属性复制后重扫，索引不应残留
   */
  public void testGetReferenceListAfterCopyRemoved() {
    PsiFile file = myFixture.configureByText(COPY_FILE_NAME, COPY_TEXT);
    scan(file);
    assertTrue(CopyPropertiesReferenceIndex.hasReference(targetSetter()));

    rewriteText(file, NO_COPY_TEXT);
    scan(file);

    assertTrue(CopyPropertiesReferenceIndex.getReferenceList(targetSetter()).isEmpty());
    assertFalse(CopyPropertiesReferenceIndex.hasReference(targetSetter()));
  }

  public void testRemoveByDirectory() {
    PsiFile file = myFixture.configureByText(COPY_FILE_NAME, COPY_TEXT);
    scan(file);
    assertTrue(CopyPropertiesReferenceIndex.hasReference(targetSetter()));

    VirtualFile parent = file.getVirtualFile().getParent();
    assertNotNull(parent);
    CopyPropertiesReferenceIndex.removeByDirectory(parent.getUrl());

    assertFalse(CopyPropertiesReferenceIndex.hasReference(targetSetter()));
  }

  private void scan(PsiFile file) {
    beanCopyHelper.copyPropertiesReferenceScan(file.getVirtualFile(), PsiManager.getInstance(getProject()));
  }

  /**
   * 改写文件内容并提交，只写VFS的话PSI仍是旧树，重扫读到的还是改动前的内容
   */
  private void rewriteText(PsiFile file, String text) {
    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.setText(text);
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });
  }

  private PsiMethod targetSetter() {
    PsiClass targetClass = myFixture.findClass("Target");
    assertNotNull(targetClass);
    PsiMethod[] methods = targetClass.findMethodsByName("setAge", false);
    assertEquals(1, methods.length);
    return methods[0];
  }

}
