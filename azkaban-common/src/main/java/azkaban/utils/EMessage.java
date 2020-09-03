package azkaban.utils;

import azkaban.alert.Alerter;
import azkaban.executor.*;
import azkaban.sla.SlaOption;
import azkaban.sms.OdsClient;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class EMessage implements Alerter {
    private static final Logger logger = Logger.getLogger(EMessage.class);

    private Props props;

    private String odsHost;
    private String merchantNo;
    private String merchantName;
    private String desKey;
    private String signKey;
    private String orgNo;
    private String azkabanName;

    public EMessage(Props props) {
        this.props = props;
        if (props != null && props.size() != 0) {
            this.odsHost = props.getString("sms.odsHost", "http://test/ods/sms/sender_verify");
            this.merchantNo = props.getString("sms.merchantNo", "");
            this.merchantName = props.getString("sms.merchantName", "大数据调度平台");
            this.desKey = props.getString("sms.desKey", "");
            this.signKey = props.getString("sms.signKey", "");
            this.orgNo = props.getString("sms.orgNo", "");
            this.azkabanName = props.getString("azkaban.name", "azkaban");
        }
    }

    @Override
    public void alertOnSuccess(ExecutableFlow exflow) throws Exception {
        this.logger.info("=====================执行成功，发送手机短信===========================");
        ExecutionOptions executionOptions = exflow.getExecutionOptions();
        ArrayList<String> successSms = executionOptions.getSuccessSms();
        sendSMS(successSms, getSuccessMSG(exflow));

    }

    public void sendSMS(ArrayList<String> phones, String msg) {
        JSONObject params = new JSONObject();
        if (phones != null && phones.size() != 0) {
            for (String phone : phones
            ) {
                this.logger.info("手机号=======================》" + phone);
                params.put("phone", phone);
                params.put("msg", msg);
                JSONObject result = OdsClient.odsAPiJson(params, odsHost, orgNo, merchantNo, merchantName, signKey, desKey);
                logger.info("返回短信发送结果======》" + result);
            }
        } else {
            logger.warn("没有手机号码");
        }

    }

    /***
     * 组装短信内容
     * @param exflow
     * @return
     */
    public String getSuccessMSG(ExecutableFlow exflow) {
        if (exflow == null) {
            return null;
        }
        String message = "\n\t项目%s执行成功" +
                "\n\t任务ID：%s\n\t项目名：%s\n\tflow名：%s" +
                "\n\t任务开始时间：%s\n\t任务结束时间：%s\n\t任务总耗时:%s" +
                "\n\t任务状态：%s";
        //任务id
        int executionId = exflow.getExecutionId();
        //job名
        String flowId = exflow.getFlowId();
        //项目名
        String projectName = exflow.getProjectName();
        //开始时间
        String startTime = TimeUtils.formatDateTimeZone(exflow.getStartTime());
        //任务结束时间
        String endTime = TimeUtils.formatDateTimeZone(exflow.getEndTime());
        //总耗时
        String duration = TimeUtils.formatDuration(exflow.getStartTime(), exflow.getEndTime());
        //任务状态
        Status status = exflow.getStatus();
        String format = String.format(message, projectName, executionId, projectName, flowId, startTime, endTime, duration, status);
        return format;
    }

    public String getErrorMSG(ExecutableFlow exflow, String... extraReasons) {
        if (exflow == null) {
            return null;
        }

        String message = "\n\t项目%s执行失败" +
                "\n\t任务ID：%s\n\t项目名：%s\n\tflow名：%s" +
                "\n\t任务开始时间：%s\n\t任务结束时间：%s\n\t任务总耗时:%s" +
                "\n\t失败的job：%s"+
                "\n\t任务状态：%s" +
                "\n\t错误日志：%s";
        //任务id
        int executionId = exflow.getExecutionId();
        //job名
        String flowId = exflow.getFlowId();
        //项目名
        String projectName = exflow.getProjectName();
        //开始时间
        String startTime = TimeUtils.formatDateTimeZone(exflow.getStartTime());
        //任务结束时间
        String endTime = TimeUtils.formatDateTimeZone(exflow.getEndTime());
        //总耗时
        String duration = TimeUtils.formatDuration(exflow.getStartTime(), exflow.getEndTime());
        //任务状态
        Status status = exflow.getStatus();
//        错误的job
        final List<String> failedJobs = findFailedJobs(exflow);

        List<String> strings;
        if(extraReasons!=null&&extraReasons.length>0){
             strings = Arrays.asList(extraReasons);
        }else {
            strings=new ArrayList<>();
            strings.add("");
        }
        strings.forEach(x->x=x+"\n\t");
        String substring;
        if(strings.toString().length()>800){
            substring = strings.toString().substring(0, 800);
        }else {
            substring = strings.toString();
        }
        logger.debug("错误信息========》"+Arrays.toString(extraReasons));


        String format = String.format(message, projectName, executionId, projectName, flowId, startTime, endTime, duration, failedJobs.toString(),status, substring);
        return format;
    }
    private static List<String> findFailedJobs(final ExecutableFlow flow) {
        final ArrayList<String> failedJobs = new ArrayList<>();
        for (final ExecutableNode node : flow.getExecutableNodes()) {
            if (node.getStatus() == Status.FAILED) {
                failedJobs.add(node.getId());
            }
        }
        return failedJobs;
    }
    @Override
    public void alertOnError(ExecutableFlow exflow, String... extraReasons) throws Exception {
        this.logger.info("=====================执行失败，发送手机短信===========================");
        ExecutionOptions executionOptions = exflow.getExecutionOptions();
        ArrayList<String> failureSms = executionOptions.getFailureSms();
        sendSMS(failureSms, getErrorMSG(exflow, extraReasons));
    }

    @Override
    public void alertOnFirstError(ExecutableFlow exflow) throws Exception {
        this.logger.info("=====================执行失败，发送手机短信===========================");
        ExecutionOptions executionOptions = exflow.getExecutionOptions();
        ArrayList<String> failureSms = executionOptions.getFailureSms();
        sendSMS(failureSms, getErrorMSG(exflow, ""));
    }

    @Override
    public void alertOnSla(SlaOption slaOption, String slaMessage) throws Exception {
        this.logger.info("=====================alertOnSla，发送手机短信===========================");
    }

    @Override
    public void alertOnFailedUpdate(Executor executor, List<ExecutableFlow> executions, ExecutorManagerException e) {
        this.logger.info("=====================alertOnFailedUpdate，发送手机短信===========================");
        if(executions!=null&&executions.size()>0){
            for (ExecutableFlow execution:executions
                 ) {
                ExecutionOptions executionOptions = execution.getExecutionOptions();
                ArrayList<String> failureSms = executionOptions.getFailureSms();
                sendSMS(failureSms, getErrorMSG(execution, ""));
            }
        }
    }
}
