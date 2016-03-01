/*
**        DroidPlugin Project
**
** Copyright(c) 2015 Andy Zhang <zhangyong232@gmail.com>
**
** This file is part of DroidPlugin.
**
** DroidPlugin is free software: you can redistribute it and/or
** modify it under the terms of the GNU Lesser General Public
** License as published by the Free Software Foundation, either
** version 3 of the License, or (at your option) any later version.
**
** DroidPlugin is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
** Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public
** License along with DroidPlugin.  If not, see <http://www.gnu.org/licenses/lgpl.txt>
**
**/

package com.morgoo.droidplugin.reflect;

import android.text.TextUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;


/**
 * Created by Andy Zhang(zhangyong232@gmail.com)ClassUtils on 2015/3/25.
 */
public class FieldUtils {

    private static Map<String, Field> sFieldCache = new HashMap<String, Field>();

    private static String getKey(Class<?> cls, String fieldName) {
        StringBuilder sb = new StringBuilder();
        sb.append(cls.toString()).append("#").append(fieldName);
        return sb.toString();
    }

    /**
     * 从类中获取原始类型的成员
     * cls：成员所属类的类型
     * filedName：成员名
     * forceAccess：？执行条件
     */
    private static Field getField(Class<?> cls, String fieldName, final boolean forceAccess) {
        Validate.isTrue(cls != null, "The class must not be null");
        Validate.isTrue(!TextUtils.isEmpty(fieldName), "The field name must not be blank/empty");
        //生成由所属类类型和成员名组成的key
        String key = getKey(cls, fieldName);
        //先在在缓存中找这个成员的代理有没有被实现过
        Field cachedField;
        synchronized (sFieldCache) {
            cachedField = sFieldCache.get(key);
        }
        //缓存中有直接返回
        if (cachedField != null) {
            if (forceAccess && !cachedField.isAccessible()) {
                cachedField.setAccessible(true);
            }
            return cachedField;
        }

        // check up the superclass hierarchy
        for (Class<?> acls = cls; acls != null; acls = acls.getSuperclass()) {
            try {
                //在类中得到成员
                final Field field = acls.getDeclaredField(fieldName);
                // getDeclaredField checks for non-public scopes as well
                // and it returns accurate results
                //如果是public类型返回成员，不可访问则继续从其超类中获取
                if (!Modifier.isPublic(field.getModifiers())) {
                    if (forceAccess) {
                        field.setAccessible(true);
                    } else {
                        continue;
                    }
                }
                //将得到的成员放入缓存
                synchronized (sFieldCache) {
                    sFieldCache.put(key, field);
                }
                return field;
            } catch (final NoSuchFieldException ex) { // NOPMD
                // ignore
            }
        }
        // check the public interface case. This must be manually searched for
        // incase there is a public supersuperclass field hidden by a private/package
        // superclass field.
        //超类中没有找到再从其所有接口中获取
        Field match = null;
        for (final Class<?> class1 : Utils.getAllInterfaces(cls)) {
            try {
                final Field test = class1.getField(fieldName);
                Validate.isTrue(match == null, "Reference to field %s is ambiguous relative to %s"
                        + "; a matching field exists on two or more implemented interfaces.", fieldName, cls);
                match = test;
            } catch (final NoSuchFieldException ex) { // NOPMD
                // ignore
            }
        }
        //加入缓存
        synchronized (sFieldCache) {
            sFieldCache.put(key, match);
        }
        //返回成员
        return match;
    }

    public static Object readField(final Field field, final Object target, final boolean forceAccess) throws IllegalAccessException {
        //为空抛异常
        Validate.isTrue(field != null, "The field must not be null");
        //处理可访问性
        if (forceAccess && !field.isAccessible()) {
            field.setAccessible(true);
        } else {
            MemberUtils.setAccessibleWorkaround(field);
        }
        //得到对象实例中成员的值
        return field.get(target);
    }

