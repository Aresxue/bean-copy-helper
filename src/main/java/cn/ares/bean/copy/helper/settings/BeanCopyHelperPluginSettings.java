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
package cn.ares.bean.copy.helper.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Aresxue
 * @time: 2025-07-10 18:41:09
 * @version: JDK 21
 */
@State(
    name = "BeanCopyHelperPluginSettings",
    storages = @Storage("BeanCopyHelperPluginSettings.xml")
)
public class BeanCopyHelperPluginSettings implements PersistentStateComponent<BeanCopyHelperPluginSettings> {

  private String foneSizePercentage = "100";

  @Override
  public @Nullable BeanCopyHelperPluginSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull BeanCopyHelperPluginSettings beanCopyHelperPluginSettings) {
    XmlSerializerUtil.copyBean(beanCopyHelperPluginSettings, this);
  }


  public static BeanCopyHelperPluginSettings getInstance() {
    return ApplicationManager.getApplication().getService(BeanCopyHelperPluginSettings.class);
  }

  public void setFoneSizePercentage(String foneSizePercentage) {
    this.foneSizePercentage = foneSizePercentage;
  }

  public String getFoneSizePercentage() {
    return foneSizePercentage;
  }

}
