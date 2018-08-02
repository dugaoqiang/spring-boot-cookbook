package com.tangcheng.learning.service.lock.key.impl;

import com.tangcheng.learning.service.lock.annotation.DistributedLock;
import com.tangcheng.learning.service.lock.annotation.KeyParam;
import com.tangcheng.learning.service.lock.key.KeyGenerator;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * spring-boot-cookbook
 * 分布式锁的Key的默认生成器
 *
 * @author tangcheng
 * @date 6/16/2018 11:52 PM
 */
@Component
public class DefaultKeyGenerator implements KeyGenerator {

    /**
     * 如果对象中嵌套对象，则取不到@KeyParam的字段
     *
     * @param proceedingJoinPoint
     * @return
     */
    @Override
    public String getLockKey(ProceedingJoinPoint proceedingJoinPoint) {
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);
        Parameter[] parameters = method.getParameters();
        Object[] args = proceedingJoinPoint.getArgs();
        StringBuilder builder = new StringBuilder();

        String delimiter = distributedLock.delimiter();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter == null) {
                continue;
            }
            KeyParam annotation = parameter.getAnnotation(KeyParam.class);
            if (annotation == null) {
                //如果入参没有加@KeyParam，则看对象里面的字段有没有加@KeyParam
                Object fieldObject = args[i];
                if (fieldObject == null) {
                    continue;
                }
                Class<?> parameterClass = fieldObject.getClass();
                findKeyParamAndAddFillRedisKey(builder, delimiter, fieldObject, parameterClass);
                //查找父类的字段
                continue;
            }
            addRedisKey(builder, parameter.getName(), annotation.value(), delimiter, args[i]);
        }
        return distributedLock.prefix() + builder.toString();
    }

    /**
     * 查找父类中有没有使用@KeyParam
     *
     * @param builder
     * @param delimiter
     * @param fieldObject
     * @param parameterClass
     */
    private void findKeyParamAndAddFillRedisKey(StringBuilder builder, String delimiter, Object fieldObject, Class<?> parameterClass) {
        KeyParam annotation;
        Field[] declaredFields = parameterClass.getDeclaredFields();
        for (Field field : declaredFields) {
            annotation = field.getAnnotation(KeyParam.class);
            if (annotation == null) {
                continue;
            }
            field.setAccessible(true);
            Object fieldValue = ReflectionUtils.getField(field, fieldObject);
            addRedisKey(builder, field.getName(), annotation.value(), delimiter, fieldValue);
        }
        Class<?> superclass = parameterClass.getSuperclass();
        if (superclass != null && !superclass.isAssignableFrom(Object.class)) {
            findKeyParamAndAddFillRedisKey(builder, delimiter, fieldObject, superclass);
        }
    }

    /**
     * 将注解中key-value拼装到redis-key
     *
     * @param builder
     * @param fieldName
     * @param annotationValue
     * @param delimiter
     * @param fileValue
     */
    private void addRedisKey(StringBuilder builder, String fieldName, String annotationValue, String delimiter, Object fileValue) {
        if (StringUtils.isBlank(annotationValue)) {
            annotationValue = fieldName;
        }
        builder.append(delimiter)
                .append(annotationValue)
                .append(delimiter)
                .append(fileValue);
    }


}