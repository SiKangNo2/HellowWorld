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


import com.morgoo.helper.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by Andy Zhang(zhangyong232@gmail.com)ClassUtils on 2015/3/25.
 */
public class MethodUtils {

    private static Map<String, Method> sMethodCache = new HashMap<String, Method>();

    //以“类名#方法名#参数类型”的方式生成唯一key（用作缓存中的key）
    private static String getKey(final Class<?> cls, final String methodName, final Class<?>... parameterTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(cls.toString()).append("#").append(methodName);
        if (parameterTypes != null && parameterTypes.length > 0) {
            for (Class<?> parameterType : parameterTypes) {
                sb.append(parameterType.toString()).append("#");
            }
        } else {
            sb.append(Void.class.toString());
        }
        return sb.toString();
    }


    private static Method getAccessibleMethodFromSuperclass(final Class<?> cls,
                                                            final String methodName, final Class<?>... parameterTypes) {

        //获取超类
        Class<?> parentClass = cls.getSuperclass();
        while (parentClass != null) {
            if (Modifier.isPublic(parentClass.getModifiers())) {
                try {
                    //在超类中查找方法
                    return parentClass.getMethod(methodName, parameterTypes);
                } catch (final NoSuchMethodException e) {
                    return null;
                }
            }
            //继续向上级找
            parentClass = parentClass.getSuperclass();
        }
        return null;
    }

    //递归向所有实现或间接实现的接口中查找方法
    private static Method getAccessibleMethodFromInterfaceNest(Class<?> cls,
                                                               final String methodName, final Class<?>... parameterTypes) {
        //获取超类
        // Search up the superclass chain
        for (; cls != null; cls = cls.getSuperclass()) {
            //获取超类实现的所有接口
            // Check the implemented interfaces of the parent class
            final Class<?>[] interfaces = cls.getInterfaces();
            //遍历接口
            for (int i = 0; i < interfaces.length; i++) {
                // Is this interface public?
                //如果修饰符不为public，放弃查找，跳出本次循环
                if (!Modifier.isPublic(interfaces[i].getModifiers())) {
                    continue;
                }
                //在接口中查找该方法，并返回结果
                // Does the method exist on this interface?
                try {
                    return interfaces[i].getDeclaredMethod(methodName,
                            parameterTypes);
                } catch (final NoSuchMethodException e) { // NOPMD
                    /*
                     * Swallow, if no method is found after the loop then this
                     * method returns null.
                     */
                }
                //找到方法的可用版本则返回，否则继续向父级查找
                // Recursively check our parent interfaces
                Method method = getAccessibleMethodFromInterfaceNest(interfaces[i],
                        methodName, parameterTypes);
                if (method != null) {
                    return method;
                }
            }
        }
        return null;
    }

    //获取可用的方法版本
    private static Method getAccessibleMethod(Method method) {
        //如果方法不可访问返回null
        if (!MemberUtils.isAccessible(method)) {
            return null;
        }
        //如果可访问且其所属类修饰符为public，返回方法
        // If the declaring class is public, we are done
        final Class<?> cls = method.getDeclaringClass();
        if (Modifier.isPublic(cls.getModifiers())) {
            return method;
        }
        //如果方法的所属类的修饰符不为Public
        //在其实现或间接实现的接口中查找此方法
        final String methodName = method.getName();
        final Class<?>[] parameterTypes = method.getParameterTypes();

        // Check the implemented interfaces and subinterfaces
        method = getAccessibleMethodFromInterfaceNest(cls, methodName,
                parameterTypes);

        //如果没有找到，则在其 实现或间接实现的超类中找
        // Check the superclass chain
        if (method == null) {
            method = getAccessibleMethodFromSuperclass(cls, methodName,
                    parameterTypes);
        }
        //返回查找结果
        return method;
    }

