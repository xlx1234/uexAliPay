package org.zywx.wbpalmstar.plugin.uexalipay.vo;

import java.io.Serializable;

/**
 * Created by ylt on 2016/12/23.
 */
public class GeneratePayOrderVO implements Serializable {

    public String private_key;
    public String app_id;
    public String notify_url;
    public BizContentVO biz_content;
}
