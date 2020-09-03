package azkaban.sms;

import com.alibaba.fastjson.JSONObject;

public class OdsClient {


    /**
     * 参数按JSON对象进行传递
     *
     * @param params
     * @param orgNo
     * @return
     */
    public static JSONObject odsAPiJson(JSONObject params, String host, String orgNo, String merchantNo, String merchantName, String signKey, String desKey) {
        try {
            JSONObject content = new JSONObject();
            JSONObject auth = new JSONObject();
            auth.put("orgNo", orgNo);
            content.put("auth", auth);
            content.put("data", params);
            JSONObject postParams = OdsSecurityUtil.packageMsg(content.toJSONString(), desKey,
                    signKey, merchantNo, merchantName, false);
            String result = OdsHttpClientUtil.doPost(host, postParams);
            return JSONObject.parseObject(result);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}