    public static Method getAccessibleMethod(final Class<?> cls, final String methodName,
                                             final Class<?>... parameterTypes) throws NoSuchMethodException {
        String key = getKey(cls, methodName, parameterTypes);
        Method method;
        synchronized (sMethodCache) {
            method = sMethodCache.get(key);
        }
        if (method != null) {
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method;
        }

        Method accessibleMethod = getAccessibleMethod(cls.getMethod(methodName,
                parameterTypes));
        synchronized (sMethodCache) {
            sMethodCache.put(key, accessibleMethod);
        }
        return accessibleMethod;

    }

    /**
     * 得到匹配的方法
     * cls：方法所属类的类型
     * methodName：方法名
     * parameterTypes：方法的参数类型（array）
     */
    private static Method getMatchingAccessibleMethod(final Class<?> cls,
                                                      final String methodName, final Class<?>... parameterTypes) {
        //生成方法的唯一key
        String key = getKey(cls, methodName, parameterTypes);
        //创建一个方法对象
        Method cachedMethod;
        //先在缓存中找此方法
        synchronized (sMethodCache) {
            cachedMethod = sMethodCache.get(key);
        }
        //缓存中有，则判断此方法的可访问性，若不可访问修改为可访问，然后返回方法
        if (cachedMethod != null) {
            if (!cachedMethod.isAccessible()) {
                cachedMethod.setAccessible(true);
            }
            return cachedMethod;
        }
        try {
            //如果缓存中没有，创建新的方法对象
            final Method method = cls.getMethod(methodName, parameterTypes);
            //设置可访问
            MemberUtils.setAccessibleWorkaround(method);
            //将方法对象加入缓存
            synchronized (sMethodCache) {
                sMethodCache.put(key, method);
            }
            //返回方法
            return method;
        } catch (final NoSuchMethodException e) { // NOPMD - Swallow the exception
        }
        // search through all methods
        //获得类中所有方法
        Method bestMatch = null;
        final Method[] methods = cls.getMethods();
        for (final Method method : methods) {
            //如果和需要的方法名相同且参数类型相同
            // compare name and parameters
            if (method.getName().equals(methodName) && MemberUtils.isAssignable(parameterTypes, method.getParameterTypes(), true)) {
                // get accessible version of method
                /**
                 * 得到可访问的方法版本（遍历其实现或间接实现的超类、接口，找到修饰符为public的方法实现）
                 * 优先级：现在判断当前的方法是否可访问，，然后在接口中找，之后再超类中找，找到则返回，找不到返回Null
                 * */
                final Method accessibleMethod = getAccessibleMethod(method);
                if (accessibleMethod != null && (bestMatch == null || MemberUtils.compareParameterTypes(
                        accessibleMethod.getParameterTypes(),
                        bestMatch.getParameterTypes(),
                        parameterTypes) < 0)) {
                    bestMatch = accessibleMethod;
                }
            }
        }
        //处理方法的可访问性
        if (bestMatch != null) {
            MemberUtils.setAccessibleWorkaround(bestMatch);
        }
        //将方法加入缓存
        synchronized (sMethodCache) {
            sMethodCache.put(key, bestMatch);
        }
        //返回方法对象
        return bestMatch;
    }

