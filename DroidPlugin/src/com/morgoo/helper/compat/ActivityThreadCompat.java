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

package com.morgoo.helper.compat;

import android.app.Instrumentation;
import android.os.Handler;
import android.os.Looper;

import com.morgoo.droidplugin.reflect.MethodUtils;

import java.lang.reflect.InvocationTargetException;


/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/3/14.
 */
public class ActivityThreadCompat {

    private static Object sActivityThread;
    private static Class sClass = null;
    //得到ActivityThread
    public synchronized static final Object currentActivityThread() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (sActivityThread == null) {
            //通过反射实现ActivityThread的currentActivityThread方法
            sActivityThread = MethodUtils.invokeStaticMethod(activityThreadClass(), "currentActivityThread");
            //如果结果为null，从主线程再执行一次
            if (sActivityThread == null) {
                sActivityThread = currentActivityThread2();
            }
        }
        return sActivityThread;
    }

    public static final Class activityThreadClass() throws ClassNotFoundException {
        if (sClass == null) {
            sClass = Class.forName("android.app.ActivityThread");
        }
        return sClass;
    }

    //从主线程中反射currentActivityThread方法
    private static Object currentActivityThread2() {
        //创建Handler，使用主线程Lopper
        Handler handler = new Handler(Looper.getMainLooper());
        final Object sLock = new Object();
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    sActivityThread = MethodUtils.invokeStaticMethod(activityThreadClass(), "currentActivityThread");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    synchronized (sLock) {
                        //解除当前线程阻塞
                        sLock.notify();
                    }
                }

            }
        });
        if (sActivityThread == null && Looper.getMainLooper() != Looper.myLooper()) {
            synchronized (sLock) {
                try {
                    //阻塞线程,等待sActivityThread得到值
                    sLock.wait(300);
                } catch (InterruptedException e) {
                }
            }
        }
        return null;
    }

    public static Instrumentation getInstrumentation() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        Object obj = currentActivityThread();
        return (Instrumentation) MethodUtils.invokeMethod(obj, "getInstrumentation");
    }
}
