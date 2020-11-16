package com.singleman.okhttp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * createTime: 2020/8/25.8:55
 * updateTime: 2020/8/25.8:55
 * author: singleMan.
 * desc:
 */
public class Utils {

    /**
     * 获取非静态 DeclaredFields
     * @param clazz
     * @return
     */
    public static Field[]  getNotStaticDeclaredFields(Class clazz){
        List<Field> notStaticList = new ArrayList<>();
        try {
            Field[] declaredFields = clazz.getDeclaredFields();
            Field.setAccessible(declaredFields,true);
            for(Field field : declaredFields){
                if(Modifier.isStatic(field.getModifiers())){
                    continue;
                }
                notStaticList.add(field);
            }

        }catch (Exception e){
        }
        if(notStaticList.isEmpty()){
            return null;
        }
        return notStaticList.toArray(new Field[]{});
    }

    /**
     * 获取字段的泛型
     * @param field
     * @return
     */
    public static Class getGenericClass(Field field){
        try {
            Type type = field.getGenericType(); // 关键的地方，如果是List类型，得到其Generic的类型
            if(type == null) return null;
            if(type instanceof ParameterizedType) // 【3】如果是泛型参数的类型
            {
                ParameterizedType pt = (ParameterizedType) type;
                Class genericClazz = (Class)pt.getActualTypeArguments()[0]; //【4】 得到泛型里的class类型对象。
                return genericClazz;
            }
        }catch (Exception e){}
        return null;
    }


    /**
     * 获取接口的实现类
     * @param all
     * @param interfaceClazz
     * @return
     */
    public static Class getClazzByInterface(List<Class> all,Class interfaceClazz){
        if(!interfaceClazz.isInterface()){
            return null;
        }
        for(Class clazz : all){
            if(clazz.isInterface()){
                continue;
            }
            if(interfaceClazz.isAssignableFrom(clazz)){
                return clazz;
            }
        }
        return null;
    }

    /**
     * 获取接口的实现类列表
     * @param all
     * @param interfaceClazz
     * @return
     */
    public static List<Class> getInterfaceImpls(List<Class> all,Class interfaceClazz){
        List<Class> implList = new ArrayList<>();
        if(!interfaceClazz.isInterface()){
            return null;
        }
        for(Class clazz : all){
            if(clazz.isInterface()){
                continue;
            }
            if(interfaceClazz.isAssignableFrom(clazz)){
                implList.add(clazz);
            }
        }
        return implList;
    }

    /**
     * 获取method 的参数的数量
     * @param method
     * @return
     */
    public static int getParameterCount(Method method){
        Class<?>[] parameterTypes = method.getParameterTypes();
        if(null == parameterTypes){
            return 0;
        }
        return parameterTypes.length;
    }
}
