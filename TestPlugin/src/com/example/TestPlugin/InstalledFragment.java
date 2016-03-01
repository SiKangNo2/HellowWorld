package com.example.TestPlugin;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.morgoo.droidplugin.pm.PluginManager;
import com.morgoo.helper.Log;

import java.util.List;

public class InstalledFragment extends ListFragment implements ServiceConnection {

    private ArrayAdapter<ApkItem> adapter;

    final Handler handler = new Handler();

    public InstalledFragment() {
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ApkItem item = adapter.getItem(position);
        //加载apk
        if (v.getId() == R.id.button2) {
            PackageManager pm = getActivity().getPackageManager();
            //跳转到apk的 MainActivity
            Intent intent = pm.getLaunchIntentForPackage(item.packageInfo.packageName);
            //为MainActivity开启一个新的栈
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);//执行挑转
        } else if (v.getId() == R.id.button3) {
            //卸载
            doUninstall(item);
        }
    }
    //删除APK
    private void doUninstall(final ApkItem item) {
        AlertDialog.Builder builder = new Builder(getActivity());
        builder.setTitle("警告，你确定要删除么？");
        builder.setMessage("警告，你确定要删除" + item.title + "么？");
        builder.setNegativeButton("删除", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!PluginManager.getInstance().isConnected()) {
                    Toast.makeText(getActivity(), "服务未连接", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        PluginManager.getInstance().deletePackage(item.packageInfo.packageName, 0);
                        Toast.makeText(getActivity(), "删除完成", Toast.LENGTH_SHORT).show();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        builder.setNeutralButton("取消", null);
        builder.show();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        startLoad();
    }

    //开始加载
    private void startLoad() {
        new Thread("ApkScanner") {
            @Override
            public void run() {
                try {
                    //
                    final List<PackageInfo> infos = PluginManager.getInstance().getInstalledPackages(0);
                    final PackageManager pm = getActivity().getPackageManager();
                    for (final PackageInfo info : infos) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                adapter.add(new ApkItem(pm, info, info.applicationInfo.publicSourceDir));
                            }
                        });
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setListShown(true);
                    }
                });
            }
        }.start();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
    }

    private MyBroadcastReceiver mMyBroadcastReceiver = new MyBroadcastReceiver();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //更新UI广播
        mMyBroadcastReceiver.registerReceiver(getActivity().getApplication());
        adapter = new ArrayAdapter<ApkItem>(getActivity(), 0) {
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getActivity()).inflate(R.layout.apk_item, null);
                }
                ApkItem item = getItem(position);
                //获取apk图标
                ImageView icon = (ImageView) convertView.findViewById(R.id.imageView);
                icon.setImageDrawable(item.icon);
                //获取apk名称
                TextView title = (TextView) convertView.findViewById(R.id.textView1);
                title.setText(item.title);
                //获取版本
                final TextView version = (TextView) convertView.findViewById(R.id.textView2);
                version.setText(String.format("%s(%s)", item.versionName, item.versionCode));
                //加载apk
                TextView btn = (TextView) convertView.findViewById(R.id.button2);
                btn.setText("打开");
                btn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        onListItemClick(getListView(), view, position, getItemId(position));
                    }
                });
                //删除apk
                btn = (TextView) convertView.findViewById(R.id.button3);
                btn.setText("卸载");
                btn.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        onListItemClick(getListView(), view, position, getItemId(position));
                    }
                });

                return convertView;
            }
        };

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText("没有安装插件");
        setListAdapter(adapter);
        setListShown(false);
        getListView().setOnItemClickListener(null);
        //插件管理服务开启后开始初始化已安装的插件
        if (PluginManager.getInstance().isConnected()) {
            startLoad();
        } else {
            PluginManager.getInstance().addServiceConnection(this);
        }
    }

    @Override
    public void onDestroy() {
        PluginManager.getInstance().removeServiceConnection(this);
        mMyBroadcastReceiver.unregisterReceiver(getActivity().getApplication());
        super.onDestroy();
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {

        void registerReceiver(Context con) {
            //注册广播，接受添加和删除包的指令
            IntentFilter f = new IntentFilter();
            f.addAction(PluginManager.ACTION_PACKAGE_ADDED);
            f.addAction(PluginManager.ACTION_PACKAGE_REMOVED);
            f.addDataScheme("package");
            con.registerReceiver(this, f);
        }

        void unregisterReceiver(Context con) {
            con.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            //添加apk
            if (PluginManager.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                try {
                    PackageManager pm = getActivity().getPackageManager();
                    String pkg = intent.getData().getAuthority();
                    PackageInfo info = PluginManager.getInstance().getPackageInfo(pkg, 0);
                    adapter.add(new ApkItem(pm, info, info.applicationInfo.publicSourceDir));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //删除
            } else if (PluginManager.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                String pkg = intent.getData().getAuthority();
                int N = adapter.getCount();
                ApkItem iremovedItem = null;
                for (int i = 0; i < N; i++) {
                    ApkItem item = adapter.getItem(i);
                    if (TextUtils.equals(item.packageInfo.packageName, pkg)) {
                        iremovedItem = item;
                        break;
                    }
                }
                if (iremovedItem != null) {
                    adapter.remove(iremovedItem);
                }
            }
        }
    }
}