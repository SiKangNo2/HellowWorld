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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.morgoo.droidplugin.PluginApplication;
import com.morgoo.droidplugin.hook.Hook;
import com.morgoo.droidplugin.hook.HookFactory;
import com.morgoo.droidplugin.hook.HookedMethodHandler;
import com.morgoo.helper.MyProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.DatagramSocket;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/3/14.
 */
//动态代理
public abstract class ProxyHook extends Hook implements InvocationHandler {
    //代理对象
    protected Object mOldObj;

    public ProxyHook(Context hostContext) {
        super(hostContext);
    }

    //得到代理对象
    public void setOldObj(Object oldObj) {
        this.mOldObj = oldObj;
    }

    //方法代理
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            //如果为false不需要使用代理
            if (!isEnable()) {
                return method.invoke(mOldObj, args);
            }
            //找到方法的代理类
            HookedMethodHandler hookedMethodHandler = mHookHandles.getHookedMethodHandler(method);
            //如果有此方法的代理则进行准备好的额外操作
            if (hookedMethodHandler != null) {
                return hookedMethodHandler.doHookInner(mOldObj, method, args);
            }
            //否则正常执行原方法
            return method.invoke(mOldObj, args);

        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause != null && MyProxy.isMethodDeclaredThrowable(method, cause)) {
                throw cause;
            } else if (cause != null) {
                RuntimeException runtimeException = !TextUtils.isEmpty(cause.getMessage()) ? new RuntimeException(cause.getMessage()) : new RuntimeException();
                runtimeException.initCause(cause);
                throw runtimeException;
            } else {
                RuntimeException runtimeException = !TextUtils.isEmpty(e.getMessage()) ? new RuntimeException(e.getMessage()) : new RuntimeException();
                runtimeException.initCause(e);
                throw runtimeException;
            }
        } catch (Throwable e) {
            if (MyProxy.isMethodDeclaredThrowable(method, e)) {
                throw e;
            } else {
                RuntimeException runtimeException = !TextUtils.isEmpty(e.getMessage()) ? new RuntimeException(e.getMessage()) : new RuntimeException();
                runtimeException.initCause(e);
                throw runtimeException;
            }
        }
    }
}
