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
package cn.ares.bean.copy.helper.model;

import cn.ares.bean.copy.helper.constant.Mark;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;

/**
 * @author: Aresxue
 * @time: 2025-07-04 14:59:44
 * @version: JDK 21
 */
public class Property {

  private String name;
  private PsiType type;
  private Mark mark;

  public Property(String name, PsiType type) {
    this.name = name;
    this.type = type;
  }

  public Property(String name, PsiType type, Mark mark) {
    this.name = name;
    this.type = type;
    this.mark = mark;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public PsiType getType() {
    return type;
  }

  public void setType(PsiType type) {
    this.type = type;
  }

  public Mark getMark() {
    return mark;
  }

  public void setMark(Mark mark) {
    this.mark = mark;
  }

  @Override
  public String toString() {
    return this.getShortType() + " " + name;
  }

  public String toFullString() {
    return type.getCanonicalText() + " " + name;
  }

  public String getShortType() {
    // 简化类型名称，去掉包名，支持泛型
    return type.getCanonicalText().replaceAll("\\b([a-zA-Z0-9_]+\\.)+", "");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Property property) {
      return name.equals(property.name) && typeEquals(type, property.type);
    }
    return false;
  }

  public boolean equalsIgnoreCase(Object obj) {
    if (obj instanceof Property property) {
      return name.equalsIgnoreCase(property.name)
          && (typeEquals(type, property.type)
          || type.getCanonicalText().equalsIgnoreCase(property.type.getCanonicalText()));
    }
    return false;
  }

  /**
   * 属性复制工具都会自动装拆箱，int和Integer视为同类型否则会误报类型不匹配
   */
  private static boolean typeEquals(PsiType left, PsiType right) {
    if (left.getCanonicalText().equals(right.getCanonicalText())) {
      return true;
    }
    return boxedEquals(left, right) || boxedEquals(right, left);
  }

  private static boolean boxedEquals(PsiType primitiveCandidate, PsiType boxedCandidate) {
    return primitiveCandidate instanceof PsiPrimitiveType primitiveType
        && boxedCandidate.getCanonicalText().equals(primitiveType.getBoxedTypeName());
  }

}