    public static Object invokeMethod(final Object object, final String methodName,
                                      Object[] args, Class<?>[] parameterTypes)
            throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        parameterTypes = Utils.nullToEmpty(parameterTypes);
        args = Utils.nullToEmpty(args);
        final Method method = getMatchingAccessibleMethod(object.getClass(),
                methodName, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException("No such accessible method: "
                    + methodName + "() on object: "
                    + object.getClass().getName());
        }
        return method.invoke(object, args);
    }

    /**
     * 通过反射调用静态方法
     * clazz：方法所属的类的类型
     * method：方法名
     * parameterTypes：方法参数类型
     * args：方法参数的值
     */
    public static Object invokeStaticMethod(final Class clazz, final String methodName,
                                            Object[] args, Class<?>[] parameterTypes)
            throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        //保证参数类型合法性
        parameterTypes = Utils.nullToEmpty(parameterTypes);
        //保证参数值的不为null
        args = Utils.nullToEmpty(args);
        //得到方法对象
        final Method method = getMatchingAccessibleMethod(clazz,
                methodName, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException("No such accessible method: "
                    + methodName + "() on object: "
                    + clazz.getName());
        }
        //执行方法返回结果，参数1：调用此方法的对象（执行静态方法时可以为null），参数2：方法的参数
        return method.invoke(null, args);
    }

    /**
     * 通过反射调用静态方法
     * clazz：方法所属类的类型
     * method：方法名
     * args：方法参数。数量不限
     */
    public static Object invokeStaticMethod(final Class clazz, final String methodName,
                                            Object... args) throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        //保证参数合法性
        args = Utils.nullToEmpty(args);
        //取出参数的类型（Class）
        final Class<?>[] parameterTypes = Utils.toClass(args);
        //返回方法执行结果
        return invokeStaticMethod(clazz, methodName, args, parameterTypes);
    }

    public static Object invokeMethod(final Object object, final String methodName,
                                      Object... args) throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        args = Utils.nullToEmpty(args);
        final Class<?>[] parameterTypes = Utils.toClass(args);
        return invokeMethod(object, methodName, args, parameterTypes);
    }

    public static <T> T invokeConstructor(final Class<T> cls, Object... args)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            InstantiationException {
        args = Utils.nullToEmpty(args);//参数是否有内容，没有返回一个新的空值数组
        final Class<?> parameterTypes[] = Utils.toClass(args);//转为class类型
        return invokeConstructor(cls, args, parameterTypes);
    }

    public static <T> T invokeConstructor(final Class<T> cls, Object[] args, Class<?>[] parameterTypes)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            InstantiationException {
        args = Utils.nullToEmpty(args);
        parameterTypes = Utils.nullToEmpty(parameterTypes);
        final Constructor<T> ctor = getMatchingAccessibleConstructor(cls, parameterTypes);
        if (ctor == null) {
            throw new NoSuchMethodException(
                    "No such accessible constructor on object: " + cls.getName());
        }
        return ctor.newInstance(args);
    }

    public static <T> Constructor<T> getMatchingAccessibleConstructor(final Class<T> cls,
                                                                      final Class<?>... parameterTypes) {
        Validate.isTrue(cls != null, "class cannot be null");
        // see if we can find the constructor directly
        // most of the time this works and it's much faster
        try {
            final Constructor<T> ctor = cls.getConstructor(parameterTypes);
            MemberUtils.setAccessibleWorkaround(ctor);
            return ctor;
        } catch (final NoSuchMethodException e) { // NOPMD - Swallow
        }
        Constructor<T> result = null;
        /*
         * (1) Class.getConstructors() is documented to return Constructor<T> so as
         * long as the array is not subsequently modified, everything's fine.
         */
        final Constructor<?>[] ctors = cls.getConstructors();

        // return best match:
        for (Constructor<?> ctor : ctors) {
            // compare parameters
            if (MemberUtils.isAssignable(parameterTypes, ctor.getParameterTypes(), true)) {
                // get accessible version of constructor
                ctor = getAccessibleConstructor(ctor);
                if (ctor != null) {
                    MemberUtils.setAccessibleWorkaround(ctor);
                    if (result == null
                            || MemberUtils.compareParameterTypes(ctor.getParameterTypes(), result
                            .getParameterTypes(), parameterTypes) < 0) {
                        // temporary variable for annotation, see comment above (1)
                        @SuppressWarnings("unchecked")
                        final
                        Constructor<T> constructor = (Constructor<T>) ctor;
                        result = constructor;
                    }
                }
            }
        }
        return result;
    }

    private static <T> Constructor<T> getAccessibleConstructor(final Constructor<T> ctor) {
        Validate.isTrue(ctor != null, "constructor cannot be null");
        return MemberUtils.isAccessible(ctor)
                && isAccessible(ctor.getDeclaringClass()) ? ctor : null;
    }

    private static boolean isAccessible(final Class<?> type) {
        Class<?> cls = type;
        while (cls != null) {
            if (!Modifier.isPublic(cls.getModifiers())) {
                return false;
            }
            cls = cls.getEnclosingClass();
        }
        return true;
    }
}
