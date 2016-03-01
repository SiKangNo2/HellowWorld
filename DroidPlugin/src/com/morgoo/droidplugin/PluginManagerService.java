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

package com.morgoo.droidplugin;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.morgoo.droidplugin.hook.handle.IActivityManagerHookHandle;
import com.morgoo.droidplugin.pm.IPluginManagerImpl;

/**
 * 插件管理服务。
 * <p/>
 * Code by Andy Zhang (zhangyong232@gmail.com) on  2015/2/11.
 */
public class PluginManagerService extends Service {

    private static final String TAG = PluginManagerService.class.getSimpleName();
    private IPluginManagerImpl mPluginPackageManager;//与绑定Context通信的Binder

    @Override
    public void onCreate() {
        super.onCreate();
        /**
         * 初始化插件管理，IPluginManagerImpl间接继承于Binder
         * (继承关系：android.os.Binder-->IPluginManager.Stub-->IPluginManagerImpl)
         * */
        mPluginPackageManager = new IPluginManagerImpl(this);
        //加载已安装的插件并获取权限
        mPluginPackageManager.onCreate();
    }

    @Override
    public void onDestroy() {
        try {
            mPluginPackageManager.onDestroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        //将mPluginPackageManager返回给Context，使用的ServiceConnection实例为PluginManager类型
        return mPluginPackageManager;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //这里要处理IntentService
        IActivityManagerHookHandle.getIntentSender.handlePendingIntent(this, intent);
        return super.onStartCommand(intent, flags, startId);
    }


}
