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

import cn.ares.bean.copy.helper.BeanCopyHelper;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

  /**
   * 缓存属性复制调用点所在文件与成员的映射关系，用于文件变更时按文件粒度清除索引
   * SmartPsiElementPointer 在PSI整树重建后会按文本范围还原，可能落到同偏移的另一个元素上且仍然有效，仅靠有效性检查无法剔除这类陈旧索引
   */
  private static final Map<String, Set<SmartPsiElementPointer<PsiMember>>> FILE_TO_MEMBER_MAP = new ConcurrentHashMap<>();

  private static final ScheduledExecutorService CLEAN_INVALID_REFERENCE_SCHEDULED = Executors.newSingleThreadScheduledExecutor();

  private static final Map<Project, SmartPointerManager> SMART_POINTER_MANAGER_MAP = new ConcurrentHashMap<>();

  /**
   * 定时清理任务只需注册一次，多项目同时打开时避免叠加出多个清理任务
   */
  private static final AtomicBoolean CLEAN_SCHEDULED_REGISTERED = new AtomicBoolean(false);

  public static void init(Project project) {
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    SMART_POINTER_MANAGER_MAP.put(project, smartPointerManager);

    if (CLEAN_SCHEDULED_REGISTERED.compareAndSet(false, true)) {
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
    }

    // 注册项目关闭时的资源清理
    Disposer.register(project, () -> {
      SMART_POINTER_MANAGER_MAP.remove(project);
      // 清理项目相关的方法引用映射
      METHOD_TO_COPY_PROPERTIES_MAP.entrySet().removeIf(entry -> {
        PsiMember member = entry.getKey().getElement();
        return member == null || !member.isValid() || member.getProject() == project;
      });
      cleanFileIndexByProject(project);
    });
  }

  private static void cleanFileIndexByProject(Project project) {
    FILE_TO_MEMBER_MAP.entrySet().removeIf(entry -> {
      Set<SmartPsiElementPointer<PsiMember>> memberPointerSet = entry.getValue();
      memberPointerSet.removeIf(memberPointer -> project == memberPointer.getProject());
      return memberPointerSet.isEmpty();
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

    // 文件反向索引也要清理失效的成员指针，否则随着编辑不断膨胀
    FILE_TO_MEMBER_MAP.entrySet().removeIf(entry -> {
      Set<SmartPsiElementPointer<PsiMember>> memberPointerSet = entry.getValue();
      memberPointerSet.removeIf(memberPointer -> {
        PsiMember member = memberPointer.getElement();
        return null == member || !member.isValid();
      });
      return memberPointerSet.isEmpty();
    });
  }

  /**
   * 只清空索引不关闭调度器，供项目关闭和测试隔离使用
   */
  public static void clear() {
    METHOD_TO_COPY_PROPERTIES_MAP.clear();
    FILE_TO_MEMBER_MAP.clear();
    SMART_POINTER_MANAGER_MAP.clear();
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
    clear();
  }

  public static void addReference(PsiMember member, PsiMethodCallExpression methodCallExpression) {
    try {
      SmartPointerManager smartPointerManager = getSmartPointerManager(member);
      SmartPsiElementPointer<PsiMember> methodPointer = smartPointerManager.createSmartPsiElementPointer(member);
      SmartPsiElementPointer<PsiMethodCallExpression> methodCallExpressionPointer = smartPointerManager.createSmartPsiElementPointer(methodCallExpression);
      METHOD_TO_COPY_PROPERTIES_MAP.computeIfAbsent(methodPointer, k -> ConcurrentHashMap.newKeySet()).add(methodCallExpressionPointer);

      String fileUrl = resolveFileUrl(methodCallExpression);
      if (null != fileUrl) {
        FILE_TO_MEMBER_MAP.computeIfAbsent(fileUrl, k -> ConcurrentHashMap.newKeySet()).add(methodPointer);
      }
    } catch (Exception exception) {
      LOGGER.warn("add reference fail:", exception);
    }
  }

  private static String resolveFileUrl(PsiMethodCallExpression methodCallExpression) {
    PsiFile containingFile = methodCallExpression.getContainingFile();
    if (null == containingFile) {
      return null;
    }
    VirtualFile virtualFile = containingFile.getVirtualFile();
    return null == virtualFile ? null : virtualFile.getUrl();
  }

  /**
   * 清除指定文件中所有属性复制调用点产生的索引，重扫该文件前必须先调用否则索引只增不减
   */
  public static void removeByFile(String fileUrl) {
    if (null == fileUrl) {
      return;
    }
    Set<SmartPsiElementPointer<PsiMember>> memberPointerSet = FILE_TO_MEMBER_MAP.remove(fileUrl);
    if (null == memberPointerSet) {
      return;
    }
    memberPointerSet.forEach(memberPointer -> {
      Set<SmartPsiElementPointer<PsiMethodCallExpression>> methodCallExpressionPointerSet = METHOD_TO_COPY_PROPERTIES_MAP.get(memberPointer);
      if (null == methodCallExpressionPointerSet) {
        return;
      }
      methodCallExpressionPointerSet.removeIf(methodCallExpressionPointer -> belongToFile(methodCallExpressionPointer, fileUrl));
      if (methodCallExpressionPointerSet.isEmpty()) {
        METHOD_TO_COPY_PROPERTIES_MAP.remove(memberPointer);
      }
    });
  }

  /**
   * 按url前缀批量清除索引，用于目录被删除或移动的场景
   */
  public static void removeByDirectory(String directoryUrl) {
    if (null == directoryUrl) {
      return;
    }
    String prefix = directoryUrl.endsWith("/") ? directoryUrl : directoryUrl + "/";
    List<String> fileUrlList = FILE_TO_MEMBER_MAP.keySet().stream().filter(fileUrl -> fileUrl.startsWith(prefix)).toList();
    fileUrlList.forEach(CopyPropertiesReferenceIndex::removeByFile);
  }

  private static boolean belongToFile(SmartPsiElementPointer<?> pointer, String fileUrl) {
    // 用指针自带的虚拟文件而非解引用后的元素，元素已失效时依然可以判定归属
    VirtualFile virtualFile = pointer.getVirtualFile();
    return null != virtualFile && fileUrl.equals(virtualFile.getUrl());
  }

  private static SmartPointerManager getSmartPointerManager(PsiMember member) {
    Project project = member.getProject();
    return SMART_POINTER_MANAGER_MAP.computeIfAbsent(project, k -> SmartPointerManager.getInstance(project));
  }

  /**
   * 是否存在属性复制引用，命中首个即返回，供右键菜单的显示判断使用
   */
  public static boolean hasReference(PsiMember member) {
    return !collectReferenceList(member, true).isEmpty();
  }

  public static List<PsiMethodCallExpression> getReferenceList(PsiMember member) {
    return collectReferenceList(member, false);
  }

  /**
   * @author: Aresxue
   * @description: 收集成员对应的属性复制调用点，返回前逐个做语义复核剔除陈旧索引
   * @time: 2026-09-02 10:00:00
   * @params: [member, stopAtFirst] 目标成员，是否命中首个即停止
   * @return: List<PsiMethodCallExpression> 确实拷贝了该成员对应属性的调用点
   */
  private static List<PsiMethodCallExpression> collectReferenceList(PsiMember member, boolean stopAtFirst) {
    if (null == member) {
      return Collections.emptyList();
    }
    SmartPsiElementPointer<PsiMember> methodPointer = getSmartPointerManager(member).createSmartPsiElementPointer(member);
    if (!member.isValid()) {
      METHOD_TO_COPY_PROPERTIES_MAP.remove(methodPointer);
      return Collections.emptyList();
    }

    Set<SmartPsiElementPointer<PsiMethodCallExpression>> methodCallExpressionPointerSet = METHOD_TO_COPY_PROPERTIES_MAP.get(methodPointer);
    if (null == methodCallExpressionPointerSet || methodCallExpressionPointerSet.isEmpty()) {
      // 剔除掉无效的引用
      METHOD_TO_COPY_PROPERTIES_MAP.remove(methodPointer);
      return Collections.emptyList();
    }

    List<PsiMethodCallExpression> methodCallExpressionList = new ArrayList<>();
    Iterator<SmartPsiElementPointer<PsiMethodCallExpression>> methodCallExpressionIterator = methodCallExpressionPointerSet.iterator();
    // 遍历所有引用，剔除掉无效的和已经不再拷贝该属性的引用
    while (methodCallExpressionIterator.hasNext()) {
      SmartPsiElementPointer<PsiMethodCallExpression> methodCallExpressionPointer = methodCallExpressionIterator.next();
      PsiMethodCallExpression methodCallExpression = methodCallExpressionPointer.getElement();
      if (null == methodCallExpression || !methodCallExpression.isValid()) {
        methodCallExpressionIterator.remove();
        continue;
      }
      if (!BeanCopyHelper.matchesPropertyCopy(member, methodCallExpression)) {
        methodCallExpressionIterator.remove();
        continue;
      }
      methodCallExpressionList.add(methodCallExpression);
      if (stopAtFirst) {
        break;
      }
    }
    if (methodCallExpressionPointerSet.isEmpty()) {
      METHOD_TO_COPY_PROPERTIES_MAP.remove(methodPointer);
    }
    return methodCallExpressionList;
  }

}
