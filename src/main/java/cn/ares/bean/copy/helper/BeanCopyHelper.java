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

import cn.ares.bean.copy.helper.constant.Mark;
import cn.ares.bean.copy.helper.model.CopyPropertiesReferenceIndex;
import cn.ares.bean.copy.helper.model.Property;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import cn.ares.bean.copy.helper.resolve.impl.ApacheBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.BootBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.HutoolBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopierResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.settings.BeanCopyHelperPluginSettings;
import cn.ares.bean.copy.helper.util.LocaleSupport;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

import static cn.ares.bean.copy.helper.constant.Mark.DIFF;
import static cn.ares.bean.copy.helper.constant.Mark.IGNORED;
import static cn.ares.bean.copy.helper.constant.Mark.SAME;
import static cn.ares.bean.copy.helper.constant.Mark.TYPE_NOT_MATCH;
import static cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopierResolveImpl.SPRING_BEAN_COPIER_CLASS_NAME;


/**
 * @author Aresxue
 */
public class BeanCopyHelper extends AnAction implements StartupActivity.DumbAware {

  private static final Logger LOGGER = Logger.getInstance(BeanCopyHelper.class);

  public static final String BEAN_COPY_HELPER = "BeanCopyHelper";
  public static final int FONE_SIZE_WIDTH = (int) (345 * 1.32);

  private static final Set<String> SETTER_LOMBOK_ANNOTATION_SET = Set.of("lombok.Setter", "lombok.Data");
  private static final Set<String> GETTER_LOMBOK_ANNOTATION_SET = Set.of("lombok.Getter", "lombok.Data", "lombok.Value");

  public static final Html METHOD_NOT_SUPPORTED_HTML = new Html(LocaleSupport.formatMessage("method.not.supported"));

  private static final List<BeanCopyResolve> RESOLVE_STRATEGIE_LIST = List.of(
      new ApacheBeanCopyResolveImpl(),
      new SpringBeanCopyResolveImpl(),
      new HutoolBeanCopyResolveImpl(),
      new SpringBeanCopierResolveImpl(),
      new BootBeanCopyResolveImpl()
  );

  private static final Set<String> BEAN_COPY_METHOD_SET = new HashSet<>();
  private static final Set<String> DEFAULT_IGNORE_PROPERTIES = new HashSet<>();

  static {
    BEAN_COPY_METHOD_SET.add("copyProperties");
    BEAN_COPY_METHOD_SET.add("BeanUtil.copyProperties");
    BEAN_COPY_METHOD_SET.add("BeanUtils.copyProperties");
    for (BeanCopyResolve beanCopyResolve : RESOLVE_STRATEGIE_LIST) {
      BEAN_COPY_METHOD_SET.add(beanCopyResolve.qualifiedName() + ".copyProperties");
    }
    BEAN_COPY_METHOD_SET.add("BeanCopier.create");
    BEAN_COPY_METHOD_SET.add(SPRING_BEAN_COPIER_CLASS_NAME + ".create");
    BEAN_COPY_METHOD_SET.add("BeanCopyUtil.copy");
    BEAN_COPY_METHOD_SET.add("BeanCopyUtil.copyPropertiesIgnoreNull");

    // 默认忽略的属性如serialVersionUID
    DEFAULT_IGNORE_PROPERTIES.add("serialVersionUID");
  }

  public static boolean isBeanCopyMethod(String canonicalText) {
    return BEAN_COPY_METHOD_SET.contains(canonicalText);
  }


  public static boolean isBeanCopyHelperAvailable(Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);

