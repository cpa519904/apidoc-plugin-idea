package com.suiyiwen.plugin.idea.apidoc.utils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.suiyiwen.plugin.idea.apidoc.bean.dialog.AbstractExampleBean;
import com.suiyiwen.plugin.idea.apidoc.bean.dialog.FieldBean;
import com.suiyiwen.plugin.idea.apidoc.bean.dialog.ParamBean;
import com.suiyiwen.plugin.idea.apidoc.bean.dialog.ResultBean;
import com.suiyiwen.plugin.idea.apidoc.component.ApiDocSettings;
import com.suiyiwen.plugin.idea.apidoc.constant.ApiDocConstant;
import com.suiyiwen.plugin.idea.apidoc.enums.AnnotationClass;
import com.suiyiwen.plugin.idea.apidoc.enums.HttpRequestMethod;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dongxuanliang252
 * @date 2019-01-02 13:47
 */
public enum NewDialogModelParseUtils {

    INSTANCE;

    public String parseRequestMethod(PsiMethod element) {
        HttpRequestMethod requestMethod = PsiHttpUtils.INSTANCE.getHttpRequestMethod(element);
        if (requestMethod == null) {
            return null;
        }
        return requestMethod.name();
    }

    public String parseRequestUrl(PsiMethod element) {
        return PsiHttpUtils.INSTANCE.getHttpRequestUrl(element);
    }

