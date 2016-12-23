package org.zywx.wbpalmstar.plugin.uexalipay;

import org.json.JSONObject;

public class ResultChecker {

    public static final int RESULT_INVALID_PARAM = 0;
    public static final int RESULT_CHECK_SIGN_FAILED = 1;
    public static final int RESULT_CHECK_SIGN_SUCCEED = 2;

    private JSONObject mJSONContent;

    public ResultChecker(String content) {
        mJSONContent = BaseHelper.string2JSON(content, ";");
    }

    String getSuccess() {
        String success = null;
        try {
            String result = mJSONContent.getString("result");
            result = result.substring(1, result.length() - 1);
            JSONObject objResult = BaseHelper.string2JSON(result, "&");
            success = objResult.getString("success");
            success = success.replace("\"", "");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return success;
    }

    public int checkSign(PayConfig payConfig) {
        int retVal = RESULT_CHECK_SIGN_SUCCEED;
        try {
            String result = mJSONContent.getString("result");
            if (0 == result.replace("{", "").replace("}", "").length()) {
                return RESULT_INVALID_PARAM;
            }
            result = result.substring(1, result.length() - 1);
            int iSignContentEnd = result.indexOf("&sign_type=");
            String signContent = result.substring(0, iSignContentEnd);
            JSONObject objResult = BaseHelper.string2JSON(result, "&");
            String signType = objResult.getString("sign_type");
            signType = signType.replace("\"", "");
            String sign = objResult.getString("sign");
            sign = sign.replace("\"", "");
            if (signType.equalsIgnoreCase("RSA")) {
                if (!Rsa.doCheck(signContent, sign, payConfig == null ? Keys.PUBLIC : payConfig.mRsaPublic))
                    retVal = RESULT_CHECK_SIGN_FAILED;
            }
        } catch (Exception e) {
            retVal = RESULT_INVALID_PARAM;
            e.printStackTrace();
        }

        return retVal;
    }

    public JSONObject getJSONResult() {

        return mJSONContent;
    }

    public boolean isPayOk(PayConfig payConfig) {
        boolean isPayOk = false;
        String success = getSuccess();
        if (success.equalsIgnoreCase("true") && checkSign(payConfig) == RESULT_CHECK_SIGN_SUCCEED)
            isPayOk = true;

        return isPayOk;
    }
}