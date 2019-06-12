package org.zywx.wbpalmstar.plugin.uexalipay;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.alipay.android.app.lib.OrderInfoUtil2_0;
import com.alipay.sdk.app.AuthTask;

import org.zywx.wbpalmstar.engine.DataHelper;
import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;
import org.zywx.wbpalmstar.engine.universalex.EUExCallback;
import org.zywx.wbpalmstar.plugin.uexalipay.vo.GeneratePayOrderVO;
import org.zywx.wbpalmstar.plugin.uexalipay.vo.GetAuthInfoVO;

import java.util.Map;

public class EUExAliPay extends EUExBase {

    static final String onFunction = "uexAliPay.onStatus";
    static final String SCRIPT_HEADER = "javascript:";

    private PFPayCallBack m_eCallBack;
    private PFPayWithOrderCallBack mPayWithOrderCallBack;
    private boolean m_paying;

    private static final String PAY_SUCCESS = "支付成功";
    private static final String PAY_FAILED = "支付失败";
    private static final String PAY_CANCEL = "支付取消";
    private static final String CONFIG_ERROR = "config error";

    private String payFuncId; //pay函数的回调


    public EUExAliPay(Context context, EBrowserView inParent) {
        super(context, inParent);
    }


    /**
     * 初始化商家信息
     *
     * @param params
     */
    public boolean setPayInfo(String[] params) {
        if (params.length < 5) {
            return false;
        }
        PFAlixpay alipay = PFAlixpay.get(mContext);
        String inPartner = params[0];
        String inSeller = params[1];
        String inRsaPrivate = params[2];
        String inRsaPublic = params[3];
        String inNotifyUrl = params[4];
        PayConfig congfig = new PayConfig(inPartner, inSeller, inRsaPrivate,
                inRsaPublic, inNotifyUrl, null);
        alipay.setPayConfig(congfig);
        return true;
    }

    /**
     * 支付
     *
     * @param params
     */
    public void pay(String[] params) {
        if (null == params || params.length < 4) {
            return;
        }
        String inTradeNum = params[0];
        String inSubject = params[1];
        String inBody = params[2];
        String inTotalFee = params[3];
        if (params.length == 5) {
            payFuncId = params[4];
        }
        // 支付状态：0-成功,1-支付中,2-失败,3-支付插件不完整
        if (m_paying)
            return;
        m_paying = true;
        if (null == m_eCallBack) {
            m_eCallBack = new PFPayCallBack();
        }
        try {
            PFAlixpay alipay = PFAlixpay.get(mContext);
            PayConfig config = alipay.getPayConfig();
            if (null == config) {
                callback(EUExCallback.F_C_PAYFAILED, CONFIG_ERROR);
                m_paying = false;
                return;
            }
            alipay.pay(inTradeNum, inSubject, inBody, inTotalFee, m_eCallBack, config);
        } catch (Exception e) {
            m_paying = false;
            errorCallback(0, 0, e.toString());
        }
    }

    public void payWithOrder(String[] params) {
        gotoPay(params);
    }

    public String generatePayOrder(String[] params) {
        GeneratePayOrderVO payOrderVO = DataHelper.gson.fromJson(params[0], GeneratePayOrderVO.class);
        Map<String, String> payParams = OrderInfoUtil2_0.buildOrderParamMap(payOrderVO);
        String orderParam = OrderInfoUtil2_0.buildOrderParam(payParams);
        String sign = OrderInfoUtil2_0.getSign(payParams, payOrderVO.private_key,false);
        return orderParam + "&" + sign;
    }

    public void gotoPay(String[] params) {
        if (params == null || params.length < 1) {
            return;
        }
        String submitInfo = params[0];
        if (params.length == 2) {
            payFuncId = params[1];
        }
        if (m_paying)
            return;
        m_paying = true;
        if (null == mPayWithOrderCallBack) {
            mPayWithOrderCallBack = new PFPayWithOrderCallBack();
        }
        try {
            PFAlixpay alipay = PFAlixpay.get(mContext);
            alipay.fastPay(submitInfo, mPayWithOrderCallBack);
        } catch (Exception e) {
            m_paying = false;
            errorCallback(0, 0, e.toString());
        }
    }

