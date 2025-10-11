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

import com.intellij.util.ArrayUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author: Aresxue
 * @time: 2025-06-28 21:50:46
 * @version: JDK 21
 */
public class CommonUtil {

  /**
   * 左填充或右填充的限制 limit when leftPad or rightPad
   */
  private static final int PAD_LIMIT = 8192;

  public static <V, K> Map<K, V> toIdentityMap(Collection<V> collection,
      Function<V, K> keyFunction) {
    if (null == collection || collection.isEmpty()) {
      return Collections.emptyMap();
    }
    return toMap(collection, (v) -> Optional.ofNullable(v).map(keyFunction).orElse(null), Function.identity());
  }

  public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> key,
      Function<E, V> value) {
    return of(collection).collect(HashMap::new, (m, v) -> m.put(key.apply(v), value.apply(v)),
        HashMap::putAll);
  }

  public static <T> Stream<T> of(Iterable<T> iterable) {
    if (null == iterable) {
      throw new IllegalArgumentException("Iterable must be not null!");
    }

    return iterable instanceof Collection ? ((Collection<T>) iterable).stream()
        : StreamSupport.stream(iterable.spliterator(), false);
  }


  public static String format(CharSequence template, Object... params) {
    if (null == template) {
      return "null";
    }
    if (ArrayUtil.isEmpty(params) || isBlank(template)) {
      return template.toString();
    }
    return format(template.toString(), params);
  }


  public static String format(String strPattern, Object... argArray) {
    if (isBlank(strPattern) || isBlank("{}") || ArrayUtil.isEmpty(argArray)) {
      return strPattern;
    }
    final int strPatternLength = strPattern.length();
    final int placeHolderLength = "{}".length();

    // 初始化定义好的长度以获得更好的性能
    final StringBuilder stringBuilder = new StringBuilder(strPatternLength + 50);

    int handledPosition = 0;
    int delimIndex;
    for (int argIndex = 0; argIndex < argArray.length; argIndex++) {
      delimIndex = strPattern.indexOf("{}", handledPosition);
      if (delimIndex == -1) {
        if (handledPosition == 0) {
          return strPattern;
        }
        // 字符串模板剩余部分不再包含占位符，加入剩余部分后返回结果
        stringBuilder.append(strPattern, handledPosition, strPatternLength);
        return stringBuilder.toString();
      }

      // 转义符
      if (delimIndex > 0 && strPattern.charAt(delimIndex - 1) == '\\') {// 转义符
        if (delimIndex > 1 && strPattern.charAt(delimIndex - 2) == '\\') {// 双转义符
          // 转义符之前还有一个转义符，占位符依旧有效
          stringBuilder.append(strPattern, handledPosition, delimIndex - 1);
          stringBuilder.append(utf8Str(argArray[argIndex]));
          handledPosition = delimIndex + placeHolderLength;
        } else {
          // 占位符被转义
          argIndex--;
          stringBuilder.append(strPattern, handledPosition, delimIndex - 1);
          stringBuilder.append('{');
          handledPosition = delimIndex + 1;
        }
      } else {// 正常占位符
        stringBuilder.append(strPattern, handledPosition, delimIndex);
        stringBuilder.append(utf8Str(argArray[argIndex]));
        handledPosition = delimIndex + placeHolderLength;
      }
    }

    // 加入最后一个占位符后所有的字符
    stringBuilder.append(strPattern, handledPosition, strPatternLength);

    return stringBuilder.toString();
  }

  public static String utf8Str(Object obj) {
    if (null == obj) {
      return null;
    }

    if (obj instanceof String) {
      return (String) obj;
    } else if (obj instanceof byte[]) {
      return str((byte[]) obj, StandardCharsets.UTF_8);
    } else if (obj instanceof Byte[]) {
      return str((Byte[]) obj, StandardCharsets.UTF_8);
    } else if (obj instanceof ByteBuffer) {
      return StandardCharsets.UTF_8.decode((ByteBuffer) obj).toString();
    } else if (isArray(obj)) {
      return throwableToString(obj);
    }

    return obj.toString();
  }

  public static boolean isArray(Object obj) {
    return null != obj && obj.getClass().isArray();
  }

  public static String throwableToString(Object obj) {
    if (null == obj) {
      return null;
    }

    if (obj instanceof long[]) {
      return Arrays.toString((long[]) obj);
    } else if (obj instanceof int[]) {
      return Arrays.toString((int[]) obj);
    } else if (obj instanceof short[]) {
      return Arrays.toString((short[]) obj);
    } else if (obj instanceof char[]) {
      return Arrays.toString((char[]) obj);
    } else if (obj instanceof byte[]) {
      return Arrays.toString((byte[]) obj);
    } else if (obj instanceof boolean[]) {
      return Arrays.toString((boolean[]) obj);
    } else if (obj instanceof float[]) {
      return Arrays.toString((float[]) obj);
    } else if (obj instanceof double[]) {
      return Arrays.toString((double[]) obj);
    } else if (isArray(obj)) {
      // 对象数组
      try {
        return Arrays.deepToString((Object[]) obj);
      } catch (Exception ignore) {
        //ignore
      }
    }

    return obj.toString();
  }

  public static String str(byte[] data, Charset charset) {
    if (data == null) {
      return null;
    }

    if (null == charset) {
      return new String(data);
    }
    return new String(data, charset);
  }

  public static String str(Byte[] data, Charset charset) {
    if (data == null) {
      return null;
    }

    byte[] bytes = new byte[data.length];
    Byte dataByte;
    for (int i = 0; i < data.length; i++) {
      dataByte = data[i];
      bytes[i] = (null == dataByte) ? -1 : dataByte;
    }

    return str(bytes, charset);
  }

  public static String str(ByteBuffer data, Charset charset) {
    if (null == charset) {
      charset = Charset.defaultCharset();
    }
    return charset.decode(data).toString();
  }

  public static boolean isBlank(CharSequence str) {
    final int length;
    if ((str == null) || ((length = str.length()) == 0)) {
      return true;
    }

    for (int i = 0; i < length; i++) {
      // 只要有一个非空字符即为非空字符串
      if (!isBlankChar(str.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  public static boolean isBlankChar(char c) {
    return Character.isWhitespace(c)
        || Character.isSpaceChar(c)
        || c == '\ufeff'
        || c == '\u202a'
        || c == '\u0000'
        || c == '\u3164'
        // Braille Pattern Blank
        || c == '\u2800'
        // MONGOLIAN VOWEL SEPARATOR
        || c == '\u180e';
  }

  public static boolean isNotBlank(CharSequence str) {
    return !isBlank(str);
  }

  public static boolean isEmpty(final String str) {
    return null == str || str.isEmpty();
  }

  public static String repeat(final char ch, final int repeat) {
    if (repeat <= 0) {
      return "";
    }
    final char[] buf = new char[repeat];
    Arrays.fill(buf, ch);
    return new String(buf);
  }

  public static String lowerFirst(final String source) {
    if (isEmpty(source)) {
      return source;
    }
    final char[] chars = source.toCharArray();
    chars[0] = 65 <= chars[0] && chars[0] <= 90 ? (char) (chars[0] + 32) : chars[0];
    return String.valueOf(chars);
  }

  public static String upperFirst(final String source) {
    if (isEmpty(source)) {
      return source;
    }
    final char[] chars = source.toCharArray();
    chars[0] = 97 <= chars[0] && chars[0] <= 122 ? (char) (chars[0] - 32) : chars[0];
    return String.valueOf(chars);
  }

  public static String rightPad(final String str, int size) {
    return rightPad(str, size, true);
  }

  public static String rightPad(final String str, int size, boolean html) {
    if (html) {
      return quote(rightPad(str, size, '@').replace("@", "&nbsp;"));
    } else {
      return rightPad(str, size, ' ');
    }
  }

  public static String quote(String str) {
    return str.replace("<", "&lt;").replace(">", "&gt;");
  }

  public static String rightPad(final String str, final int size, final char padChar) {
    if (str == null) {
      return null;
    }
    final int pads = size - str.length();
    if (pads <= 0) {
      return str;
    }
    if (pads > PAD_LIMIT) {
      return rightPad(str, size, String.valueOf(padChar));
    }
    return str.concat(repeat(padChar, pads));
  }

  public static String rightPad(final String str, final int size, String padStr) {
    if (str == null) {
      return null;
    }
    if (isEmpty(padStr)) {
      padStr = " ";
    }
    final int padLen = padStr.length();
    final int strLen = str.length();
    final int pads = size - strLen;
    if (pads <= 0) {
      // Returns original String when possible
      return str;
    }
    if (padLen == 1 && pads <= PAD_LIMIT) {
      return rightPad(str, size, padStr.charAt(0));
    }

    if (pads == padLen) {
      return str.concat(padStr);
    } else if (pads < padLen) {
      return str.concat(padStr.substring(0, pads));
    } else {
      final char[] padding = new char[pads];
      final char[] padChars = padStr.toCharArray();
      for (int i = 0; i < pads; i++) {
        padding[i] = padChars[i % padLen];
      }
      return str.concat(new String(padding));
    }
  }

  public static String throwableToString(Throwable throwable) {
    StringWriter stringWriter = new StringWriter();
    throwable.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }

}
