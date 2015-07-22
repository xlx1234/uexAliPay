package org.zywx.wbpalmstar.plugin.uexalipay;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.alipay.sdk.app.PayTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PFAlixpay {

	static final String PARTNER = "partner=";
	static final String SELLER_ID = "seller_id=";
	static final String TRADE_NUM = "out_trade_no=";
	static final String SUBJECT = "subject=";
	static final String BODY = "body=";
	static final String TOTAL_FEE = "total_fee=";
	static final String NOTIFY_URL = "notify_url=";
	static final String SIGN_TYPE = "sign_type=";
	static final String SIGN = "&sign=";
	static final String RSA = "RSA";
	static final String SERVICE = "service=";
	static final String PAYMENT_TYPE = "payment_type=";
	static final String _INPUT_CHARSET = "_input_charset=";
	static final String IT_B_PAY    = "it_b_pay=";
	static final String SHOW_URL    = "show_url=";
	private Context mContext;
	private PayConfig mPayConfig;
	
	private static PFAlixpay instance;
	
	private PFAlixpay(Context context){
		mContext = context;
	}
	
	public static PFAlixpay get(Context context){
		if(null == instance){
			instance = new PFAlixpay(context);
		}
		return instance;
	}

	public void pay(String inTradeNum, String inSubject, String inBody,
			String inTotalFee, final Handler inCallBack, PayConfig payConfig) {
		boolean ret = false;
		try{
			String orderInfo = getOrderInfo(inTradeNum, inSubject, inBody, inTotalFee, payConfig);
			String signType = getSignType();
			String sign = sign(signType, orderInfo);
			sign = URLEncoder.encode(sign, "UTF-8");
            final String submitInfo = orderInfo + SIGN + "\"" + sign + "\"&" + getSignType();

			Runnable payRunnable = new Runnable() {

				@Override
				public void run() {
					// 构造PayTask 对象
					PayTask alipay = new PayTask((Activity) mContext);
					// 调用支付接口
					String result = alipay.pay(submitInfo);

					Message msg = new Message();
					msg.what = AlixId.RQF_PAY;
					msg.obj = result;
                    inCallBack.sendMessage(msg);
				}
			};

			Thread payThread = new Thread(payRunnable);
			payThread.start();
		} catch (Exception e) {
			Toast.makeText(mContext, "算法异常!", Toast.LENGTH_SHORT).show();
		}
	}
	
	public void fastPay(final String submitInfo, final Handler inCallBack) {
		try {
            Runnable payRunnable = new Runnable() {

                @Override
                public void run() {
                    // 构造PayTask 对象
                    PayTask alipay = new PayTask((Activity) mContext);
                    // 调用支付接口
                    String result = alipay.pay(submitInfo);

                    Message msg = new Message();
                    msg.what = AlixId.RQF_PAY;
                    msg.obj = result;
                    inCallBack.sendMessage(msg);
                }
            };

            Thread payThread = new Thread(payRunnable);
            payThread.start();
		} catch (Exception e) {
			Toast.makeText(mContext, "支付信息错误!", Toast.LENGTH_SHORT).show();
		}
	}
	
	private String getOrderInfo(String inTradeNum, String inSubject, String inBody, String inTotalFee, PayConfig payConfig){
		boolean isDebug = false;
		if(isDebug){
			inTradeNum = getTradeNum();
			inTotalFee = "0.01";
		}
		String orderInfo = PARTNER + "\"" + payConfig.mPartner + "\"";
		orderInfo += "&";
		orderInfo += SELLER_ID + "\"" + payConfig.mSeller_id + "\"";
		orderInfo += "&";
		orderInfo += TRADE_NUM + "\"" + inTradeNum + "\"";
		orderInfo += "&";
		orderInfo += SUBJECT + "\"" + inSubject + "\"";
		orderInfo += "&";
		orderInfo += BODY + "\"" + inBody + "\"";
		orderInfo += "&";
		orderInfo += TOTAL_FEE + "\"" + inTotalFee + "\"";
		orderInfo += "&";
		orderInfo += NOTIFY_URL + "\"" + URLEncoder.encode(payConfig.mNotifyUrl) + "\"";
		orderInfo += "&";
		orderInfo += SERVICE + "\"" + "mobile.securitypay.pay" + "\"";
		orderInfo += "&";
		orderInfo += PAYMENT_TYPE + "\"" + "1" + "\"";
		orderInfo += "&";
		orderInfo += _INPUT_CHARSET + "\"" + "utf-8" + "\"";
		orderInfo += "&";
		orderInfo += IT_B_PAY + "\"" + "30m" + "\"";
		return orderInfo;
	}
	
	private String getSignType() {

		return SIGN_TYPE + "\"" + RSA + "\"";
	}
	
	private String sign(String signType, String content) {
		return Rsa.sign(content, mPayConfig.mRsaPrivate);
	}
	
	private String getTradeNum() { //use to test
		SimpleDateFormat format = new SimpleDateFormat("MMddHHmmss");
		Date date = new Date();
		String strKey = format.format(date);
		java.util.Random r = new java.util.Random();
		strKey = strKey + r.nextInt();
		strKey = strKey.substring(0, 15);
		return strKey;
	}
	
	public PayConfig getPayConfig() {
		if (null == mPayConfig) {
			return null;
		}
		return mPayConfig;
	}

	public void setPayConfig(PayConfig config) {
		mPayConfig = config;
	}

	private JSONObject string2JSON(String str, String split) throws JSONException {
		JSONObject json = new JSONObject();
		String[] arrStr = str.split(split);
		for (int i = 0; i < arrStr.length; i++) {
			String[] arrKeyValue = arrStr[i].split("=");
			json.put(arrKeyValue[0], arrStr[i].substring(arrKeyValue[0].length() + 1));
		}
		return json;
	}


}