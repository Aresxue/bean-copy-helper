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

import java.util.Locale;
import java.util.ResourceBundle;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import static java.util.Locale.SIMPLIFIED_CHINESE;
import static java.util.Locale.US;

/**
 * @author: Aresxue
 * @time: 2025-07-01 20:56:30
 * @description: 多语言支持
 * @description: Locale support
 * @version: JDK 21
 */
public class LocaleSupport {

  private static final ResourceBundle RESOURCE_BUNDLE;

  static {
    Locale locale = Locale.getDefault();
    // 仅支持zn_CN和en_US
    if (locale != SIMPLIFIED_CHINESE && locale != US) {
      locale = SIMPLIFIED_CHINESE;
    }
    RESOURCE_BUNDLE = ResourceBundle.getBundle("i18n/message", locale);
  }

  public static String formatMessage(String messageKey, Object... args) {
    String messageFormat = RESOURCE_BUNDLE.getString(messageKey);
    if (messageFormat.equals(messageKey)) {
      return messageKey;
    }
    FormattingTuple formattingTuple = MessageFormatter.arrayFormat(messageFormat, args);

    if (formattingTuple.getThrowable() != null) {
      return formattingTuple.getMessage() + "\n" + CommonUtil.throwableToString(
          formattingTuple.getThrowable());
    } else {
      return formattingTuple.getMessage();
    }
  }

}