    public String parseRequestTitle(PsiMethod element) {
        if (element == null) {
            return null;
        }
        return PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY,
                PsiFormatUtil.SHOW_NAME, PsiFormatUtil.SHOW_NAME);
    }

    public String parseApiGroup(PsiMethod element) {
        if (element == null) {
            return null;
        }
        return PsiFormatUtil.formatClass(element.getContainingClass(), PsiFormatUtil.SHOW_NAME);
    }

    public String parseApiName(PsiMethod element) {
        if (element == null) {
            return null;
        }
        return PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY,
                PsiFormatUtil.SHOW_NAME, PsiFormatUtil.SHOW_NAME);
    }

    public ParamBean parseRequestParameter(PsiMethod element) {
        if (element == null) {
            return null;
        }
        List<PsiParameter> requestParameterTypeList = new ArrayList<>();
        for (PsiParameter psiParameter : element.getParameterList().getParameters()) {
            if (isRequestParameter(psiParameter)) {
                requestParameterTypeList.add(psiParameter);
            }
        }
        return parseParamBean(ApiDocConstant.TAG_REQUEST_PARAM_GROUP_TITLE, requestParameterTypeList);
    }

    private boolean isRequestParameter(PsiParameter psiParameter) {
        if (psiParameter == null) {
            return false;
        }
        if (PsiAnnotationUtils.INSTANCE.hasAnnotation(psiParameter.getModifierList(), AnnotationClass.REQUEST_PARAM.getClassName())) {
            return true;
        }
        if (ArrayUtils.isEmpty(psiParameter.getModifierList().getAnnotations())) {
            if (PsiTypesUtils.INSTANCE.isAssignableFrom(ApiDocConstant.HTTP_SERVLET_REQUEST_CLASS_NAME, psiParameter.getType(), psiParameter)) {
                return false;
            }
            if (PsiTypesUtils.INSTANCE.isAssignableFrom(ApiDocConstant.HTTP_SERVLET_RESPONSE_CLASS_NAME, psiParameter.getType(), psiParameter)) {
                return false;
            }
            return true;
        }
        if (PsiAnnotationUtils.INSTANCE.hasAnnotation(psiParameter.getModifierList(), AnnotationClass.REQUEST_BODY.getClassName())) {
            return false;
        }
        return true;
    }

    public ParamBean parseRequestBody(PsiMethod element) {
        if (element == null) {
            return null;
        }
        PsiType requestBodyPsiType = null;
        for (PsiParameter psiParameter : element.getParameterList().getParameters()) {
            if (PsiAnnotationUtils.INSTANCE.hasAnnotation(psiParameter.getModifierList(), AnnotationClass.REQUEST_BODY.getClassName())) {
                requestBodyPsiType = psiParameter.getType();
                break;
            }
        }
        return parseBodyExampleBean(ApiDocConstant.TAG_REQUEST_BODY_GROUP_TITLE, requestBodyPsiType, ParamBean.class, element);
    }

    public ResultBean parseResponseBody(PsiMethod element) {
        if (element == null) {
            return null;
        }
        return parseBodyExampleBean(ApiDocConstant.TAG_RESPONSE_BODY_GROUP_TITLE, element.getReturnType(), ResultBean.class, element);
    }

    private <T extends AbstractExampleBean> T parseBodyExampleBean(String title, PsiType psiType, Class<T> cls, @NotNull PsiElement context) {
        T exampleBean = ClassUtils.INSTANCE.newInstance(cls);
        if (psiType == null) {
            return null;
        }
        FieldBean rootFieldBean = new FieldBean();
        String defaultRootName = exampleBean instanceof ResultBean ? ApiDocConstant.STRING_RESPONSE : ApiDocConstant.STRING_REQUEST_BODY;
        rootFieldBean.setName(ApiDocCommonUtils.INSTANCE.getSuitableFieldName(defaultRootName, context));
        rootFieldBean.setType(PsiTypesUtils.INSTANCE.getFieldType(psiType, context).name());
        rootFieldBean.setPsiType(psiType);
        if (PsiTypesUtils.INSTANCE.isEnum(psiType, context)) {
            rootFieldBean.setDescription(PsiTypesUtils.INSTANCE.generateEnumDescription(psiType, context));
        }
        List<FieldBean> innerChildFieldList = parseRefFieldBeanList(psiType, context);
        List<FieldBean> retChildFieldList = new ArrayList<>();
        if (PsiTypesUtils.INSTANCE.isIterable(psiType, context)) {
            rootFieldBean.setChildFieldList(innerChildFieldList);
            retChildFieldList.add(rootFieldBean);
        } else if (CollectionUtils.isEmpty(innerChildFieldList)) {
            retChildFieldList.add(rootFieldBean);
        } else {
            retChildFieldList = innerChildFieldList;
        }
        exampleBean.setTitle(title);
        exampleBean.setFieldList(retChildFieldList);
        return exampleBean;
    }

    private ParamBean parseParamBean(String title, List<PsiParameter> elementList) {
        ParamBean exampleBean = new ParamBean();
        if (CollectionUtils.isEmpty(elementList)) {
            return null;
        }
        exampleBean.setTitle(title);
        List<FieldBean> allFieldBeanList = new ArrayList<>();
        for (PsiParameter psiParameter : elementList) {
            List<FieldBean> innerChildFieldList = parseRefFieldBeanList(psiParameter.getType(), psiParameter);
            if (CollectionUtils.isNotEmpty(innerChildFieldList)) {
                allFieldBeanList.addAll(innerChildFieldList);
            } else {
                PsiType psiType = psiParameter.getType();
                FieldBean fieldBean = new FieldBean();
                fieldBean.setName(ApiDocCommonUtils.INSTANCE.getSuitableFieldName(psiParameter.getName(), psiParameter));
                fieldBean.setType(PsiTypesUtils.INSTANCE.getFieldType(psiType, psiParameter).name());
                fieldBean.setPsiType(psiType);
                if (PsiTypesUtils.INSTANCE.isEnum(psiType, psiParameter)) {
                    fieldBean.setDescription(PsiTypesUtils.INSTANCE.generateEnumDescription(psiType, psiParameter));
                }
                allFieldBeanList.add(fieldBean);
            }
        }
        exampleBean.setFieldList(allFieldBeanList);
        return exampleBean;
    }

    private List<FieldBean> parseRefFieldBeanList(PsiType psiType, @NotNull PsiElement context) {
        return parseRefFieldBeanList(psiType, ApiDocConstant.OBJECT_EXTRACT_DEPTH_START, context);
    }

    private List<FieldBean> parseRefFieldBeanList(PsiType psiType, int depth, @NotNull PsiElement context) {
        boolean isFirstDepth = ApiDocConstant.OBJECT_EXTRACT_DEPTH_START == depth;
        if (isFirstDepth) {
            depth++;
        }
        List<FieldBean> innerChildFieldList = new ArrayList<>();
        //boxedType, String, enum, map, primitiveType,number,Character,CharSequence,Boolean,Date
        if (PsiTypesUtils.INSTANCE.isExtractEndPsiType(psiType, context)) {
            //不处理
        } else if (PsiTypesUtils.INSTANCE.isIterable(psiType, context)) {
            PsiType[] genericPsiTypes = ((PsiClassType) psiType).getParameters();
            if (ArrayUtils.isNotEmpty(genericPsiTypes)) {
                innerChildFieldList = parseRefFieldBeanList(genericPsiTypes[0], depth, context);
            }
        } else if (psiType instanceof PsiClassType) {
            innerChildFieldList = parsePsiClassType(psiType, depth, context);
        } else if (psiType instanceof PsiArrayType) {
            PsiArrayType arrayType = (PsiArrayType) psiType;
            PsiType componentType = arrayType.getComponentType();
            innerChildFieldList = parseRefFieldBeanList(componentType, depth, context);
        }
        if (CollectionUtils.isNotEmpty(innerChildFieldList)) {
            return innerChildFieldList;
        }
        return null;
    }

    private FieldBean parseFieldBean(PsiField psiField, PsiSubstitutor psiSubstitutor, int depth, @NotNull PsiElement context) {
        FieldBean fieldBean = new FieldBean();
        fieldBean.setName(ApiDocCommonUtils.INSTANCE.getSuitableFieldName(psiField.getName(), context));
        PsiType psiType = PsiTypesUtils.INSTANCE.createGenericPsiType(psiField.getType(), psiSubstitutor);
        fieldBean.setType(PsiTypesUtils.INSTANCE.getFieldType(psiType, context).name());
        fieldBean.setPsiType(psiType);
        fieldBean.setDescription(PsiFieldUtils.INSTANCE.getFieldDescription(psiField));
        if (StringUtils.isBlank(fieldBean.getDescription()) && PsiTypesUtils.INSTANCE.isEnum(psiType, context)) {
            fieldBean.setDescription(PsiTypesUtils.INSTANCE.generateEnumDescription(psiType, context));
        }
        if (depth >= ApiDocSettings.getInstance(context.getProject()).getDepth()) {
            return fieldBean;
        }
        List<FieldBean> childFieldList = parseRefFieldBeanList(psiType, depth + 1, context);
        if (CollectionUtils.isNotEmpty(childFieldList)) {
            fieldBean.setChildFieldList(childFieldList);
        }
        return fieldBean;
    }

    private List<FieldBean> parsePsiClassType(PsiType psiType, int depth, @NotNull PsiElement context) {
        List<FieldBean> retList = new ArrayList<>();
        PsiClass psiClass = PsiTypesUtil.getPsiClass(psiType);
        PsiSubstitutor psiSubstitutor = ((PsiClassType) psiType).resolveGenerics().getSubstitutor();
        for (PsiField psiField : psiClass.getFields()) {
            if (PsiFieldUtils.INSTANCE.isVariable(psiField)) {
                retList.add(parseFieldBean(psiField, psiSubstitutor, depth, context));
            }
        }
        PsiType[] superTypes = psiType.getSuperTypes();
        if (ArrayUtils.isNotEmpty(superTypes)) {
            for (PsiType superType : superTypes) {
                if (superType instanceof PsiClassType) {
                    PsiClass superPsiClass = ((PsiClassType) superType).resolve();
                    if (superPsiClass.isInterface()) {
                        continue;
                    }
                }
                List<FieldBean> superFieldBeanList = parsePsiClassType(PsiTypesUtils.INSTANCE.createGenericPsiType(superType, psiSubstitutor), depth, context);
                if (CollectionUtils.isNotEmpty(superFieldBeanList)) {
                    retList.addAll(superFieldBeanList);
                }
            }
        }
        return retList;
    }
}
