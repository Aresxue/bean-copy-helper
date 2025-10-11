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
package cn.ares.bean.copy.helper.constant;

/**
 * @author: Aresxue
 * @time: 2025-07-01 14:44:38
 * @version: JDK 21
 */
public enum Mark {

  SAME("green", " ✅ "),
  TYPE_NOT_MATCH("yellow", " ⚠️ "),
  IGNORED("grey", " 🚫 "),
  DIFF("red", " ❌ "),
  ;
  private final String color;
  private final String icon;

  Mark(String color, String icon) {
    this.color = color;
    this.icon = icon;
  }

  public String getColor() {
    return color;
  }

  public String getIcon() {
    return icon;
  }

}
