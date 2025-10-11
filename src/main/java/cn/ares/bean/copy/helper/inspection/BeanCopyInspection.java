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
package cn.ares.bean.copy.helper.inspection;

import cn.ares.bean.copy.helper.BeanCopyHelper;
import cn.ares.bean.copy.helper.BeanCopyHelper.Result;
import cn.ares.bean.copy.helper.model.Property;
import cn.ares.bean.copy.helper.resolve.BeanCopyResolve;
import cn.ares.bean.copy.helper.resolve.impl.ApacheBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.resolve.impl.SpringBeanCopyResolveImpl;
import cn.ares.bean.copy.helper.util.LocaleSupport;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

import static cn.ares.bean.copy.helper.constant.Mark.TYPE_NOT_MATCH;
import static com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;


/**
 * @author Aresxue
 */
public class BeanCopyInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> problems = new ArrayList<>();

    PsiCodeBlock codeBlock = method.getBody();
    if (null == codeBlock) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    codeBlock.accept(new JavaRecursiveElementVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression) {
        super.visitMethodCallExpression(methodCallExpression);
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        String canonicalText = methodExpression.getCanonicalText();
        if (BeanCopyHelper.isBeanCopyMethod(canonicalText)) {
          Result invoke = BeanCopyHelper.invoke(methodCallExpression);
          if (null == invoke) {
            return;
          }

          PsiClass sourceClass = invoke.sourceClass();
          PsiClass targetClass = invoke.targetClass();

          if (sourceClass == null || targetClass == null
              || JAVA_LANG_OBJECT.equals(sourceClass.getQualifiedName())
              || JAVA_LANG_OBJECT.equals(targetClass.getQualifiedName())) {
            return;
          }

          Map<String, Property> sourcePropertyMap = invoke.sourcePropertyMap();
          Map<String, Property> targetPropertyMap = invoke.targetPropertyMap();
          Map<String, Property> lowerCaseTargetPropertyMap = invoke.lowerCaseTargetPropertyMap();

          Set<String> commonPropertyNameSet = BeanCopyHelper.findCommonPropertyNameSet(invoke);
          if (commonPropertyNameSet.isEmpty()) {
            // 创建一个 ProblemDescriptor 来描述我们发现的问题
            ProblemDescriptor problem = manager.createProblemDescriptor(
                methodCallExpression,
                LocaleSupport.formatMessage("not.same.property", sourceClass.getName(), targetClass.getName()),
                true,
                WEAK_WARNING,
                isOnTheFly
            );
            problems.add(problem);
          }

          List<Property> typeNotMatchList = sourcePropertyMap.values().stream()
              .filter(property -> TYPE_NOT_MATCH.equals(property.getMark())).toList();

          if (!typeNotMatchList.isEmpty()) {
            String tips = typeNotMatchList.stream()
                .map(property -> property.toString() + "  " + property.getMark().getIcon() + BeanCopyResolve.getProperty(targetPropertyMap, lowerCaseTargetPropertyMap, property.getName()).toString())
                .collect(Collectors.joining("\n"));
            ProblemDescriptor problem = manager.createProblemDescriptor(
                methodCallExpression,
                LocaleSupport.formatMessage("type.not.match", sourceClass.getName(), targetClass.getName(), tips),
                true,
                WEAK_WARNING,
                isOnTheFly
            );
            problems.add(problem);
          }


          if (SpringBeanCopyResolveImpl.checkMethod(methodCallExpression)) {
            ProblemDescriptor problem = manager.createProblemDescriptor(
                methodCallExpression,
                LocaleSupport.formatMessage("spring.bean.copy.wrong.parameter.type"),
                true,
                WEAK_WARNING,
                isOnTheFly
            );
            problems.add(problem);
          }
        } else if (ApacheBeanCopyResolveImpl.isBeanCopyPropertyMethod(canonicalText)) {
          PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
          PsiClass targetClass = PsiUtil.resolveClassInType(expressions[0].getType());
          if (null != targetClass) {
            if (expressions[1] instanceof PsiLiteralExpression literalExpression) {
              String fieldName = literalExpression.getText().replace("\"", "");
              if (Arrays.stream(targetClass.getAllFields()).noneMatch(field -> field.getName().equals(fieldName))) {
                ProblemDescriptor problem = manager.createProblemDescriptor(
                    methodCallExpression,
                    LocaleSupport.formatMessage("apache.bean.copy.field.not.exist", targetClass.getName(), fieldName),
                    true,
                    WEAK_WARNING,
                    isOnTheFly
                );
                problems.add(problem);
              }
            }
          }
        }
      }
    });

    return problems.toArray(new ProblemDescriptor[0]);
  }
  
}