    /**
     * 替换原有的成员为伪装之后的成员
     * file：需要替换的字段
     * target：被代理的对象，如果要替换的是静态字段，可以为null
     * value：伪装后的字段
     * forceAccess：是否可访问
     */
    public static void writeField(final Field field, final Object target, final Object value, final boolean forceAccess)
            throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        //可访问性处理
        if (forceAccess && !field.isAccessible()) {
            field.setAccessible(true);
        } else {
            MemberUtils.setAccessibleWorkaround(field);
        }
        //将target实例中 field 对象表示的原字段设置为伪装之后的value
        field.set(target, value);
    }

    //得到对象（target）中字段（feiled）的值
    public static Object readField(final Field field, final Object target) throws IllegalAccessException {
        return readField(field, target, true);
    }

    //根据Class反射字段
    public static Field getField(final Class<?> cls, final String fieldName) {
        return getField(cls, fieldName, true);
    }


    /**
     * 读取指定对象中成员的值
     * target：成员所属的类
     * filedName：成员名
     */
    public static Object readField(final Object target, final String fieldName) throws IllegalAccessException {
        //如果为null抛出异常提示
        Validate.isTrue(target != null, "target object must not be null");
        //取出成员
        final Class<?> cls = target.getClass();
        final Field field = getField(cls, fieldName, true);
        //如果为null抛出异常提示
        Validate.isTrue(field != null, "Cannot locate field %s on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        return readField(field, target, false);
    }

    public static Object readField(final Object target, final String fieldName, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        final Class<?> cls = target.getClass();
        final Field field = getField(cls, fieldName, forceAccess);
        Validate.isTrue(field != null, "Cannot locate field %s on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        return readField(field, target, forceAccess);
    }

    //将实例中的成员替换为伪装后的成员
    public static void writeField(final Object target, final String fieldName, final Object value) throws IllegalAccessException {
        writeField(target, fieldName, value, true);
    }

    /**
     * 将代理结果替换原有API
     * target 需要代理的对象
     * fieldName 被代理的成员名
     * value 被为装之后的成员对象
     * （可以理解为 将target 中的 fieldName 字段替换为 value ）
     */
    public static void writeField(final Object target, final String fieldName, final Object value, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        //得到被代理对象的所属类的Class
        final Class<?> cls = target.getClass();
        //得到被代理对象的原始状态
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate declared field %s.%s", cls.getName(), fieldName);
        // already forced access above, don't repeat it here:
        writeField(field, target, value, forceAccess);
    }

    /**
     * 将代理结果替换原有API
     * field 字段信息
     * target 需要代理的对象
     * value 被为装之后的成员对象
     * （可以理解为 将target 中的 field 字段替换为 value ）
     */
    public static void writeField(final Field field, final Object target, final Object value) throws IllegalAccessException {
        writeField(field, target, value, true);
    }

    //读取静态成员
    public static Object readStaticField(final Field field, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        Validate.isTrue(Modifier.isStatic(field.getModifiers()), "The field '%s' is not static", field.getName());
        return readField(field, (Object) null, forceAccess);
    }

    //读取静态成员
    public static Object readStaticField(final Class<?> cls, final String fieldName) throws IllegalAccessException {
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate field '%s' on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        return readStaticField(field, true);
    }

    //替换实例的静态成员
    public static void writeStaticField(final Field field, final Object value, final boolean forceAccess) throws IllegalAccessException {
        Validate.isTrue(field != null, "The field must not be null");
        Validate.isTrue(Modifier.isStatic(field.getModifiers()), "The field %s.%s is not static", field.getDeclaringClass().getName(),
                field.getName());
        writeField(field, (Object) null, value, forceAccess);
    }

    /**
     * 替换实例的静态成员
     * cls：被代理对象的Class
     * fieldName：需要替换的字段名
     * value：伪装后的字段
     */
    public static void writeStaticField(final Class<?> cls, final String fieldName, final Object value) throws IllegalAccessException {
        //先获取成员
        final Field field = getField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate field %s on %s", fieldName, cls);
        // already forced access above, don't repeat it here:
        //替换
        writeStaticField(field, value, true);
    }

    public static Field getDeclaredField(final Class<?> cls, final String fieldName, final boolean forceAccess) {
        Validate.isTrue(cls != null, "The class must not be null");
        Validate.isTrue(!TextUtils.isEmpty(fieldName), "The field name must not be blank/empty");
        try {
            // only consider the specified class by using getDeclaredField()
            final Field field = cls.getDeclaredField(fieldName);
            if (!MemberUtils.isAccessible(field)) {
                if (forceAccess) {
                    field.setAccessible(true);
                } else {
                    return null;
                }
            }
            return field;
        } catch (final NoSuchFieldException e) { // NOPMD
            // ignore
        }
        return null;
    }

    public static void writeDeclaredField(final Object target, final String fieldName, final Object value) throws IllegalAccessException {
        Validate.isTrue(target != null, "target object must not be null");
        final Class<?> cls = target.getClass();
        final Field field = getDeclaredField(cls, fieldName, true);
        Validate.isTrue(field != null, "Cannot locate declared field %s.%s", cls.getName(), fieldName);
        // already forced access above, don't repeat it here:
        writeField(field, target, value, false);
    }


}
