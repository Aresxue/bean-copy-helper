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

import java.util.Set;

/**
 * @author: Aresxue
 * @time: 2026-09-03 10:00:00
 * @version: JDK 21
 * @description: 忽略属性集及其可信度，resolved为false表示有忽略属性的值无法静态求出，
 * 此时集合不完整，被忽略的属性可能仍显示为已复制，据此抑制会误报的告警与引用索引写入
 */
public record IgnoreProperties(Set<String> propertyNameSet, boolean resolved) {

  public static final IgnoreProperties RESOLVED_EMPTY = new IgnoreProperties(Set.of(), true);

  public static IgnoreProperties unresolved() {
    return new IgnoreProperties(Set.of(), false);
  }

}
