package org.zywx.wbpalmstar.plugin.uexalipay;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;
import org.zywx.wbpalmstar.engine.universalex.EUExCallback;

public class EUExAliPay extends EUExBase {

    private static final String onFunction = "uexAliPay.onStatus";
    private static final String SCRIPT_HEADER = "javascript:";

    private static final String PAY_SUCCESS = "支付成功";
    private static final String PAY_FAILED = "支付失败";
    private static final String PAY_CANCEL = "支付取消";
    private static final String CONFIG_ERROR = "config error";

    private PFPayCallBack m_eCallBack;
    private PFPayWithOrderCallBack mPayWithOrderCallBack;
    private boolean m_paying;

    public EUExAliPay(Context context, EBrowserView inParent) {
        super(context, inParent);
    }

    /**
     * 初始化商家信息
     *
     * @param params
     */
    public void setPayInfo(String[] params) {
        if (params.length < 5) {
            return;
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
    }

    /**
     * 支付
     *
     * @param params
     */
    public void pay(String[] params) {
        if (params.length < 4) {
            return;
        }
        String inTradeNum = params[0];
        String inSubject = params[1];
        String inBody = params[2];
        String inTotalFee = params[3];
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

    public void gotoPay(String[] params) {
        if (params == null || params.length != 1) {
            return;
        }
        String submitInfo = params[0];
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

    private void callback(int status, String msg) {
        String js = SCRIPT_HEADER + "if(" + onFunction + "){" + onFunction + "(" + status + ",'" + msg + "');}";
        onCallback(js);
//        if (null != payFuncId) {
//            callbackToJs(Integer.parseInt(payFuncId), false, status, msg);
//        }
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
                            int retVal = resultChecker.checkSign(PFAlixpay.get(mContext)
                                    .getPayConfig());
                            if (retVal == ResultChecker.RESULT_CHECK_SIGN_FAILED) { // 订单信息被非法篡改
                                status = EUExCallback.F_C_PAYFAILED;
                                callbackMsg = PAY_FAILED;
                                callback(status, callbackMsg);
                                return;
                            } else {
                                String code = (String) resultChecker.getJSONResult().get
                                        ("resultStatus");
                                int resultCode = Integer.valueOf(code.substring(1, code.length()
                                        - 1));
                                switch (resultCode) {

                                    /*摘自支付宝官：方为了简化集成流程，商户可以将同步结果仅仅作为一个支付结束的通知（忽略执行校验），实际支付是否成功，完全依赖服务端异步通知。
                                     * 故此处收到9000不再做其他校验*/
                                    case 9000:// 支付成功
                                        if (resultChecker.isPayOk(PFAlixpay.get(mContext)
                                                .getPayConfig())) {
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
                            String code = (String) resultChecker.getJSONResult().get
                                    ("resultStatus");
                            int resultCode = Integer.valueOf(code.substring(1, code.length() - 1));
                            switch (resultCode) {
                                case 9000:// 支付成功
                                    String result = (String) resultChecker.getJSONResult().get
                                            ("result");
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
                                    String memo = (String) resultChecker.getJSONResult().get
                                            ("memo");
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

    @Override
    protected boolean clean() {

        m_paying = false;
        if (null != m_eCallBack) {
            m_eCallBack = null;
        }
        if (null != mPayWithOrderCallBack) {
            mPayWithOrderCallBack = null;
        }
        return true;
    }

}