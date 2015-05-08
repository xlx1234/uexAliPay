package org.zywx.wbpalmstar.plugin.uexalipay;

public class PayConfig {
	public String mPartner;
	public String mSeller_id;
	public String mRsaPrivate;
	public String mRsaPublic;
	public String mNotifyUrl;
	public String mPluginName;
	public String appScheme;//兼容ios,未使用

	public PayConfig(String partner, String seller, String rsaPrivate, String rsaPublic, String notifyUrl, String pluginName){
		mPartner = partner;
		mSeller_id = seller;
		mRsaPrivate = rsaPrivate;
		mRsaPublic = Keys.PUBLIC;
		mNotifyUrl = notifyUrl;
		mPluginName = pluginName;
	}
}