    private class PFPayCallBack extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                String strRet = (String) msg.obj;
                switch (msg.what) {
                    case AlixId.RQF_PAY: {
                        try {
                            int status = 0;
                            String callbackMsg = null;
                            ResultChecker resultChecker = new ResultChecker(strRet);
                            int retVal = resultChecker.checkSign(PFAlixpay.get(mContext).getPayConfig());
                            if (retVal == ResultChecker.RESULT_CHECK_SIGN_FAILED) { // 订单信息被非法篡改
                                status = EUExCallback.F_C_PAYFAILED;
                                callbackMsg = PAY_FAILED;
                                callback(status, callbackMsg);
                                return;
                            } else {
                                String code = (String) resultChecker.getJSONResult().get("resultStatus");
                                int resultCode = Integer.valueOf(code.substring(1, code.length() - 1));
                                switch (resultCode) {
                                    case 9000:// 支付成功
                                        if (resultChecker.isPayOk(PFAlixpay.get(mContext).getPayConfig())) {
                                            status = EUExCallback.F_C_PAYSUCCSS;
                                            callbackMsg = PAY_SUCCESS;
                                        } else {
                                            status = EUExCallback.F_C_PAYFAILED;
                                            callbackMsg = PAY_FAILED;
                                        }
                                        break;
                                    case 6001:// 用户中途取消支付操作
                                        status = 4;
                                        callbackMsg = PAY_CANCEL;
                                        break;
                                    case 4000:// 系统异常
                                    case 4001:// 数据格式不正确
                                    case 4003:// 该用户绑定的支付宝账户被冻结或不允许支付
                                    case 4004:// 该用户已解除绑定
                                    case 4005:// 绑定失败或没有绑定
                                    case 4006:// 订单支付失败
                                    case 4010:// 重新绑定账户
                                    case 6000:// 支付服务正在进行升级操作
                                    case 6002:// 网络错误
                                        status = EUExCallback.F_C_PAYFAILED;
                                        callbackMsg = PAY_FAILED;
                                        break;
                                }
                                callback(status, callbackMsg);
                            }
                        } catch (Exception e) { // 异常 提示信息为 strRet
                            e.printStackTrace();
                            errorCallback(0, 0, e.toString() + "//" + strRet);
                        }
                    }
                    m_paying = false;
                    break;
                }
                super.handleMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class PFPayWithOrderCallBack extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                String strRet = (String) msg.obj;
                String js = "";
                switch (msg.what) {
                    case AlixId.RQF_PAY: {
                        try {
                            int status = 0;
                            String callbackMsg = null;
                            ResultChecker resultChecker = new ResultChecker(strRet);
                            String code = (String) resultChecker.getJSONResult().get("resultStatus");
                            int resultCode = Integer.valueOf(code.substring(1, code.length() - 1));
                            switch (resultCode) {
                                case 9000:// 支付成功
                                    String result = (String) resultChecker.getJSONResult().get("result");
                                    result = result.substring(1, result.length() - 1);
                                    status = EUExCallback.F_C_PAYSUCCSS;
                                    callbackMsg = result;
                                    break;
                                case 6001:// 用户中途取消支付操作
                                    status = 4;
                                    callbackMsg = PAY_CANCEL;
                                    break;
                                case 4000:// 系统异常
                                case 4001:// 数据格式不正确
                                case 4003:// 该用户绑定的支付宝账户被冻结或不允许支付
                                case 4004:// 该用户已解除绑定
                                case 4005:// 绑定失败或没有绑定
                                case 4006:// 订单支付失败
                                case 4010:// 重新绑定账户
                                case 6000:// 支付服务正在进行升级操作
                                case 6002:// 网络错误
                                default:
                                    status = resultCode;
                                    String memo = (String) resultChecker.getJSONResult().get("memo");
                                    callbackMsg = memo.substring(1, memo.length() - 1);
                                    break;
                            }
                            callback(status, callbackMsg);
                        } catch (Exception e) { // 异常 提示信息为 strRet
                            e.printStackTrace();
                            errorCallback(0, 0, e.toString() + "//" + strRet);
                        }
                    }
                    m_paying = false;
                    break;
                }
                super.handleMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public void auth(String[] params){

        final String authInfo = params[0];
        int callbackId=-1;
        if (params.length>1){
            callbackId= Integer.parseInt(params[1]);
        }
        final int finalCallbackId = callbackId;
        Runnable authRunnable = new Runnable() {

            @Override
            public void run() {
                // 构造AuthTask 对象
                AuthTask authTask = new AuthTask((Activity) mContext);
                // 调用授权接口，获取授权结果
                Map<String, String> result = authTask.authV2(authInfo, true);

                AuthResult authResult = new AuthResult(result, true);
                String resultStatus = authResult.getResultStatus();

                // 判断resultStatus 为“9000”且result_code
                // 为“200”则代表授权成功，具体状态码代表含义可参考授权接口文档
                if (TextUtils.equals(resultStatus, "9000") && TextUtils.equals(authResult.getResultCode(), "200")) {
                    // 获取alipay_open_id，调支付时作为参数extern_token 的value
                    // 传入，则支付账户为该授权账户
//                    Toast.makeText(mContext,
//                            "授权成功\n" + String.format("authCode:%s", authResult.getAuthCode()), Toast.LENGTH_SHORT)
//                            .show();
                    callbackToJs(finalCallbackId,false,0,DataHelper.gson.toJsonTree(authResult));
                } else {
                    // 其他状态值则为授权失败
//                    Toast.makeText(mContext,
//                            "授权失败" + String.format("authCode:%s", authResult.getAuthCode()), Toast.LENGTH_SHORT).show();
                    callbackToJs(finalCallbackId,false,1,DataHelper.gson.toJsonTree(authResult));
                }
            }
        };

        // 必须异步调用
        Thread authThread = new Thread(authRunnable);
        authThread.start();
    }

    private void callback(int status, String msg) {
        String js = SCRIPT_HEADER + "if(" + onFunction + "){" + onFunction + "(" + status + ",'" + msg + "');}";
        onCallback(js);
        if (null != payFuncId) {
            callbackToJs(Integer.parseInt(payFuncId), false, status, msg);
        }
    }

    public String getAuthInfo(String[] params){

        GetAuthInfoVO getAuthInfoVO=DataHelper.gson.fromJson(params[0],GetAuthInfoVO.class);
        Map<String, String> authInfoMap = OrderInfoUtil2_0.buildAuthInfoMap(getAuthInfoVO.pid, getAuthInfoVO.appId, getAuthInfoVO.targetId,
                getAuthInfoVO.rsa2);
        String info = OrderInfoUtil2_0.buildOrderParam(authInfoMap);

        String sign = OrderInfoUtil2_0.getSign(authInfoMap, getAuthInfoVO.rsaPrivate, getAuthInfoVO.rsa2);
        final String authInfo = info + "&" + sign;
        return authInfo;
    }

    @Override
    protected boolean clean() {

        m_paying = false;
        if (null != m_eCallBack) {
            m_eCallBack = null;
        }
        return true;
    }

}