    if (elementAtCaret == null) {
      return false;
    }

    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression.class);
    if (methodCallExpression == null) {
      return false;
    }
    String canonicalText = methodCallExpression.getMethodExpression().getCanonicalText();
    return isBeanCopyMethod(canonicalText);
  }


  public static String resolveQualifiedName(PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    PsiElement resolvedMethod = methodExpression.resolve();
    if (resolvedMethod instanceof PsiMethod method) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        return containingClass.getQualifiedName();
      }
    }
    return null;
  }


  public static Result invoke(PsiMethodCallExpression methodCallExpression) {
    if (null == methodCallExpression) {
      return null;
    }
    for (BeanCopyResolve beanCopyResolve : RESOLVE_STRATEGIE_LIST) {
      if (beanCopyResolve.isSupport(methodCallExpression)) {
        return beanCopyResolve.resolve(methodCallExpression);
      }
    }
    return null;
  }

  public static Result invoke(Editor editor, PsiFile file) {
    try {
      int offset = editor.getCaretModel().getOffset();
      PsiElement elementAtCaret = file.findElementAt(offset);
      PsiMethodCallExpression methodCallExpression;
      methodCallExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression.class);
      return invoke(methodCallExpression);
    } catch (Exception exception) {
      return null;
    }
  }

  public static void markProperties(Set<String> ignoreProperties, Map<String, Property> propertyMap, Map<String, Property> lowerCasePropertyMap, Property property) {
    Mark mark;
    // 是否是忽略的
    String propertyName = property.getName();
    if (DEFAULT_IGNORE_PROPERTIES.contains(propertyName) || ignoreProperties.contains(propertyName)) {
      mark = IGNORED;
    } else {
      Property targetProperty = BeanCopyResolve.getProperty(propertyMap, lowerCasePropertyMap, propertyName);
      // 是否同名
      if (null == targetProperty) {
        mark = DIFF;
      } else {
        // 是否同类型
        if (property.equals(targetProperty) || (!lowerCasePropertyMap.isEmpty() && property.equalsIgnoreCase(targetProperty))) {
          mark = SAME;
        } else {
          mark = TYPE_NOT_MATCH;
        }
      }
    }
    property.setMark(mark);
  }

  public static Set<String> findCommonPropertyNameSet(Result result) {
    return findCommonPropertyNameSet(result.sourcePropertyMap.keySet(), result.targetPropertyMap.keySet(), result.ignoredProperties());
  }

  public static Set<String> findCommonPropertyNameSet(Set<String> sourceClassFieldSet, Set<String> targetClassFieldSet, Set<String> ignoreProperties) {
    sourceClassFieldSet.retainAll(targetClassFieldSet);
    // 移除忽略字段
    if (!ignoreProperties.isEmpty()) {
      ignoreProperties.forEach(sourceClassFieldSet::remove);
    }
    DEFAULT_IGNORE_PROPERTIES.forEach(sourceClassFieldSet::remove);

    return sourceClassFieldSet.stream().sorted().collect(Collectors.toCollection(TreeSet::new));
  }


  public static Set<String> getIgnoreProperties(PsiExpression[] expressions) {
    if (expressions.length > 2) {
      return Stream.of(expressions)
          .filter(expression -> expression instanceof PsiLiteralExpression)
          .map(literalExpression -> literalExpression.getText().replace("\"", ""))
          .collect(Collectors.toSet());
    }
    return Set.of();
  }

  @Override
  public void runActivity(@NotNull Project project) {
    DumbService.getInstance(project).runWhenSmart(() -> {
      // 注意要在后台线程执行，避免主线程卡顿
      Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(() -> {
        LOGGER.info("start scan project: " + project.getName());
        CopyPropertiesReferenceIndex.init(project);

        application.runReadAction(() -> {
          Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));
          PsiManager manager = PsiManager.getInstance(project);

          // 直接在当前读操作中处理文件，避免嵌套线程
          for (VirtualFile virtualFile : javaFiles) {
            if (null != virtualFile) {
              copyPropertiesReferenceScan(virtualFile, manager);
            }
          }
        });
      });
    });
    // 启动文件变更监听
    startFileChangeListener(project);
  }

  private void copyPropertiesReferenceScan(VirtualFile virtualFile, PsiManager manager) {
    try {
      if (null == virtualFile || !virtualFile.isValid()) {
        return;
      }
      PsiFile file = manager.findFile(virtualFile);
      if (file instanceof PsiJavaFile javaFile) {
        LOGGER.info("start scan file: " + virtualFile.getPath());
        // 扫描单个文件
        try {
          javaFile.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression) {
              try {
                super.visitMethodCallExpression(methodCallExpression);
                PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
                String canonicalText = methodExpression.getCanonicalText();
                if (isBeanCopyMethod(canonicalText)) {
                  Result result = invoke(methodCallExpression);
                  if (null == result) {
                    return;
                  }

                  PsiClass sourceClass = result.sourceClass();
                  PsiClass targetClass = result.targetClass();

                  if (sourceClass == null || targetClass == null) {
                    return;
                  }

                  Map<String, Property> targetPropertyMap = result.targetPropertyMap();
                  Set<String> sameProperties = targetPropertyMap.values().stream()
                      .filter(property -> SAME == property.getMark())
                      .map(Property::getName)
                      .collect(Collectors.toSet());
                  if (!sameProperties.isEmpty()) {
                    Set<String> setterMethodSet = new HashSet<>();
                    Set<String> getterMethodSet = new HashSet<>();
                    sameProperties.forEach(propertyName -> {
                      String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                      setterMethodSet.add("set" + suffix);
                      getterMethodSet.add("get" + suffix);
                    });
                    for (PsiMethod method : targetClass.getMethods()) {
                      if (setterMethodSet.contains(method.getName()) && validSetterMethod(method)) {
                        CopyPropertiesReferenceIndex.addReference(method, methodCallExpression);
                      }
                    }
                    for (PsiMethod method : sourceClass.getMethods()) {
                      if (getterMethodSet.contains(method.getName()) && validGetterMethod(method)) {
                        CopyPropertiesReferenceIndex.addReference(method, methodCallExpression);
                      }
                    }

                    // 添加使用了lombok的类的字段的引用
                    if (hasSetterLombokAnnotation(targetClass)) {
                      sameProperties.forEach(propertyName -> {
                        PsiField field = targetClass.findFieldByName(propertyName, true);
                        if (null != field) {
                          CopyPropertiesReferenceIndex.addReference(field, methodCallExpression);
                        }
                      });
                    }
                    if (hasGetterLombokAnnotation(sourceClass)) {
                      sameProperties.forEach(propertyName -> {
                        PsiField field = sourceClass.findFieldByName(propertyName, true);
                        if (null != field) {
                          CopyPropertiesReferenceIndex.addReference(field, methodCallExpression);
                        }
                      });
                    }
                  }
                }
              } catch (Throwable throwable) {
                LOGGER.warn("scan file: " + virtualFile.getPath() + " fail:", throwable);
              }
            }
          });
        } catch (Throwable throwable) {
          String message = throwable.getMessage();
          if (message != null && message.contains("Outdated stub in index")) {
            LOGGER.warn("Skip outdated stub index file: " + virtualFile.getPath());
            return;
          }
          LOGGER.warn("visit file fail: " + virtualFile.getPath(), throwable);
        }
        LOGGER.info("scan file end: " + virtualFile.getPath());
      }
    } catch (Throwable throwable) {
      LOGGER.warn("scan file: " + virtualFile.getPath() + " fail:", throwable);
    }
  }

  private boolean hasGetterLombokAnnotation(PsiClass sourceClass) {
    PsiModifierList modifierList = sourceClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName != null && GETTER_LOMBOK_ANNOTATION_SET.contains(qualifiedName)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasSetterLombokAnnotation(PsiClass targetClass) {
    PsiModifierList modifierList = targetClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName != null && SETTER_LOMBOK_ANNOTATION_SET.contains(qualifiedName)) {
          return true;
        }
      }
    }
    return false;
  }


  private boolean validSetterMethod(PsiMethod method) {
    // 非static返回为void入参个数为1的方法
    return method.getParameterList().getParametersCount() == 1
        && PsiTypes.voidType() == method.getReturnType()
        && !method.hasModifierProperty(PsiModifier.STATIC);
  }

  private boolean validGetterMethod(PsiMethod method) {
    // 非static返回不为void入参个数为0的方法
    return method.getParameterList().getParametersCount() == 0
        && PsiTypes.voidType() != method.getReturnType()
        && !method.hasModifierProperty(PsiModifier.STATIC);
  }

  private void startFileChangeListener(@NotNull Project project) {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        Application application = ApplicationManager.getApplication();
        application.executeOnPooledThread(() ->
            DumbService.getInstance(project).runWhenSmart(() ->
                application.runReadAction(() -> {
                  PsiManager manager = PsiManager.getInstance(project);
                  for (VFileEvent event : events) {
                    handleEvent(event, manager, project);
                  }
                })));
      }
    });
  }

  private void handleEvent(VFileEvent event, PsiManager manager, @NotNull Project project) {
    if (event instanceof VFileCreateEvent fileCreateEvent) {
      // 扫描新增文件
      VirtualFile virtualFile = fileCreateEvent.getFile();
      copyPropertiesReferenceScan(virtualFile, manager);
    } else if (event instanceof VFileCopyEvent fileCopyEvent) {
      // 对于复制事件，需要延迟处理以获取真实文件
      Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(() ->
          DumbService.getInstance(project).runWhenSmart(() -> application.runReadAction(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(new File(fileCopyEvent.getPath()));
            if (null != virtualFile) {
              copyPropertiesReferenceScan(virtualFile,
                  PsiManager.getInstance(project));
            }
          })));
    } else if (event instanceof VFileContentChangeEvent fileContentChangeEvent) {
      VirtualFile virtualFile = fileContentChangeEvent.getFile();
      copyPropertiesReferenceScan(virtualFile, manager);
    } else if (event instanceof VFileDeleteEvent fileDeleteEvent) {
    } else if (event instanceof VFileMoveEvent fileMoveEvent) {
    } else if (event instanceof VFilePropertyChangeEvent filePropertyChangeEvent) {
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent anActionEvent) {
    // 决定何时显示
    PsiElement element = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT);
    boolean visible = false;
    if (element instanceof PsiMethod method) {
      if ((method.getName().startsWith("set") && validSetterMethod(method))
          || (method.getName().startsWith("get") && validGetterMethod(method))) {
        visible = isVisible(method);
      }
    } else if (element instanceof PsiField field) {
      visible = isVisible(field);
    }
    anActionEvent.getPresentation().setEnabledAndVisible(visible);
  }

  private boolean isVisible(PsiMember member) {
    List<PsiMethodCallExpression> referenceList = CopyPropertiesReferenceIndex.getReferenceList(member);
    return !referenceList.isEmpty();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    PsiElement element = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT);
    if (element instanceof PsiMember member) {
      List<PsiMethodCallExpression> referenceList = CopyPropertiesReferenceIndex.getReferenceList(member);
      if (referenceList.isEmpty()) {
        return;
      }

      if (referenceList.size() == 1) {
        navigateTo(referenceList.getFirst());
      } else {
        Project project = member.getProject();
        FindManager findManager = project.getService(FindManager.class);
        if (findManager instanceof FindManagerImpl findManagerImpl) {
          FindUsagesManager findUsagesManager = findManagerImpl.getFindUsagesManager();
          FindUsagesHandler findUsagesHandler = findUsagesManager.getFindUsagesHandler(member, true);
          FindUsagesOptions findUsagesOptions = null == findUsagesHandler ? new FindUsagesOptions(project) : findUsagesHandler.getFindUsagesOptions();
          FindUsagesHandlerBase findUsagesHandlerBase = new FindUsagesHandlerBase(member);
          UsageViewPresentation usageViewPresentation = findUsagesManager.createPresentation(findUsagesHandlerBase, findUsagesOptions);

          UsageTarget[] usageTargets = new UsageTarget[]{new PsiElement2UsageTargetAdapter(member, true)};
          Usage[] usages = referenceList.stream()
              .map(reference -> new UsageInfo2UsageAdapter(new UsageInfo(reference)))
              .toArray(Usage[]::new);
          UsageViewManager.getInstance(project).showUsages(
              usageTargets,
              usages,
              usageViewPresentation
          );
        }
      }
    }
  }

  private void navigateTo(PsiElement element) {
    if (element == null || !element.isValid()) {
      return;
    }
    if (element instanceof Navigatable navigatable) {
      navigatable.navigate(true);
    } else if (element.getNavigationElement() instanceof Navigatable navigatable) {
      navigatable.navigate(true);
    }
  }

  public static double getFontSize(int maxLength) {
    // 计算字体大小保留1位小数
    String foneSizePercentage = BeanCopyHelperPluginSettings.getInstance().getFoneSizePercentage();
    double fontSize = ((int) Math.round((FONE_SIZE_WIDTH * Integer.parseInt(foneSizePercentage) * 0.01 / maxLength) * 10)) * 0.1;
    if (fontSize > 10) {
      fontSize = 10;
    }
    return fontSize;
  }

  public record Result(PsiClass sourceClass, PsiClass targetClass,
                       Map<String, Property> sourcePropertyMap,
                       Map<String, Property> targetPropertyMap,
                       Map<String, Property> lowerCaseSourcePropertyMap,
                       Map<String, Property> lowerCaseTargetPropertyMap,
                       Set<String> ignoredProperties) {

  }


}
