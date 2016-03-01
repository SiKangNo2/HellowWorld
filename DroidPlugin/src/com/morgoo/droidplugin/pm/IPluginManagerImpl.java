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

package com.morgoo.droidplugin.pm;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.RemoteException;
import android.text.TextUtils;

import com.morgoo.droidplugin.am.BaseActivityManagerService;
import com.morgoo.droidplugin.am.MyActivityManagerService;
import com.morgoo.droidplugin.core.PluginClassLoader;
import com.morgoo.droidplugin.core.PluginDirHelper;
import com.morgoo.droidplugin.pm.parser.IntentMatcher;
import com.morgoo.droidplugin.pm.parser.PluginPackageParser;
import com.morgoo.helper.Log;
import com.morgoo.helper.compat.PackageManagerCompat;
import com.morgoo.helper.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 此服务模仿系统的PackageManagerService，提供对插件简单的管理服务。
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/2/12.
 */
public class IPluginManagerImpl extends IPluginManager.Stub {

    private static final String TAG = IPluginManagerImpl.class.getSimpleName();
    //插件缓存
    private Map<String, PluginPackageParser> mPluginCache = Collections.synchronizedMap(new HashMap<String, PluginPackageParser>(20));

    private Context mContext;

    private AtomicBoolean mHasLoadedOk = new AtomicBoolean(false);
    private final Object mLock = new Object();

    private BaseActivityManagerService mActivityManagerService;

    private Set<String> mHostRequestedPermission = new HashSet<String>(10);

    private Map<String, Signature[]> mSignatureCache = new HashMap<String, Signature[]>();

    public IPluginManagerImpl(Context context) {
        mContext = context;
        mActivityManagerService = new MyActivityManagerService(mContext);
    }

    public void onCreate() {
        new Thread() {
            @Override
            public void run() {
                onCreateInner();
            }
        }.start();
    }

    private void onCreateInner() {
        //加载所有插件
        loadAllPlugin(mContext);
        //加载所有权限
        loadHostRequestedPermission();
        try {
            mHasLoadedOk.set(true);
            synchronized (mLock) {
                //加载完成，解除所有被阻塞的线程
                mLock.notifyAll();
            }
        } catch (Exception e) {
        }
    }

    //加载所需的权限
    private void loadHostRequestedPermission() {
        try {
            mHostRequestedPermission.clear();
            PackageManager pm = mContext.getPackageManager();
            PackageInfo pms = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (pms != null && pms.requestedPermissions != null && pms.requestedPermissions.length > 0) {
                for (String requestedPermission : pms.requestedPermissions) {
                    mHostRequestedPermission.add(requestedPermission);
                }
            }
        } catch (Exception e) {
        }
    }

    //加载所有插件
    private void loadAllPlugin(Context context) {
        long b = System.currentTimeMillis();
        //插件apk集合
        ArrayList<File> apkfiles = null;
        try {
            apkfiles = new ArrayList<File>();
            //获取插件根目录
            File baseDir = new File(PluginDirHelper.getBaseDir(context));
            File[] dirs = baseDir.listFiles();
            //取出所有插件（apk），放入apkFiles
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    //使用宿主目录下的apk文件构建新的apk
                    File file = new File(dir, "apk/base-1.apk");
                    if (file.exists()) {
                        apkfiles.add(file);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "scan a apk file error %s", e);
        }

        Log.i(TAG, "Search apk cost %s ms", (System.currentTimeMillis() - b));
        b = System.currentTimeMillis();

        if (apkfiles != null && apkfiles.size() > 0) {
            for (File pluginFile : apkfiles) {
                long b1 = System.currentTimeMillis();
                try {
                    //初始化PluginPackageParser（apk解析工具）
                    PluginPackageParser pluginPackageParser = new PluginPackageParser(mContext, pluginFile);
                    //读取插件签名
                    Signature[] signatures = readSignatures(pluginPackageParser.getPackageName());
                    //如果没有签名，为其保存签名，否则直接添加到缓存
                    if (signatures == null || signatures.length <= 0) {
                        pluginPackageParser.collectCertificates(0);
                        PackageInfo info = pluginPackageParser.getPackageInfo(PackageManager.GET_SIGNATURES);
                        saveSignatures(info);
                    } else {
                        mSignatureCache.put(pluginPackageParser.getPackageName(), signatures);
                        pluginPackageParser.writeSignature(signatures);
                    }
                    if (!mPluginCache.containsKey(pluginPackageParser.getPackageName())) {
                        mPluginCache.put(pluginPackageParser.getPackageName(), pluginPackageParser);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "parse a apk file error %s", e, pluginFile.getPath());
                } finally {
                    Log.i(TAG, "Parse %s apk cost %s ms", pluginFile.getPath(), (System.currentTimeMillis() - b1));
                }
            }
        }

        Log.i(TAG, "Parse all apk cost %s ms", (System.currentTimeMillis() - b));
        b = System.currentTimeMillis();

        try {
            mActivityManagerService.onCreate(IPluginManagerImpl.this);
        } catch (Throwable e) {
            Log.e(TAG, "mActivityManagerService.onCreate", e);
        }

        Log.i(TAG, "ActivityManagerService.onCreate %s ms", (System.currentTimeMillis() - b));
    }

