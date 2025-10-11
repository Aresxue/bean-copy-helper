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

import com.intellij.openapi.options.Configurable;
import java.awt.FlowLayout;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Aresxue
 * @time: 2025-07-10 19:12:27
 * @version: JDK 21
 */
public class BeanCopyHelperPluginConfigurable implements Configurable {

  private JTextField foneSizePercentageField;

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel mainPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    foneSizePercentageField = new JTextField(5);
    mainPanel.add(new JLabel("字体大小(%): "));
    mainPanel.add(foneSizePercentageField);
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    return !Objects.equals(foneSizePercentageField.getText(), BeanCopyHelperPluginSettings.getInstance().getFoneSizePercentage());
  }

  @Override
  public void apply() {
    BeanCopyHelperPluginSettings.getInstance().setFoneSizePercentage(foneSizePercentageField.getText());
  }

  @Override
  public void reset() {
    foneSizePercentageField.setText(BeanCopyHelperPluginSettings.getInstance().getFoneSizePercentage());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Bean Copy Helper Plugin";
  }

}
