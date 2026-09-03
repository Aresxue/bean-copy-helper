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
import cn.ares.bean.copy.helper.model.IgnoreProperties;
import cn.ares.bean.copy.helper.model.Property;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import cn.ares.bean.copy.helper.resolve.impl.ApacheBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.BootBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.HutoolBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopierResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.settings.BeanCopyHelperPluginSettings;
import cn.ares.bean.copy.helper.util.CommonUtil;
import cn.ares.bean.copy.helper.util.LocaleSupport;
import cn.ares.bean.copy.helper.util.PsiConstantUtil;
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
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
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
import static cn.ares.bean.copy.helper.resolve.impl.HutoolBeanCopyResolveImpl.HUTOOL_BEAN_UTIL_CLASS_NAME;
import static cn.ares.bean.copy.helper.resolve.impl.HutoolBeanCopyResolveImpl.HUTOOL_EXTRA_METHOD_NAME_LIST;
import static cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopierResolveImpl.SPRING_BEAN_COPIER_CLASS_NAME;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;


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

  private static final String IGNORE_PROPERTIES_UNRESOLVED = LocaleSupport.formatMessage("ignore.properties.unresolved");

  private static final List<BeanCopyResolve> RESOLVE_STRATEGIE_LIST = List.of(
      new ApacheBeanCopyResolveImpl(),
      new SpringBeanCopyResolveImpl(),
      new HutoolBeanCopyResolveImpl(),
      new SpringBeanCopierResolveImpl(),
      new BootBeanCopyResolveImpl()
  );

  private static final Set<String> BEAN_COPY_METHOD_SET = new HashSet<>();
  private static final Set<String> DEFAULT_IGNORE_PROPERTIES = new HashSet<>();

  private static final int IGNORE_PROPERTY_RESOLVE_MAX_DEPTH = 5;
  /**
   * 前两个参数是源和目标，忽略属性从第三个参数开始
   */
  private static final int DEFAULT_IGNORE_PROPERTIES_START_INDEX = 2;

  static {
    BEAN_COPY_METHOD_SET.add("copyProperties");
    BEAN_COPY_METHOD_SET.add("BeanUtil.copyProperties");
    BEAN_COPY_METHOD_SET.add("BeanUtils.copyProperties");
    for (BeanCopyResolve beanCopyResolve : RESOLVE_STRATEGIE_LIST) {
      BEAN_COPY_METHOD_SET.add(beanCopyResolve.qualifiedName() + ".copyProperties");
    }
    // 只注册带类名的形式，toBean是常见的自定义方法名，注册裸方法名会让任意项目的toBean调用都弹出意图
    for (String methodName : HUTOOL_EXTRA_METHOD_NAME_LIST) {
      BEAN_COPY_METHOD_SET.add("BeanUtil." + methodName);
      BEAN_COPY_METHOD_SET.add(HUTOOL_BEAN_UTIL_CLASS_NAME + "." + methodName);
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
    // 不能就地改写入参，调用方拿的是Result里的属性Map的keySet，剪掉后后续的类型告警会丢失
    Set<String> commonPropertyNameSet = new TreeSet<>(sourceClassFieldSet);
    commonPropertyNameSet.retainAll(targetClassFieldSet);
    // 移除忽略字段
    ignoreProperties.forEach(commonPropertyNameSet::remove);
    DEFAULT_IGNORE_PROPERTIES.forEach(commonPropertyNameSet::remove);

    return commonPropertyNameSet;
  }


  public static IgnoreProperties getIgnoreProperties(PsiMethodCallExpression methodCallExpression, PsiClass sourceClass, PsiClass targetClass) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    int startIndex = resolveIgnorePropertiesStartIndex(methodCallExpression);
    if (startIndex < 0 || expressions.length <= startIndex) {
      return IgnoreProperties.RESOLVED_EMPTY;
    }
    Set<String> ignorePropertyNameSet = new HashSet<>();
    boolean resolved = true;
    for (int i = startIndex; i < expressions.length; i++) {
      resolved &= collectIgnoreProperty(expressions[i], ignorePropertyNameSet, sourceClass, targetClass, 0);
    }
    return new IgnoreProperties(ignorePropertyNameSet, resolved);
  }

  /**
   * 忽略属性的起始下标按形参类型定位，否则Hutool的ignoreCase、BeanCopier的useConverter这类
   * 同位置的非String参数会被当成忽略属性；返回-1表示该重载没有忽略属性参数
   * 方法解析不出来时回落到通用形态，即前两个参数是源和目标，忽略属性从第三个参数开始
   */
  private static int resolveIgnorePropertiesStartIndex(PsiMethodCallExpression methodCallExpression) {
    PsiMethod method = methodCallExpression.resolveMethod();
    if (null == method) {
      return DEFAULT_IGNORE_PROPERTIES_START_INDEX;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = DEFAULT_IGNORE_PROPERTIES_START_INDEX; i < parameters.length; i++) {
      // String、String[]和String...的元素类型都是String
      if (JAVA_LANG_STRING.equals(parameters[i].getType().getDeepComponentType().getCanonicalText())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * 解析单个忽略属性参数的实际值，返回false表示无法静态求出，供Hutool的CopyOptions链式配置复用
   */
  public static boolean collectIgnoreProperty(PsiExpression expression, Set<String> ignorePropertyNameSet,
      PsiClass sourceClass, PsiClass targetClass) {
    return collectIgnoreProperty(expression, ignorePropertyNameSet, sourceClass, targetClass, 0);
  }

  /**
   * 忽略属性常量化是常见写法，先用平台的常量求值覆盖字面量、常量引用、拼接、括号、三元和强转，
   * 求不出来再按数组初始化器、lombok常量、变量初始值逐级还原，返回false表示实际值无法静态求出
   * depth用于防止常量之间循环引用导致无限递归
   */
  private static boolean collectIgnoreProperty(PsiExpression expression, Set<String> ignorePropertyNameSet,
      PsiClass sourceClass, PsiClass targetClass, int depth) {
    if (null == expression || depth > IGNORE_PROPERTY_RESOLVE_MAX_DEPTH) {
      return false;
    }
    Object constantValue = PsiConstantUtil.evaluate(expression);
    if (constantValue instanceof String propertyName) {
      ignorePropertyNameSet.add(propertyName);
      return true;
    }
    if (null != constantValue) {
      // 求出了非字符串常量，这个位置不是忽略属性，不算解析失败
      return true;
    }
    PsiArrayInitializerExpression arrayInitializer = PsiConstantUtil.findArrayInitializer(expression);
    if (null != arrayInitializer) {
      boolean resolved = true;
      for (PsiExpression element : arrayInitializer.getInitializers()) {
        resolved &= collectIgnoreProperty(element, ignorePropertyNameSet, sourceClass, targetClass, depth + 1);
      }
      return resolved;
    }
    if (expression instanceof PsiReferenceExpression referenceExpression) {
      return collectIgnorePropertyByReference(referenceExpression, ignorePropertyNameSet, sourceClass, targetClass, depth);
    }
    // 方法调用等运行时才能确定的值无法静态求出
    return false;
  }

  /**
   * 引用求值失败的三种情形：lombok生成的常量没有初始值、数组常量链要继续展开、变量不是编译期常量
   */
  private static boolean collectIgnorePropertyByReference(PsiReferenceExpression referenceExpression,
      Set<String> ignorePropertyNameSet, PsiClass sourceClass, PsiClass targetClass, int depth) {
    if (referenceExpression.resolve() instanceof PsiVariable variable) {
      if (variable instanceof PsiField field) {
        String propertyName = PsiConstantUtil.evaluateFieldNameConstant(field);
        if (null != propertyName) {
          ignorePropertyNameSet.add(propertyName);
          return true;
        }
      }
      PsiExpression initializer = variable.getInitializer();
      // 初始值也求不出来时继续走名字兜底，不能直接判为失败
      if (null != initializer
          && collectIgnoreProperty(initializer, ignorePropertyNameSet, sourceClass, targetClass, depth + 1)) {
        return true;
      }
    }
    return collectIgnorePropertyByName(referenceExpression, ignorePropertyNameSet, sourceClass, targetClass);
  }

  /**
   * 求值和结构还原都失败时的兜底，仅当常量名恰是源类或目标类的属性名才采纳
   * 能求值的一律走不到这里，触发面很窄，用于兜住lombok插件未启用导致Fields内部类不存在、
   * 常量名被lombok.config改过等情形，同时避免把无关常量的名字当成属性名
   */
  private static boolean collectIgnorePropertyByName(PsiReferenceExpression referenceExpression,
      Set<String> ignorePropertyNameSet, PsiClass sourceClass, PsiClass targetClass) {
    String referenceName = referenceExpression.getReferenceName();
    if (null == referenceName) {
      return false;
    }
    if (!hasField(sourceClass, referenceName) && !hasField(targetClass, referenceName)) {
      return false;
    }
    ignorePropertyNameSet.add(referenceName);
    return true;
  }

  private static boolean hasField(PsiClass psiClass, String fieldName) {
    return null != psiClass && null != psiClass.findFieldByName(fieldName, true);
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

  void copyPropertiesReferenceScan(VirtualFile virtualFile, PsiManager manager) {
    try {
      if (null == virtualFile || !virtualFile.isValid()) {
        return;
      }
      PsiFile file = manager.findFile(virtualFile);
      if (file instanceof PsiJavaFile javaFile) {
        LOGGER.info("start scan file: " + virtualFile.getPath());
        // 重扫前先清除该文件的旧索引，否则索引只增不减，被删除的调用点会一直残留
        CopyPropertiesReferenceIndex.removeByFile(virtualFile.getUrl());
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
                  // 忽略属性没有解析完整时被忽略的属性会被当成已复制，宁可不建索引也不建指向错误位置的索引
                  if (!result.ignorePropertiesResolved()) {
                    return;
                  }

                  PsiClass sourceClass = result.sourceClass();
                  PsiClass targetClass = result.targetClass();

                  if (sourceClass == null || targetClass == null) {
                    return;
                  }

                  // 目标类的Setter和源类的Getter都是该属性复制的引用方
                  addAccessorReference(targetClass, result.targetPropertyMap(), true, methodCallExpression);
                  addAccessorReference(sourceClass, result.sourcePropertyMap(), false, methodCallExpression);
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

  /**
   * 建立单侧访问器到属性复制调用点的索引
   * setter为true时owner是目标类匹配Setter，为false时owner是源类匹配Getter
   */
  private static void addAccessorReference(PsiClass ownerClass, Map<String, Property> propertyMap, boolean setter, PsiMethodCallExpression methodCallExpression) {
    Set<String> samePropertyNameSet = propertyMap.values().stream()
        .filter(property -> SAME == property.getMark())
        .map(Property::getName)
        .collect(Collectors.toSet());
    if (samePropertyNameSet.isEmpty()) {
      return;
    }

    // 预筛只为避免逐方法遍历全部属性，matchesAccessor仍是最终判据
    Set<String> accessorNameSet = propertyMap.values().stream()
        .filter(property -> SAME == property.getMark())
        .flatMap(property -> accessorNameSet(property.getName(), property.getType(), setter).stream())
        .collect(Collectors.toSet());

    // getAllMethods包含父类声明的方法，父类声明子类未覆写的访问器同样是该属性复制的引用方
    for (PsiMethod method : ownerClass.getAllMethods()) {
      if (accessorNameSet.contains(method.getName()) && matchesAccessor(method, ownerClass, propertyMap, setter)) {
        CopyPropertiesReferenceIndex.addReference(method, methodCallExpression);
      }
    }

    // 添加使用了lombok的类的字段的引用
    boolean lombokAnnotated = setter ? hasSetterLombokAnnotation(ownerClass) : hasGetterLombokAnnotation(ownerClass);
    if (lombokAnnotated) {
      samePropertyNameSet.forEach(propertyName -> {
        PsiField field = ownerClass.findFieldByName(propertyName, true);
        if (null != field) {
          CopyPropertiesReferenceIndex.addReference(field, methodCallExpression);
        }
      });
    }
  }

  /**
   * @author: Aresxue
   * @description: 判断成员是否确实参与了该属性复制，建索引与查询共用同一判据避免两套判据分叉
   * @time: 2026-09-02 10:00:00
   * @params: [member, methodCallExpression] 目标成员，属性复制调用点
   * @return: boolean 该成员对应的属性确实会被复制时返回true
   */
  public static boolean matchesPropertyCopy(PsiMember member, PsiMethodCallExpression methodCallExpression) {
    if (null == member || null == methodCallExpression || !methodCallExpression.isValid() || !member.isValid()) {
      return false;
    }
    try {
      if (!isBeanCopyMethod(methodCallExpression.getMethodExpression().getCanonicalText())) {
        return false;
      }
      Result result = invoke(methodCallExpression);
      if (null == result) {
        return false;
      }
      // 与建索引同一判据，忽略属性没有解析完整时不能断言该属性会被复制
      if (!result.ignorePropertiesResolved()) {
        return false;
      }
      PsiClass sourceClass = result.sourceClass();
      PsiClass targetClass = result.targetClass();
      if (null == sourceClass || null == targetClass) {
        return false;
      }
      // 源类和目标类可能是同一个类，两侧都要判断命中任一即可
      return matchesAccessor(member, targetClass, result.targetPropertyMap(), true)
          || matchesAccessor(member, sourceClass, result.sourcePropertyMap(), false);
    } catch (ProcessCanceledException | IndexNotReadyException exception) {
      throw exception;
    } catch (Exception exception) {
      LOGGER.warn("match property copy fail:", exception);
      return false;
    }
  }

  private static boolean matchesAccessor(PsiMember member, PsiClass ownerClass, Map<String, Property> propertyMap, boolean setter) {
    // 成员必须声明在该类自身或其父类型上，挡住指针漂移到无关类的同名成员
    PsiClass containingClass = member.getContainingClass();
    if (null == containingClass || JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
      return false;
    }
    if (!InheritanceUtil.isInheritorOrSelf(ownerClass, containingClass, true)) {
      return false;
    }

    if (member instanceof PsiField field) {
      // 非lombok类的字段本身不是复制的引用方，其Getter/Setter才是
      boolean lombokAnnotated = setter ? hasSetterLombokAnnotation(ownerClass) : hasGetterLombokAnnotation(ownerClass);
      if (!lombokAnnotated) {
        return false;
      }
      Property property = propertyMap.get(field.getName());
      return null != property && SAME == property.getMark();
    }

    if (member instanceof PsiMethod method) {
      boolean validAccessor = setter ? validSetterMethod(method) : validGetterMethod(method);
      if (!validAccessor) {
        return false;
      }
      // 由属性名正向拼出期望的方法名，避免方法名反推属性名在getURL这类命名上失配
      String methodName = method.getName();
      return propertyMap.values().stream()
          .filter(property -> SAME == property.getMark())
          .anyMatch(property -> accessorNameSet(property.getName(), property.getType(), setter).contains(methodName)
              && accessorTypeMatch(method, property.getType(), setter));
    }

    return false;
  }

  /**
   * 属性对应的访问器方法名，boolean属性的Getter可能是is前缀如lombok为boolean active生成的isActive
   */
  private static Set<String> accessorNameSet(String propertyName, PsiType propertyType, boolean setter) {
    String suffix = CommonUtil.upperFirst(propertyName);
    if (setter) {
      return Set.of("set" + suffix);
    }
    if (isBooleanType(propertyType)) {
      return Set.of("get" + suffix, "is" + suffix);
    }
    return Set.of("get" + suffix);
  }

  /**
   * 由Getter方法名反查属性名，供Hutool的CopyOptions#setIgnoreProperties(Func1...)方法引用形式使用，
   * 仍由属性名正向拼出方法名再比对，避免直接裁剪方法名前缀在getURL这类命名上失配
   */
  public static String resolvePropertyNameByGetter(String getterName, PsiClass psiClass) {
    if (null == getterName || null == psiClass) {
      return null;
    }
    for (PsiField field : psiClass.getAllFields()) {
      String propertyName = field.getName();
      if (accessorNameSet(propertyName, field.getType(), false).contains(getterName)) {
        return propertyName;
      }
    }
    return null;
  }

  private static boolean isBooleanType(PsiType propertyType) {
    String canonicalText = propertyType.getCanonicalText();
    return "boolean".equals(canonicalText) || JAVA_LANG_BOOLEAN.equals(canonicalText);
  }

  private static boolean accessorTypeMatch(PsiMethod method, PsiType propertyType, boolean setter) {
    if (setter) {
      PsiType parameterType = method.getParameterList().getParameters()[0].getType();
      // 允许手写的宽化Setter如setAge(Object)，但拒绝类型不兼容的重载如Integer属性配setAge(String)
      return parameterType.isAssignableFrom(propertyType);
    }
    PsiType returnType = method.getReturnType();
    return null != returnType && propertyType.isAssignableFrom(returnType);
  }

  private static boolean hasGetterLombokAnnotation(PsiClass sourceClass) {
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

  private static boolean hasSetterLombokAnnotation(PsiClass targetClass) {
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


  private static boolean validSetterMethod(PsiMethod method) {
    // 非static返回为void入参个数为1的方法
    return method.getParameterList().getParametersCount() == 1
        && PsiTypes.voidType() == method.getReturnType()
        && !method.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean validGetterMethod(PsiMethod method) {
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
      // 文件已删除无法再判断是否目录，按文件和目录各清一次，未命中时是空操作
      String fileUrl = fileDeleteEvent.getFile().getUrl();
      CopyPropertiesReferenceIndex.removeByFile(fileUrl);
      CopyPropertiesReferenceIndex.removeByDirectory(fileUrl);
    } else if (event instanceof VFileMoveEvent fileMoveEvent) {
      // 移动后url变化，清掉旧url的映射再按新位置重扫
      VirtualFile virtualFile = fileMoveEvent.getFile();
      String oldUrl = fileMoveEvent.getOldParent().getUrl() + "/" + virtualFile.getName();
      CopyPropertiesReferenceIndex.removeByFile(oldUrl);
      // 移动的是目录时其下所有文件的旧url都要清除
      CopyPropertiesReferenceIndex.removeByDirectory(oldUrl);
      copyPropertiesReferenceScan(virtualFile, manager);
    } else if (event instanceof VFilePropertyChangeEvent filePropertyChangeEvent) {
      // 只有重命名会改变url，其余属性变更与索引无关
      if (VirtualFile.PROP_NAME.equals(filePropertyChangeEvent.getPropertyName())) {
        VirtualFile virtualFile = filePropertyChangeEvent.getFile();
        VirtualFile parent = virtualFile.getParent();
        if (null != parent) {
          CopyPropertiesReferenceIndex.removeByFile(parent.getUrl() + "/" + filePropertyChangeEvent.getOldValue());
        }
        copyPropertiesReferenceScan(virtualFile, manager);
      }
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
      String methodName = method.getName();
      if ((methodName.startsWith("set") && validSetterMethod(method))
          || ((methodName.startsWith("get") || methodName.startsWith("is")) && validGetterMethod(method))) {
        visible = isVisible(method);
      }
    } else if (element instanceof PsiField field) {
      visible = isVisible(field);
    }
    anActionEvent.getPresentation().setEnabledAndVisible(visible);
  }

  private boolean isVisible(PsiMember member) {
    // 语义复核要跑完整的解析，右键菜单每次都会触发，命中首个即返回避免放大延迟
    return CopyPropertiesReferenceIndex.hasReference(member);
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

  /**
   * 忽略属性没有解析完整时在展示上给出提示，否则据此生成的Setter代码可能把实际已被忽略的属性也复制过去
   */
  public static String buildIgnorePropertiesUnresolvedHtml(Result result) {
    if (result.ignorePropertiesResolved()) {
      return "";
    }
    return "<p style=\"font-size: 10px;\">⚠️ " + IGNORE_PROPERTIES_UNRESOLVED + "</p>";
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

  /**
   * ignorePropertiesResolved为false表示有忽略属性的值无法静态求出，ignoredProperties不完整，
   * 此时不能断言某个属性一定会被复制，据此抑制会误报的类型告警与引用索引写入
   */
  public record Result(PsiClass sourceClass, PsiClass targetClass,
                       Map<String, Property> sourcePropertyMap,
                       Map<String, Property> targetPropertyMap,
                       Map<String, Property> lowerCaseSourcePropertyMap,
                       Map<String, Property> lowerCaseTargetPropertyMap,
                       Set<String> ignoredProperties,
                       boolean ignorePropertiesResolved,
                       boolean sourceCollection) {

  }


}
