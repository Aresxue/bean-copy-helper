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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author: Aresxue
 * @time: 2025-07-08 16:27:56
 * @version: JDK 21
 */
public class CopyPropertiesReferenceIndex {

  private static final Logger LOGGER = Logger.getInstance(CopyPropertiesReferenceIndex.class);

  /**
   * 缓存set方法与copy方法的映射关系
   */
  private static final Map<SmartPsiElementPointer<PsiMember>, Set<SmartPsiElementPointer<PsiMethodCallExpression>>> METHOD_TO_COPY_PROPERTIES_MAP = new ConcurrentHashMap<>();

  private static final ScheduledExecutorService CLEAN_INVALID_REFERENCE_SCHEDULED = Executors.newSingleThreadScheduledExecutor();

  private static final Map<Project, SmartPointerManager> SMART_POINTER_MANAGER_MAP = new ConcurrentHashMap<>();

  public static void init(Project project) {
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    SMART_POINTER_MANAGER_MAP.put(project, smartPointerManager);
    
    // 每隔5min执行一次引用检查清理失效索引
    CLEAN_INVALID_REFERENCE_SCHEDULED.scheduleAtFixedRate(() -> {
      try {
        Application application = ApplicationManager.getApplication();
        // 直接在清理线程中检查，避免在后台线程中执行读操作
        if (application.isReadAccessAllowed()) {
          cleanInvalidReferences();
        } else {
          application.runReadAction(CopyPropertiesReferenceIndex::cleanInvalidReferences);
        }
      } catch (Exception exception) {
        LOGGER.warn("clean invalid reference fail:", exception);
      }
    }, 0, 5, TimeUnit.MINUTES);
    
    // 注册项目关闭时的资源清理
    Disposer.register(project, () -> {
      SMART_POINTER_MANAGER_MAP.remove(project);
      // 清理项目相关的方法引用映射
      METHOD_TO_COPY_PROPERTIES_MAP.entrySet().removeIf(entry -> {
        PsiMember member = entry.getKey().getElement();
        return member == null || !member.isValid() || member.getProject() == project;
      });
    });
  }

  private static void cleanInvalidReferences() {
    List<SmartPsiElementPointer<PsiMember>> invalidMethodPointerList = new ArrayList<>();
    METHOD_TO_COPY_PROPERTIES_MAP.keySet().forEach(methodPointer -> {
      PsiMember member = methodPointer.getElement();
      if (null == member || !member.isValid()) {
        invalidMethodPointerList.add(methodPointer);
      } else {
        METHOD_TO_COPY_PROPERTIES_MAP.get(methodPointer).removeIf(methodCallExpressionPointer -> {
              PsiMethodCallExpression methodCallExpression = methodCallExpressionPointer.getElement();
              return null == methodCallExpression || !methodCallExpression.isValid();
            });
      }
    });
    invalidMethodPointerList.forEach(METHOD_TO_COPY_PROPERTIES_MAP::remove);
  }

  /**
   * 应用关闭时调用，清理全局资源
   */
  public static void shutdown() {
    if (!CLEAN_INVALID_REFERENCE_SCHEDULED.isShutdown()) {
      CLEAN_INVALID_REFERENCE_SCHEDULED.shutdown();
      try {
        if (!CLEAN_INVALID_REFERENCE_SCHEDULED.awaitTermination(5, TimeUnit.SECONDS)) {
          CLEAN_INVALID_REFERENCE_SCHEDULED.shutdownNow();
        }
      } catch (InterruptedException e) {
        CLEAN_INVALID_REFERENCE_SCHEDULED.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    METHOD_TO_COPY_PROPERTIES_MAP.clear();
    SMART_POINTER_MANAGER_MAP.clear();
  }

  public static void addReference(PsiMember member, PsiMethodCallExpression methodCallExpression) {
    try {
      SmartPointerManager smartPointerManager = getSmartPointerManager(member);
      SmartPsiElementPointer<PsiMember> methodPointer = smartPointerManager.createSmartPsiElementPointer(member);
      SmartPsiElementPointer<PsiMethodCallExpression> methodCallExpressionPointer = smartPointerManager.createSmartPsiElementPointer(methodCallExpression);
      METHOD_TO_COPY_PROPERTIES_MAP.computeIfAbsent(methodPointer, k -> new HashSet<>()).add(methodCallExpressionPointer);
    } catch (Exception exception) {
      LOGGER.warn("add reference fail:", exception);
    }
  }

  private static SmartPointerManager getSmartPointerManager(PsiMember member) {
    Project project = member.getProject();
    return SMART_POINTER_MANAGER_MAP.computeIfAbsent(project, k -> SmartPointerManager.getInstance(project));
  }

  public static List<PsiMethodCallExpression> getReferenceList(PsiMember member) {
    if (null == member) {
      return Collections.emptyList();
    }
    SmartPsiElementPointer<PsiMember> methodPointer = getSmartPointerManager(member).createSmartPsiElementPointer(member);
    if (!member.isValid()) {
      METHOD_TO_COPY_PROPERTIES_MAP.remove(methodPointer);
      return Collections.emptyList();
    }

    Set<SmartPsiElementPointer<PsiMethodCallExpression>> methodCallExpressionPointerSet = METHOD_TO_COPY_PROPERTIES_MAP.get(methodPointer);
    List<PsiMethodCallExpression> methodCallExpressionList;
    if (null == methodCallExpressionPointerSet || methodCallExpressionPointerSet.isEmpty()) {
      // 剔除掉无效的引用
      METHOD_TO_COPY_PROPERTIES_MAP.remove(methodPointer);
      methodCallExpressionList = Collections.emptyList();
    } else {
      methodCallExpressionList = new ArrayList<>();
      Iterator<SmartPsiElementPointer<PsiMethodCallExpression>> methodCallExpressionIterator = methodCallExpressionPointerSet.iterator();
      // 遍历所有引用，剔除掉无效的引用
      while (methodCallExpressionIterator.hasNext()) {
        SmartPsiElementPointer<PsiMethodCallExpression> methodCallExpressionPointer = methodCallExpressionIterator.next();
        PsiMethodCallExpression methodCallExpression = methodCallExpressionPointer.getElement();
        if (methodCallExpression != null && methodCallExpression.isValid()) {
          methodCallExpressionList.add(methodCallExpression);
        } else {
          // 剔除掉无效的引用
          methodCallExpressionIterator.remove();
        }
      }
    }
    return methodCallExpressionList;
  }

}