    //删除缓存中失效的插件
    private void enforcePluginFileExists() throws RemoteException {
        List<String> removedPkg = new ArrayList<>();
        for (String pkg : mPluginCache.keySet()) {
            PluginPackageParser parser = mPluginCache.get(pkg);
            File pluginFile = parser.getPluginFile();
            if (pluginFile != null && pluginFile.exists()) {
                //DO NOTHING
            } else {
                removedPkg.add(pkg);
            }
        }
        for (String pkg : removedPkg) {
            deletePackage(pkg, 0);
        }
    }


    @Override
    public boolean waitForReady() {
        waitForReadyInner();
        return true;
    }

    //阻塞当前线程
    private void waitForReadyInner() {
        if (!mHasLoadedOk.get()) {
            synchronized (mLock) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }


    private void handleException(Exception e) throws RemoteException {
        RemoteException remoteException;
        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            remoteException = new RemoteException(e.getMessage());
            remoteException.initCause(e);
            remoteException.setStackTrace(e.getStackTrace());
        } else {
            remoteException = new RemoteException();
            remoteException.initCause(e);
            remoteException.setStackTrace(e.getStackTrace());
        }
        throw remoteException;
    }


    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws RemoteException {
        //阻塞线程，等待本地插件初始化结束
        waitForReadyInner();
        try {
            String pkg = getAndCheckCallingPkg(packageName);
            if (pkg != null) {
                //删除缓存中失效插件
                enforcePluginFileExists();
                //在缓存中查找是否已存在
                PluginPackageParser parser = mPluginCache.get(pkg);
                if (parser != null) {
                    PackageInfo packageInfo = parser.getPackageInfo(flags);
                    if (packageInfo != null && (flags & PackageManager.GET_SIGNATURES) != 0 && packageInfo.signatures == null) {
                        packageInfo.signatures = mSignatureCache.get(packageName);
                    }
                    return packageInfo;
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }


    @Override
    public boolean isPluginPackage(String packageName) throws RemoteException {
        waitForReadyInner();
        enforcePluginFileExists();
        return mPluginCache.containsKey(packageName);
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName className, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            String pkg = getAndCheckCallingPkg(className.getPackageName());
            if (pkg != null) {
                enforcePluginFileExists();
                PluginPackageParser parser = mPluginCache.get(className.getPackageName());
                if (parser != null) {
                    return parser.getActivityInfo(className, flags);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName className, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            String pkg = getAndCheckCallingPkg(className.getPackageName());
            if (pkg != null) {
                enforcePluginFileExists();
                PluginPackageParser parser = mPluginCache.get(className.getPackageName());
                if (parser != null) {
                    return parser.getReceiverInfo(className, flags);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName className, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            String pkg = getAndCheckCallingPkg(className.getPackageName());
            if (pkg != null) {
                enforcePluginFileExists();
                PluginPackageParser parser = mPluginCache.get(className.getPackageName());
                if (parser != null) {
                    return parser.getServiceInfo(className, flags);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }

        return null;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName className, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            String pkg = getAndCheckCallingPkg(className.getPackageName());
            if (pkg != null) {
                enforcePluginFileExists();
                PluginPackageParser parser = mPluginCache.get(className.getPackageName());
                if (parser != null) {
                    return parser.getProviderInfo(className, flags);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    private boolean shouldNotBlockOtherInfo() {
        return true;
//        int pid = Binder.getCallingPid();
//        if (pid == android.os.Process.myPid()) {
//            return true;
//        } else {
//            List<String> pkgs = mActivityManagerService.getPackageNamesByPid(pid);
//            if (pkgs != null && pkgs.size() > 0 && !pkgs.contains(mContext.getPackageName())) {
//                return false;
//            } else {
//                return true;
//            }
//        }
    }

    private String getAndCheckCallingPkg(String pkg) {
        return pkg;
//        if (shouldNotBlockOtherInfo()) {
//            return pkg;
//        } else {
//            if (!pkgInPid(Binder.getCallingPid(), pkg)) {
//                return null;
//            } else {
//                return pkg;
//            }
//        }
    }

    private boolean pkgInPid(int pid, String pkg) {
        List<String> pkgs = mActivityManagerService.getPackageNamesByPid(pid);
        if (pkgs != null && pkgs.size() > 0) {
            return pkgs.contains(pkg);
        } else {
            return true;
        }
    }

    @Override
    public ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                List<ResolveInfo> infos = IntentMatcher.resolveIntent(mContext, mPluginCache, intent, resolvedType, flags);
                if (infos != null && infos.size() > 0) {
                    return IntentMatcher.findBest(infos);
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                List<ResolveInfo> infos = new ArrayList<ResolveInfo>();
                for (String pkg : pkgs) {
                    intent.setPackage(pkg);
                    List<ResolveInfo> list = IntentMatcher.resolveIntent(mContext, mPluginCache, intent, resolvedType, flags);
                    infos.addAll(list);
                }
                if (infos != null && infos.size() > 0) {
                    return IntentMatcher.findBest(infos);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }


    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                return IntentMatcher.resolveActivityIntent(mContext, mPluginCache, intent, resolvedType, flags);
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                List<ResolveInfo> infos = new ArrayList<ResolveInfo>();
                for (String pkg : pkgs) {
                    intent.setPackage(pkg);
                    List<ResolveInfo> list = IntentMatcher.resolveActivityIntent(mContext, mPluginCache, intent, resolvedType, flags);
                    infos.addAll(list);
                }
                if (infos != null && infos.size() > 0) {
                    return infos;
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public List<ResolveInfo> queryIntentReceivers(Intent intent, String resolvedType, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                return IntentMatcher.resolveReceiverIntent(mContext, mPluginCache, intent, resolvedType, flags);
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                List<ResolveInfo> infos = new ArrayList<ResolveInfo>();
                for (String pkg : pkgs) {
                    intent.setPackage(pkg);
                    List<ResolveInfo> list = IntentMatcher.resolveReceiverIntent(mContext, mPluginCache, intent, resolvedType, flags);
                    infos.addAll(list);
                }
                if (infos != null && infos.size() > 0) {
                    return infos;
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public ResolveInfo resolveService(Intent intent, String resolvedType, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                List<ResolveInfo> infos = IntentMatcher.resolveServiceIntent(mContext, mPluginCache, intent, resolvedType, flags);
                if (infos != null && infos.size() > 0) {
                    return IntentMatcher.findBest(infos);
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                List<ResolveInfo> infos = new ArrayList<ResolveInfo>();
                for (String pkg : pkgs) {
                    intent.setPackage(pkg);
                    List<ResolveInfo> list = IntentMatcher.resolveServiceIntent(mContext, mPluginCache, intent, resolvedType, flags);
                    infos.addAll(list);
                }
                if (infos != null && infos.size() > 0) {
                    return IntentMatcher.findBest(infos);
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public List<ResolveInfo> queryIntentServices(Intent intent, String resolvedType, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                IntentMatcher.resolveServiceIntent(mContext, mPluginCache, intent, resolvedType, flags);
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                List<ResolveInfo> infos = new ArrayList<ResolveInfo>();
                for (String pkg : pkgs) {
                    intent.setPackage(pkg);
                    List<ResolveInfo> list = IntentMatcher.resolveServiceIntent(mContext, mPluginCache, intent, resolvedType, flags);
                    infos.addAll(list);
                }
                if (infos != null && infos.size() > 0) {
                    return infos;
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public List<ResolveInfo> queryIntentContentProviders(Intent intent, String resolvedType, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                return IntentMatcher.resolveProviderIntent(mContext, mPluginCache, intent, resolvedType, flags);
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                List<ResolveInfo> infos = new ArrayList<ResolveInfo>();
                for (String pkg : pkgs) {
                    intent.setPackage(pkg);
                    List<ResolveInfo> list = IntentMatcher.resolveProviderIntent(mContext, mPluginCache, intent, resolvedType, flags);
                    infos.addAll(list);
                }
                if (infos != null && infos.size() > 0) {
                    return infos;
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }


    @Override
    public List<PackageInfo> getInstalledPackages(int flags) throws RemoteException {
        //检查服务是否初始化完成，没有则进入阻塞状态，等待初始化完成
        waitForReadyInner();
        try {
            //删除缓存中失效文件
            enforcePluginFileExists();
            List<PackageInfo> infos = new ArrayList<PackageInfo>(mPluginCache.size());
            //初始化时加载的本地插件缓存取出（shouldNotBlockOtherInfo()无内容 ，返回true）
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    infos.add(pluginPackageParser.getPackageInfo(flags));
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    if (pkgs.contains(pluginPackageParser.getPackageName())) {
                        infos.add(pluginPackageParser.getPackageInfo(flags));
                    }
                }
            }
            return infos;
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            List<ApplicationInfo> infos = new ArrayList<ApplicationInfo>(mPluginCache.size());
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    infos.add(pluginPackageParser.getApplicationInfo(flags));
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    if (pkgs.contains(pluginPackageParser.getPackageName())) {
                        infos.add(pluginPackageParser.getApplicationInfo(flags));
                    }
                }

            }
            return infos;
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public PermissionInfo getPermissionInfo(String name, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionInfo> permissionInfos = pluginPackageParser.getPermissions();
                    for (PermissionInfo permissionInfo : permissionInfos) {
                        if (TextUtils.equals(permissionInfo.name, name)) {
                            return permissionInfo;
                        }
                    }
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionInfo> permissionInfos = pluginPackageParser.getPermissions();
                    for (PermissionInfo permissionInfo : permissionInfos) {
                        if (TextUtils.equals(permissionInfo.name, name) && pkgs.contains(permissionInfo.packageName)) {
                            return permissionInfo;
                        }
                    }
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public List<PermissionInfo> queryPermissionsByGroup(String group, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            List<PermissionInfo> list = new ArrayList<PermissionInfo>();
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionInfo> permissionInfos = pluginPackageParser.getPermissions();
                    for (PermissionInfo permissionInfo : permissionInfos) {
                        if (TextUtils.equals(permissionInfo.group, group) && !list.contains(permissionInfo)) {
                            list.add(permissionInfo);
                        }
                    }
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionInfo> permissionInfos = pluginPackageParser.getPermissions();
                    for (PermissionInfo permissionInfo : permissionInfos) {
                        if (pkgs.contains(permissionInfo.packageName) && TextUtils.equals(permissionInfo.group, group) && !list.contains(permissionInfo)) {
                            list.add(permissionInfo);
                        }
                    }
                }
            }
            return list;
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public PermissionGroupInfo getPermissionGroupInfo(String name, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionGroupInfo> permissionGroupInfos = pluginPackageParser.getPermissionGroups();
                    for (PermissionGroupInfo permissionGroupInfo : permissionGroupInfos) {
                        if (TextUtils.equals(permissionGroupInfo.name, name)) {
                            return permissionGroupInfo;
                        }
                    }
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionGroupInfo> permissionGroupInfos = pluginPackageParser.getPermissionGroups();
                    for (PermissionGroupInfo permissionGroupInfo : permissionGroupInfos) {
                        if (TextUtils.equals(permissionGroupInfo.name, name) && pkgs.contains(permissionGroupInfo.packageName)) {
                            return permissionGroupInfo;
                        }
                    }
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public List<PermissionGroupInfo> getAllPermissionGroups(int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            List<PermissionGroupInfo> list = new ArrayList<PermissionGroupInfo>();
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionGroupInfo> permissionGroupInfos = pluginPackageParser.getPermissionGroups();
                    for (PermissionGroupInfo permissionGroupInfo : permissionGroupInfos) {
                        if (!list.contains(permissionGroupInfo)) {
                            list.add(permissionGroupInfo);
                        }
                    }
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<PermissionGroupInfo> permissionGroupInfos = pluginPackageParser.getPermissionGroups();
                    for (PermissionGroupInfo permissionGroupInfo : permissionGroupInfos) {
                        if (!list.contains(permissionGroupInfo) && pkgs
                                .contains(permissionGroupInfo.packageName)) {
                            list.add(permissionGroupInfo);
                        }
                    }
                }
            }
            return list;
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public ProviderInfo resolveContentProvider(String name, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            enforcePluginFileExists();
            if (shouldNotBlockOtherInfo()) {
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<ProviderInfo> providerInfos = pluginPackageParser.getProviders();
                    for (ProviderInfo providerInfo : providerInfos) {
                        if (TextUtils.equals(providerInfo.authority, name)) {
                            return providerInfo;
                        }
                    }
                }
            } else {
                List<String> pkgs = mActivityManagerService.getPackageNamesByPid(Binder.getCallingPid());
                for (PluginPackageParser pluginPackageParser : mPluginCache.values()) {
                    List<ProviderInfo> providerInfos = pluginPackageParser.getProviders();
                    for (ProviderInfo providerInfo : providerInfos) {
                        if (TextUtils.equals(providerInfo.authority, name) && pkgs.contains(providerInfo.packageName)) {
                            return providerInfo;
                        }
                    }
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public void deleteApplicationCacheFiles(String packageName, IPackageDataObserver observer) throws RemoteException {
        boolean success = false;
        try {
            if (TextUtils.isEmpty(packageName)) {
                return;
            }

            PluginPackageParser parser = mPluginCache.get(packageName);
            if (parser == null) {
                return;
            }
            ApplicationInfo applicationInfo = parser.getApplicationInfo(0);
            Utils.deleteDir(new File(applicationInfo.dataDir, "caches").getName());
            success = true;
        } catch (Exception e) {
            handleException(e);
        } finally {
            if (observer != null) {
                observer.onRemoveCompleted(packageName, success);
            }
        }
    }

    @Override
    public void clearApplicationUserData(String packageName, IPackageDataObserver observer) throws RemoteException {
        boolean success = false;
        try {
            if (TextUtils.isEmpty(packageName)) {
                return;
            }

            PluginPackageParser parser = mPluginCache.get(packageName);
            if (parser == null) {
                return;
            }
            ApplicationInfo applicationInfo = parser.getApplicationInfo(0);
            Utils.deleteDir(applicationInfo.dataDir);
            success = true;
        } catch (Exception e) {
            handleException(e);
        } finally {
            if (observer != null) {
                observer.onRemoveCompleted(packageName, success);
            }
        }
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws RemoteException {
        waitForReadyInner();
        try {
            PluginPackageParser parser = mPluginCache.get(packageName);
            if (parser != null) {
                return parser.getApplicationInfo(flags);
            }
        } catch (Exception e) {
            handleException(e);
        }

        return null;
    }

    /**
     * @param filepath 插件Apk的路径
     * @param flags    Flag
     */
    @Override
    public int installPackage(String filepath, int flags) throws RemoteException {
        //install plugin
        String apkfile = null;
        try {
            // 验证apk的合法性
            PackageManager pm = mContext.getPackageManager();
            //得到apk的整体包信息
            PackageInfo info = pm.getPackageArchiveInfo(filepath, 0);
            if (info == null) {
                return PackageManagerCompat.INSTALL_FAILED_INVALID_APK;
            }
            //以插件的包名在宿主程序的插件目录下创建一个当前插件的专属包，并初始化一个“apk”目录
            apkfile = PluginDirHelper.getPluginApkFile(mContext, info.packageName);

            if ((flags & PackageManagerCompat.INSTALL_REPLACE_EXISTING) != 0) {
                // 新安装的插件
                // 停止，删除插件的缓存
                forceStopPackage(info.packageName);//杀掉进程
                if (mPluginCache.containsKey(info.packageName)) {
                    //删除缓存区文件
                    deleteApplicationCacheFiles(info.packageName, null);
                }
                new File(apkfile).delete();//删除当前插件在宿主程序目录下的apk文件（此文件为初始化目录结构时创建的空文件）
                Utils.copyFile(filepath, apkfile);//将插件的apk文件写入目录

                //初始化PluginPackageParser（apk解析工具），将插件的Activity,service,boardCast等信息解析出来，放入缓存
                PluginPackageParser parser = new PluginPackageParser(mContext, new File(apkfile));
                parser.collectCertificates(0);//？收集凭证（字面翻译）
                PackageInfo pkgInfo = parser.getPackageInfo(PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
                // 验证申请的permission，宿主中是否存在，不存在不让安装。
                if (pkgInfo != null && pkgInfo.requestedPermissions != null && pkgInfo.requestedPermissions.length > 0) {
                    for (String requestedPermission : pkgInfo.requestedPermissions) {
                        boolean b = false;
                        try {
                            //得到插件的每一个权限
                            b = pm.getPermissionInfo(requestedPermission, 0) != null;
                        } catch (NameNotFoundException e) {
                        }
                        //在Host中查找，如果没有此权限则返回错误标识
                        if (!mHostRequestedPermission.contains(requestedPermission) && b) {
                            Log.e(TAG, "No Permission %s", requestedPermission);
                            new File(apkfile).delete();
                            return PluginManager.INSTALL_FAILED_NO_REQUESTEDPERMISSION;
                        }
                    }
                }
                // 保存签名
                saveSignatures(pkgInfo);
//                if (pkgInfo.reqFeatures != null && pkgInfo.reqFeatures.length > 0) {
//                    for (FeatureInfo reqFeature : pkgInfo.reqFeatures) {
//                        Log.e(TAG, "reqFeature name=%s,flags=%s,glesVersion=%s", reqFeature.name, reqFeature.flags, reqFeature.getGlEsVersion());
//                    }
//                }
                // 拷贝插件中的native库文件。
                copyNativeLibs(mContext, apkfile, parser.getApplicationInfo(0));
                // opt Dex文件，并使用DexClassLoader动态的载入类代码。
                dexOpt(mContext, apkfile, parser);
                mPluginCache.put(parser.getPackageName(), parser);
                // 回调函数，通知插件安装
                mActivityManagerService.onPkgInstalled(mPluginCache, parser, parser.getPackageName());
                //发送广播通知插件有新的安装
                sendInstalledBroadcast(info.packageName);
                return PackageManagerCompat.INSTALL_SUCCEEDED;
            } else {
                //在缓存中查找当前插件，存在则不安装
                if (mPluginCache.containsKey(info.packageName)) {
                    return PackageManagerCompat.INSTALL_FAILED_ALREADY_EXISTS;
                } else {
                    // 覆盖安装,即更新的过程，与新安装类似
                    forceStopPackage(info.packageName);//杀掉进程
                    new File(apkfile).delete();//删除原apk文件
                    Utils.copyFile(filepath, apkfile);//将新的插件apk copy到目录
                    //初始化PluginPackageParser（apk解析类）
                    PluginPackageParser parser = new PluginPackageParser(mContext, new File(apkfile));
                    parser.collectCertificates(0);
                    // 验证申请的permission，宿主中是否存在，不存在不让安装。
                    PackageInfo pkgInfo = parser.getPackageInfo(PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
                    if (pkgInfo != null && pkgInfo.requestedPermissions != null && pkgInfo.requestedPermissions.length > 0) {
                        for (String requestedPermission : pkgInfo.requestedPermissions) {
                            boolean b = false;
                            try {
                                b = pm.getPermissionInfo(requestedPermission, 0) != null;
                            } catch (NameNotFoundException e) {
                            }
                            if (!mHostRequestedPermission.contains(requestedPermission) && b) {
                                Log.e(TAG, "No Permission %s", requestedPermission);
                                new File(apkfile).delete();
                                return PluginManager.INSTALL_FAILED_NO_REQUESTEDPERMISSION;
                            }
                        }
                    }
                    // 保存签名
                    saveSignatures(pkgInfo);
//                    if (pkgInfo.reqFeatures != null && pkgInfo.reqFeatures.length > 0) {
//                        for (FeatureInfo reqFeature : pkgInfo.reqFeatures) {
//                            Log.e(TAG, "reqFeature name=%s,flags=%s,glesVersion=%s", reqFeature.name, reqFeature.flags, reqFeature.getGlEsVersion());
//                        }
//                    }
                    // 拷贝插件中的native库文件
                    copyNativeLibs(mContext, apkfile, parser.getApplicationInfo(0));
                    // opt Dex文件，并使用DexClassLoader动态的载入类代码。
                    dexOpt(mContext, apkfile, parser);
                    mPluginCache.put(parser.getPackageName(), parser);
                    // 回调函数，通知插件安装
                    mActivityManagerService.onPkgInstalled(mPluginCache, parser, parser.getPackageName());
                    sendInstalledBroadcast(info.packageName);
                    return PackageManagerCompat.INSTALL_SUCCEEDED;
                }
            }
        } catch (Exception e) {
            //删除无法加载的插件，返回错误标识
            if (apkfile != null) {
                new File(apkfile).delete();
            }
            handleException(e);
            return PackageManagerCompat.INSTALL_FAILED_INTERNAL_ERROR;
        }
    }

    private void dexOpt(Context hostContext, String apkfile, PluginPackageParser parser) throws Exception {
        String packageName = parser.getPackageName();
        String optimizedDirectory = PluginDirHelper.getPluginDalvikCacheDir(hostContext, packageName);
        String libraryPath = PluginDirHelper.getPluginNativeLibraryDir(hostContext, packageName);
        ClassLoader classloader = new PluginClassLoader(apkfile, optimizedDirectory, libraryPath, ClassLoader.getSystemClassLoader());
//        DexFile dexFile = DexFile.loadDex(apkfile, PluginDirHelper.getPluginDalvikCacheFile(mContext, parser.getPackageName()), 0);
//        Log.e(TAG, "dexFile=%s,1=%s,2=%s", dexFile, DexFile.isDexOptNeeded(apkfile), DexFile.isDexOptNeeded(PluginDirHelper.getPluginDalvikCacheFile(mContext, parser.getPackageName())));
    }

    //保存签名
    private void saveSignatures(PackageInfo pkgInfo) {
        if (pkgInfo != null && pkgInfo.signatures != null) {
            int i = 0;
            for (Signature signature : pkgInfo.signatures) {
                File file = new File(PluginDirHelper.getPluginSignatureFile(mContext, pkgInfo.packageName, i));
                try {
                    Utils.writeToFile(file, signature.toByteArray());
                    Log.i(TAG, "Save %s signature of %s,md5=%s", pkgInfo.packageName, i, Utils.md5(signature.toByteArray()));
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.w(TAG, "Save signatures fail", e);
                    file.delete();
                    Utils.deleteDir(PluginDirHelper.getPluginSignatureDir(mContext, pkgInfo.packageName));
                    break;
                }
                i++;
            }
        }
    }

    private Signature[] readSignatures(String packageName) {
        List<String> fils = PluginDirHelper.getPluginSignatureFiles(mContext, packageName);
        List<Signature> signatures = new ArrayList<Signature>(fils.size());
        int i = 0;
        for (String file : fils) {
            try {
                byte[] data = Utils.readFromFile(new File(file));
                if (data != null) {
                    Signature sin = new Signature(data);
                    signatures.add(sin);
                    Log.i(TAG, "Read %s signature of %s,md5=%s", packageName, i, Utils.md5(sin.toByteArray()));
                } else {
                    Log.i(TAG, "Read %s signature of %s FAIL", packageName, i);
                    return null;
                }
                i++;
            } catch (Exception e) {
                Log.i(TAG, "Read %s signature of %s FAIL", e, packageName, i);
                return null;
            }
        }
        return signatures.toArray(new Signature[signatures.size()]);
    }


    private void sendInstalledBroadcast(String packageName) {
        Intent intent = new Intent(PluginManager.ACTION_PACKAGE_ADDED);
        intent.setData(Uri.parse("package://" + packageName));
        mContext.sendBroadcast(intent);
    }

    private void sendUninstalledBroadcast(String packageName) {
        Intent intent = new Intent(PluginManager.ACTION_PACKAGE_REMOVED);
        intent.setData(Uri.parse("package://" + packageName));
        mContext.sendBroadcast(intent);
    }

    private void copyNativeLibs(Context context, String apkfile, ApplicationInfo applicationInfo) throws Exception {
        String nativeLibraryDir = PluginDirHelper.getPluginNativeLibraryDir(context, applicationInfo.packageName);
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(apkfile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            Map<String, ZipEntry> libZipEntries = new HashMap<String, ZipEntry>();
            Map<String, Set<String>> soList = new HashMap<String, Set<String>>(1);
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.contains("../")) {
                    Log.d(TAG, "Path traversal attack prevented");
                    continue;
                }
                if (name.startsWith("lib/") && !entry.isDirectory()) {
                    libZipEntries.put(name, entry);
                    String soName = new File(name).getName();
                    Set<String> fs = soList.get(soName);
                    if (fs == null) {
                        fs = new TreeSet<String>();
                        soList.put(soName, fs);
                    }
                    fs.add(name);
                }
            }

            for (String soName : soList.keySet()) {
                Log.e(TAG, "==========so name=" + soName);
                Set<String> soPaths = soList.get(soName);
                String soPath = findSoPath(soPaths);
                if (soPath != null) {
                    File file = new File(nativeLibraryDir, soName);
                    if (file.exists()) {
                        file.delete();
                    }
                    InputStream in = null;
                    FileOutputStream ou = null;
                    try {
                        in = zipFile.getInputStream(libZipEntries.get(soPath));
                        ou = new FileOutputStream(file);
                        byte[] buf = new byte[8192];
                        int read = 0;
                        while ((read = in.read(buf)) != -1) {
                            ou.write(buf, 0, read);
                        }
                        ou.flush();
                        ou.getFD().sync();
                        Log.i(TAG, "copy so(%s) for %s to %s ok!", soName, soPath, file.getPath());
                    } catch (Exception e) {
                        if (file.exists()) {
                            file.delete();
                        }
                        throw e;
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Exception e) {
                            }
                        }
                        if (ou != null) {
                            try {
                                ou.close();
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private String findSoPath(Set<String> soPaths) {
        if (soPaths != null && soPaths.size() > 0) {
            for (String soPath : soPaths) {
                if (!TextUtils.isEmpty(Build.CPU_ABI) && soPath.contains(Build.CPU_ABI)) {
                    return soPath;
                }
            }

            for (String soPath : soPaths) {
                if (!TextUtils.isEmpty(Build.CPU_ABI2) && soPath.contains(Build.CPU_ABI2)) {
                    return soPath;
                }
            }
        }
        return null;
    }

    //删除插件
    @Override
    public int deletePackage(String packageName, int flags) throws RemoteException {
        try {
            if (mPluginCache.containsKey(packageName)) {
                forceStopPackage(packageName);//停止进程
                PluginPackageParser parser;
                synchronized (mPluginCache) {
                    parser = mPluginCache.remove(packageName);
                }
                Utils.deleteDir(PluginDirHelper.makePluginBaseDir(mContext, packageName));
                mActivityManagerService.onPkgDeleted(mPluginCache, parser, packageName);
                mSignatureCache.remove(packageName);
                sendUninstalledBroadcast(packageName);
                return PackageManagerCompat.DELETE_SUCCEEDED;
            }
        } catch (Exception e) {
            handleException(e);
        }
        return PackageManagerCompat.DELETE_FAILED_INTERNAL_ERROR;
    }

    @Override
    public List<ActivityInfo> getReceivers(String packageName, int flags) throws RemoteException {
        try {
            String pkg = getAndCheckCallingPkg(packageName);
            if (pkg != null) {
                PluginPackageParser parser = mPluginCache.get(packageName);
                if (parser != null) {
                    return new ArrayList<ActivityInfo>(parser.getReceivers());
                }
            }
        } catch (Exception e) {
            RemoteException remoteException = new RemoteException();
            remoteException.setStackTrace(e.getStackTrace());
            throw remoteException;
        }
        return new ArrayList<ActivityInfo>(0);
    }

    @Override
    public List<IntentFilter> getReceiverIntentFilter(ActivityInfo info) throws RemoteException {
        try {
            String pkg = getAndCheckCallingPkg(info.packageName);
            if (pkg != null) {
                PluginPackageParser parser = mPluginCache.get(info.packageName);
                if (parser != null) {
                    List<IntentFilter> filters = parser.getReceiverIntentFilter(info);
                    if (filters != null && filters.size() > 0) {
                        return new ArrayList<IntentFilter>(filters);
                    }
                }
            }
            return new ArrayList<IntentFilter>(0);
        } catch (Exception e) {
            RemoteException remoteException = new RemoteException();
            remoteException.setStackTrace(e.getStackTrace());
            throw remoteException;
        }
    }

    @Override
    public int checkSignatures(String pkg1, String pkg2) throws RemoteException {
        PackageManager pm = mContext.getPackageManager();
        Signature[] signatures1 = new Signature[0];
        try {
            signatures1 = getSignature(pkg1, pm);
        } catch (NameNotFoundException e) {
            return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
        }
        Signature[] signatures2 = new Signature[0];
        try {
            signatures2 = getSignature(pkg2, pm);
        } catch (NameNotFoundException e) {
            return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
        }


        boolean pkg1Signed = signatures1 != null && signatures1.length > 0;
        boolean pkg2Signed = signatures2 != null && signatures2.length > 0;

        if (!pkg1Signed && !pkg2Signed) {
            return PackageManager.SIGNATURE_NEITHER_SIGNED;
        } else if (!pkg1Signed && pkg2Signed) {
            return PackageManager.SIGNATURE_FIRST_NOT_SIGNED;
        } else if (pkg1Signed && !pkg2Signed) {
            return PackageManager.SIGNATURE_SECOND_NOT_SIGNED;
        } else {
            if (signatures1.length == signatures2.length) {
                for (int i = 0; i < signatures1.length; i++) {
                    Signature s1 = signatures1[i];
                    Signature s2 = signatures2[i];
                    if (!Arrays.equals(s1.toByteArray(), s2.toByteArray())) {
                        return PackageManager.SIGNATURE_NO_MATCH;
                    }
                }
                return PackageManager.SIGNATURE_MATCH;
            } else {
                return PackageManager.SIGNATURE_NO_MATCH;
            }
        }
    }

    private Signature[] getSignature(String pkg, PackageManager pm) throws RemoteException, NameNotFoundException {
        PackageInfo info = getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
        if (info == null) {
            info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
        }
        if (info == null) {
            throw new NameNotFoundException();
        }
        return info.signatures;

    }

    //////////////////////////////////////
    //
    //  THIS API FOR ACTIVITY MANAGER
    //
    //////////////////////////////////////

    @Override
    public ActivityInfo selectStubActivityInfo(ActivityInfo pluginInfo) throws RemoteException {
        return mActivityManagerService.selectStubActivityInfo(Binder.getCallingPid(), Binder.getCallingUid(), pluginInfo);
    }

    @Override
    public ActivityInfo selectStubActivityInfoByIntent(Intent intent) throws RemoteException {
        ActivityInfo ai = null;
        if (intent.getComponent() != null) {
            ai = getActivityInfo(intent.getComponent(), 0);
        } else {
            ResolveInfo resolveInfo = resolveIntent(intent, intent.resolveTypeIfNeeded(mContext.getContentResolver()), 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                ai = resolveInfo.activityInfo;
            }
        }

        if (ai != null) {
            return selectStubActivityInfo(ai);
        }
        return null;
    }

    @Override
    public ServiceInfo selectStubServiceInfo(ServiceInfo targetInfo) throws RemoteException {
        return mActivityManagerService.selectStubServiceInfo(Binder.getCallingPid(), Binder.getCallingUid(), targetInfo);
    }

    @Override
    public ServiceInfo selectStubServiceInfoByIntent(Intent intent) throws RemoteException {
        ServiceInfo ai = null;
        if (intent.getComponent() != null) {
            ai = getServiceInfo(intent.getComponent(), 0);
        } else {
            ResolveInfo resolveInfo = resolveIntent(intent, intent.resolveTypeIfNeeded(mContext.getContentResolver()), 0);
            if (resolveInfo.serviceInfo != null) {
                ai = resolveInfo.serviceInfo;
            }
        }

        if (ai != null) {
            return selectStubServiceInfo(ai);
        }
        return null;
    }


    @Override
    public ServiceInfo getTargetServiceInfo(ServiceInfo targetInfo) throws RemoteException {
        return mActivityManagerService.getTargetServiceInfo(Binder.getCallingPid(), Binder.getCallingUid(), targetInfo);
    }

    @Override
    public ProviderInfo selectStubProviderInfo(String name) throws RemoteException {
        ProviderInfo targetInfo = resolveContentProvider(name, 0);
        return mActivityManagerService.selectStubProviderInfo(Binder.getCallingPid(), Binder.getCallingUid(), targetInfo);
    }

    @Override
    public List<String> getPackageNameByPid(int pid) throws RemoteException {
        List<String> packageNameByProcessName = mActivityManagerService.getPackageNamesByPid(pid);
        if (packageNameByProcessName != null) {
            return new ArrayList<String>(packageNameByProcessName);
        } else {
            return null;
        }
    }

    @Override
    public String getProcessNameByPid(int pid) throws RemoteException {
        return mActivityManagerService.getProcessNameByPid(pid);
    }


    @Override
    public boolean killBackgroundProcesses(String pluginPackageName) throws RemoteException {
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> infos = am.getRunningAppProcesses();
        boolean success = false;
        for (RunningAppProcessInfo info : infos) {
            if (info.pkgList != null) {
                String[] pkgListCopy = Arrays.copyOf(info.pkgList, info.pkgList.length);
                Arrays.sort(pkgListCopy);
                if (Arrays.binarySearch(pkgListCopy, pluginPackageName) >= 0 && info.pid != android.os.Process.myPid()) {
                    Log.i(TAG, "killBackgroundProcesses(%s),pkgList=%s,pid=%s", pluginPackageName, Arrays.toString(info.pkgList), info.pid);
                    android.os.Process.killProcess(info.pid);
                    success = true;
                }
            }
        }
        return success;
    }

    @Override
    public boolean killApplicationProcess(String pluginPackageName) throws RemoteException {
        return killBackgroundProcesses(pluginPackageName);
    }

    @Override
    public boolean forceStopPackage(String pluginPackageName) throws RemoteException {
        return killBackgroundProcesses(pluginPackageName);
    }

    @Override
    public boolean registerApplicationCallback(IApplicationCallback callback) throws RemoteException {
        return mActivityManagerService.registerApplicationCallback(Binder.getCallingPid(), Binder.getCallingUid(), callback);
    }

    @Override
    public boolean unregisterApplicationCallback(IApplicationCallback callback) throws RemoteException {
        return mActivityManagerService.unregisterApplicationCallback(Binder.getCallingPid(), Binder.getCallingUid(), callback);
    }

    @Override
    public void onActivityCreated(ActivityInfo stubInfo, ActivityInfo targetInfo) throws RemoteException {
        mActivityManagerService.onActivityCreated(Binder.getCallingPid(), Binder.getCallingUid(), stubInfo, targetInfo);
    }

    @Override
    public void onActivityDestory(ActivityInfo stubInfo, ActivityInfo targetInfo) throws RemoteException {
        mActivityManagerService.onActivityDestory(Binder.getCallingPid(), Binder.getCallingUid(), stubInfo, targetInfo);
    }

    @Override
    public void onServiceCreated(ServiceInfo stubInfo, ServiceInfo targetInfo) throws RemoteException {
        mActivityManagerService.onServiceCreated(Binder.getCallingPid(), Binder.getCallingUid(), stubInfo, targetInfo);
    }

    @Override
    public void onServiceDestory(ServiceInfo stubInfo, ServiceInfo targetInfo) throws RemoteException {
        mActivityManagerService.onServiceDestory(Binder.getCallingPid(), Binder.getCallingUid(), stubInfo, targetInfo);
    }

    @Override
    public void onProviderCreated(ProviderInfo stubInfo, ProviderInfo targetInfo) throws RemoteException {
        mActivityManagerService.onProviderCreated(Binder.getCallingPid(), Binder.getCallingUid(), stubInfo, targetInfo);
    }

    @Override
    public void reportMyProcessName(String stubProcessName, String targetProcessName, String targetPkg) throws RemoteException {
        mActivityManagerService.onReportMyProcessName(Binder.getCallingPid(), Binder.getCallingUid(), stubProcessName, targetProcessName, targetPkg);
    }

    public void onDestroy() {
        mActivityManagerService.onDestory();
    }

    @Override
    public void onActivtyOnNewIntent(ActivityInfo stubInfo, ActivityInfo targetInfo, Intent intent) throws RemoteException {
        mActivityManagerService.onActivtyOnNewIntent(Binder.getCallingPid(), Binder.getCallingUid(), stubInfo, targetInfo, intent);
    }

    @Override
    public int getMyPid() {
        return android.os.Process.myPid();
    }

}
