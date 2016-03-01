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

package com.morgoo.droidplugin.hook.proxy;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import com.morgoo.droidplugin.hook.BaseHookHandle;
import com.morgoo.droidplugin.hook.handle.IPackageManagerHookHandle;
import com.morgoo.droidplugin.reflect.Utils;
import com.morgoo.helper.Log;
import com.morgoo.helper.compat.ActivityThreadCompat;
import com.morgoo.helper.MyProxy;
import com.morgoo.droidplugin.reflect.FieldUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;


/**
 * Hook some function on IPackageManager
 * <p/>
 * Code by Andy Zhang (zhangyong232@gmail.com) on  2015/2/5.
 */
//PackageManager代理
public class IPackageManagerHook extends ProxyHook {

    private static final String TAG = IPackageManagerHook.class.getSimpleName();

    public IPackageManagerHook(Context hostContext) {
        super(hostContext);
    }

    @Override
    protected BaseHookHandle createHookHandle() {
        return new IPackageManagerHookHandle(mHostContext);
    }

    @Override
    protected void onInstall(ClassLoader classLoader) throws Throwable {
        //得到ActivityThread
        Object currentActivityThread = ActivityThreadCompat.currentActivityThread();
        Log.i("test", "得到ActivityThread created!!");
        //设置需要代理的对象（ActivityThread中的PackageManager）
        setOldObj(FieldUtils.readField(currentActivityThread, "sPackageManager"));
        //得到代理对象的所有接口
        Class<?> iPmClass = mOldObj.getClass();
        List<Class<?>> interfaces = Utils.getAllInterfaces(iPmClass);
        //转为Class数组
        Class[] ifs = interfaces != null && interfaces.size() > 0 ? interfaces.toArray(new Class[interfaces.size()]) : new Class[0];
        //实现动态代理，得到被伪装后的实例
        Object newPm = MyProxy.newProxyInstance(iPmClass.getClassLoader(), ifs, this);
        //将ActivityThread中的sPackageManager用 newPm（伪装对象）替换
        FieldUtils.writeField(currentActivityThread, "sPackageManager", newPm);
        //从HostContext获取PackageManager实例
        PackageManager pm = mHostContext.getPackageManager();
        //获取实例中的mPm成员，并将其替换为伪装对象newPm
        Object mPM = FieldUtils.readField(pm, "mPM");
        if (mPM != newPm) {
            FieldUtils.writeField(pm, "mPM", newPm);
        }
    }


    public static void fixContextPackageManager(Context context) {
        try {
            Object currentActivityThread = ActivityThreadCompat.currentActivityThread();
            Object newPm = FieldUtils.readField(currentActivityThread, "sPackageManager");
            PackageManager pm = context.getPackageManager();
            Object mPM = FieldUtils.readField(pm, "mPM");
            if (mPM != newPm) {
                FieldUtils.writeField(pm, "mPM", newPm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}