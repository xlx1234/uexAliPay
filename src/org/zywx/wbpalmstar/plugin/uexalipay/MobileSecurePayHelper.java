package org.zywx.wbpalmstar.plugin.uexalipay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

public class MobileSecurePayHelper {

	private final static String server_url = "https://msp.alipay.com/x.htm";
	private ProgressDialog mProgress;
	private Context mContext;

	public MobileSecurePayHelper(Context context) {
		mContext = context;
	}

	public boolean detectPlugin(PayConfig payConfig) {
		boolean isExist = isPluginExist();
		if (!isExist) {
			// get the cacheDir.
			File cacheDir = mContext.getCacheDir();
			final String cachePath = cacheDir.getAbsolutePath() + "/temp.apk";
			mProgress = BaseHelper.showProgress(mContext, null, "正在检测安全支付服务版本", false, true);
			new Thread(new Runnable() {
				public void run() {
					// 检测是否有新的版本。
					PackageInfo apkInfo = getApkInfo(mContext, cachePath);
					String newApkdlUrl = checkNewUpdate(apkInfo);
					boolean loadOk = true;
					if(newApkdlUrl != null){
							Message msg = new Message();
							msg.what = AlixId.RQF_LOADING_APP;
							mHandler.sendMessage(msg);
							loadOk = retrieveApkFromNet(mContext, newApkdlUrl, cachePath);
							if(!loadOk){
								Message msg1 = new Message();
								msg1.what = AlixId.RQF_ERROR;
								msg1.obj = cachePath;
								mHandler.sendMessage(msg1);
							}else{
								// 提示安装
								Message msg1 = new Message();
								msg1.what = AlixId.RQF_INSTALL_CHECK;
								msg1.obj = cachePath;
								mHandler.sendMessage(msg1);
							}
					}else{
						Message msg1 = new Message();
						msg1.what = AlixId.RQF_INSTALL_CHECK;
						msg1.obj = cachePath;
						mHandler.sendMessage(msg1);
					}
					
				}
			}).start();
		}
		return isExist;
	}

	public void showInstallConfirmDialog(final Context context, final String cachePath) {
		AlertDialog.Builder tDialog = new AlertDialog.Builder(context);
		tDialog.setIcon(android.R.drawable.ic_dialog_alert);
		tDialog.setTitle("安装提示");
		tDialog.setMessage("未安装安全支付服务\n为保证您的交易安全，需要您安装支付宝安全支付服务，才能进行付款。\n\n点击确定，立即安装。");
		tDialog.setPositiveButton("确定",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						// 修改apk权限
						BaseHelper.chmod("777", cachePath);
						// install the apk.
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.setDataAndType(Uri.parse("file://" + cachePath),
								"application/vnd.android.package-archive");
						context.startActivity(intent);
					}
				});

		tDialog.setNegativeButton("取消",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});

		tDialog.show();
	}

	public boolean isPluginExist() {
		PackageManager manager = mContext.getPackageManager();
		List<PackageInfo> pkgList = manager.getInstalledPackages(0);
		for (int i = 0; i < pkgList.size(); i++) {
			PackageInfo pI = pkgList.get(i);
			if (pI.packageName.equalsIgnoreCase("com.alipay.android.app"))
				return true;
		}

		return false;
	}

	// 捆绑安装
	public boolean retrieveApkFromAssets(Context context, String fileName,
			String path) {
		boolean bRet = false;
		try {
			InputStream is = context.getAssets().open("widget/wgtRes/" + fileName);

			File file = new File(path);
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);

			byte[] temp = new byte[1024];
			int i = 0;
			while ((i = is.read(temp)) > 0) {
				fos.write(temp, 0, i);
			}

			fos.close();
			is.close();
			bRet = true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return bRet;
	}

	/**
	 * 获取未安装的APK信息
	 * @param context
	 * @param archiveFilePath
	 *            APK文件的路径。如：/sdcard/download/XX.apk
	 */
	public static PackageInfo getApkInfo(Context context, String archiveFilePath) {
		PackageManager pm = context.getPackageManager();
		PackageInfo apkInfo = pm.getPackageArchiveInfo(archiveFilePath,
				PackageManager.GET_META_DATA);
		return apkInfo;
	}

	// 检查是否有新的版本，如果有，返回apk的下载地址。
	public String checkNewUpdate(PackageInfo packageInfo) {
		String url = null;
		String versionName = "1.0.0";
		try {
			if(packageInfo != null){
				if(TextUtils.isEmpty(packageInfo.versionName)){
					packageInfo.versionName = "1.0.0";
				}
				versionName = packageInfo.versionName;
			}else{
				versionName = "1.0.0";
			}
			JSONObject resp = sendCheckNewUpdate(versionName);
//			 JSONObject resp = sendCheckNewUpdate("1.0.0");
			if (resp.getString("needUpdate").equalsIgnoreCase("true")) {
				url = resp.getString("updateUrl");
			}
			// else ok.
		} catch (Exception e) {
			e.printStackTrace();
		}

		return url;
	}

	public JSONObject sendCheckNewUpdate(String versionName) {
		JSONObject objResp = null;
		try {
			JSONObject req = new JSONObject();
			req.put(AlixDefine.action, AlixDefine.actionUpdate);

			JSONObject data = new JSONObject();
			data.put(AlixDefine.platform, "android");
			data.put(AlixDefine.VERSION, versionName);
			data.put(AlixDefine.partner, "");

			req.put(AlixDefine.data, data);

			objResp = sendRequest(req.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return objResp;
	}

	public JSONObject sendRequest(final String content) {
		NetworkManager nM = new NetworkManager();
		JSONObject jsonResponse = null;
		try {
			String response = null;
			synchronized (nM) {
				response = nM.SendAndWaitResponse(mContext, content, server_url);
			}
			jsonResponse = new JSONObject(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsonResponse;
	}

	// 动态下载
	public boolean retrieveApkFromNet(Context context, String strurl, String filename) {
		boolean bRet = false;
		try {
			NetworkManager nM = new NetworkManager();
			bRet = nM.urlDownloadToFile(context, strurl, filename);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return bRet;
	}

	// close the progress bar
	void closeProgress() {
		try {
			if (mProgress != null) {
				mProgress.dismiss();
				mProgress = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			try {
				switch (msg.what) {
				case AlixId.RQF_INSTALL_CHECK:
					closeProgress();
					String cachePath = (String) msg.obj;
					showInstallConfirmDialog(mContext, cachePath);
					break;
				case AlixId.RQF_LOADING_APP:
					closeProgress();
					mProgress = BaseHelper.showProgress(mContext, null, "检测到新版本,正在下载..", false, true);
					break;
				case AlixId.RQF_ERROR:
					closeProgress();
					break;
				}

				super.handleMessage(msg);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
}