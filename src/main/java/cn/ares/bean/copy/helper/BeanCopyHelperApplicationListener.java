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
import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.diagnostic.Logger;

/**
 * 应用生命周期监听器，用于在应用关闭时清理资源
 *
 * @author Aresxue
 */
public class BeanCopyHelperApplicationListener implements AppLifecycleListener {

  private static final Logger LOGGER = Logger.getInstance(BeanCopyHelperApplicationListener.class);

  @Override
  public void appWillBeClosed(boolean isRestart) {
    try {
      LOGGER.info("BeanCopyHelper plugin shutting down, cleaning resources...");
      CopyPropertiesReferenceIndex.shutdown();
    } catch (Exception exception) {
      LOGGER.warn("Error during BeanCopyHelper plugin shutdown", exception);
    }
  }

}