package org.zywx.wbpalmstar.plugin.uexalipay.vo;

import java.io.Serializable;

/**
 * Created by ylt on 2016/12/23.
 */
public class BizContentVO implements Serializable {

    public String subject;

    public String body;

    public String out_trade_no;

    public String total_amount;

    public String seller_id;

    public String product_code = "QUICK_MSECURITY_PAY";

    public String timeout_express = "30m";

}
