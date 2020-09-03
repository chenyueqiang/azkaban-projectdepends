/*
 * Copyright 2018 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.executor;

import static java.util.Objects.requireNonNull;

import azkaban.Constants.ConfigurationKeys;
import azkaban.alert.Alerter;
import azkaban.utils.AuthenticationUtils;
import azkaban.utils.FileIOUtils;
import azkaban.utils.Props;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utils for controlling executions.
 */
public class ExecutionControllerUtils {

    private static final Logger logger = LoggerFactory.getLogger(
            ExecutionControllerUtils.class);
    private static final String SPARK_JOB_TYPE = "spark";
    private static final String APPLICATION_ID = "${application.id}";
    // The regex to look for while fetching application ID from the Hadoop/Spark job log
    private static final Pattern APPLICATION_ID_PATTERN = Pattern
            .compile("application_\\d+_\\d+");
    // The regex to look for while validating the content from RM job link
    private static final Pattern FAILED_TO_READ_APPLICATION_PATTERN = Pattern
            .compile("Failed to read the application");
    private static final Pattern INVALID_APPLICATION_ID_PATTERN = Pattern
            .compile("Invalid Application ID");

    /**
     * If the current status of the execution is not one of the finished statuses, mark the execution
     * as failed in the DB.
     * 如果执行的当前状态不是完成状态之一，那么在DB中将执行标记为失败。
     *
     * @param executorLoader the executor loader
     * @param alerterHolder  the alerter holder
     * @param flow           the execution
     * @param reason         reason for finalizing the execution
     * @param originalError  the cause, if execution is being finalized because of an error
     */
    public static void finalizeFlow(final ExecutorLoader executorLoader, final AlerterHolder
            alerterHolder, final ExecutableFlow flow, final String reason,
                                    @Nullable final Throwable originalError) {
        boolean alertUser = true;

        // First check if the execution in the datastore is finished.
        try {
            final ExecutableFlow dsFlow;
            if (isFinished(flow)) {
                dsFlow = flow;
            } else {
                dsFlow = executorLoader.fetchExecutableFlow(flow.getExecutionId());

                // If it's marked finished, we're good. If not, we fail everything and then mark it
                // finished.
                if (!isFinished(dsFlow)) {
                    failEverything(dsFlow);
                    executorLoader.updateExecutableFlow(dsFlow);
                }
            }

            if (flow.getEndTime() == -1) {
                flow.setEndTime(System.currentTimeMillis());
                executorLoader.updateExecutableFlow(dsFlow);
            }
        } catch (final ExecutorManagerException e) {
            // If failed due to azkaban internal error, do not alert user.
            alertUser = false;
            logger.error("Failed to finalize flow " + flow.getExecutionId() + ", do not alert user.", e);
        }

        if (alertUser) {

            String[] finalizeFlowReasons = ExecutionControllerUtils.getFinalizeFlowReasons(reason,
                    originalError);
            try {
                List<String> errorLogs = ExecutionControllerUtils.getErrorLogs(flow, executorLoader);
                for (String finalizeFlowReason : finalizeFlowReasons) {
                    String erroLine = ExecutionControllerUtils.getErroLine(finalizeFlowReason);
                    errorLogs.add(erroLine);
                }
                finalizeFlowReasons = errorLogs.toArray(new String[errorLogs.size()]);
            }catch (Exception e){
                e.printStackTrace();
            }
            alertUserOnFlowFinished(flow, alerterHolder,
                    finalizeFlowReasons, executorLoader);
//            alertUserOnFlowFinished(flow, alerterHolder, getFinalizeFlowReasons(reason, originalError),executorLoader);
        }
    }

    /**
     * When a flow is finished, alert the user as is configured in the execution options.
     * 当流完成时，按照执行选项中配置的方式向用户发出警报。
     *
     * @param flow          the execution
     * @param alerterHolder the alerter holder
     * @param extraReasons  the extra reasons for alerting
     */
    public static void alertUserOnFlowFinished(final ExecutableFlow flow, final AlerterHolder
            alerterHolder, final String[] extraReasons,final ExecutorLoader executorLoader) {
        final ExecutionOptions options = flow.getExecutionOptions();
        final Alerter mailAlerter = alerterHolder.get("email");
        final Alerter msg = alerterHolder.get("msg");
        if (flow.getStatus() != Status.SUCCEEDED) {
            if (options.getFailureEmails() != null && !options.getFailureEmails().isEmpty()) {
                try {
                    mailAlerter.alertOnError(flow, extraReasons);

                } catch (final Exception e) {
                    logger.error("Failed to alert on error for execution " + flow.getExecutionId(), e);
                }
            }
            //添加短信报警功能
            if (options.getFailureSms() != null && !options.getFailureSms().isEmpty()) {
                try {
                    msg.alertOnError(flow, extraReasons);
                } catch (Exception e) {
                    logger.error("Failed to alert on error for execution " + flow.getExecutionId(), e);
                }
            }
            if (options.getFlowParameters().containsKey("alert.type")) {
                final String alertType = options.getFlowParameters().get("alert.type");
                final Alerter alerter = alerterHolder.get(alertType);
                if (alerter != null) {
                    try {
                        alerter.alertOnError(flow, extraReasons);
                    } catch (final Exception e) {
                        logger.error("Failed to alert on error by " + alertType + " for execution " + flow
                                .getExecutionId(), e);
                    }
                } else {
                    logger.error("Alerter type " + alertType + " doesn't exist. Failed to alert.");
                }
            }
        } else {
            if (options.getSuccessEmails() != null && !options.getSuccessEmails().isEmpty()) {
                try {
                    mailAlerter.alertOnSuccess(flow);
                } catch (final Exception e) {
                    logger.error("Failed to alert on success for execution " + flow.getExecutionId(), e);
                }
            }
            //添加短信报警功能
            if (options.getSuccessSms() != null && !options.getSuccessSms().isEmpty()) {
                try {
                    msg.alertOnSuccess(flow);
                } catch (Exception e) {
                    logger.error("Failed to alert on success for execution " + flow.getExecutionId(), e);
                }
            }
            if (options.getFlowParameters().containsKey("alert.type")) {
                final String alertType = options.getFlowParameters().get("alert.type");
                final Alerter alerter = alerterHolder.get(alertType);
                if (alerter != null) {
                    try {
                        alerter.alertOnSuccess(flow);
                    } catch (final Exception e) {
                        logger.error("Failed to alert on success by " + alertType + " for execution " + flow
                                .getExecutionId(), e);
                    }
                } else {
                    logger.error("Alerter type " + alertType + " doesn't exist. Failed to alert.");
                }
            }
        }
    }

    /**
     * Alert the user when the flow has encountered the first error.
     * 当流遇到第一个错误时通知用户。
     *
     * @param flow          the execution
     * @param alerterHolder the alerter holder
     */
    public static void alertUserOnFirstError(final ExecutableFlow flow,
                                             final AlerterHolder alerterHolder) {
        final ExecutionOptions options = flow.getExecutionOptions();
        //控制报警器运行程序
        if (options.getNotifyOnFirstFailure()) {
            logger.info("Alert on first error of execution " + flow.getExecutionId());
            final Alerter mailAlerter = alerterHolder.get("email");
            try {
                mailAlerter.alertOnFirstError(flow);
            } catch (final Exception e) {
                logger.error("Failed to send first error email." + e.getMessage(), e);
            }

            if (options.getFlowParameters().containsKey("alert.type")) {
                final String alertType = options.getFlowParameters().get("alert.type");
                final Alerter alerter = alerterHolder.get(alertType);
                if (alerter != null) {
                    try {
                        alerter.alertOnFirstError(flow);
                    } catch (final Exception e) {
                        logger.error("Failed to alert by " + alertType, e);
                    }
                } else {
                    logger.error("Alerter type " + alertType + " doesn't exist. Failed to alert.");
                }
            }
        }
        //短信报警
        if (options.getSmsOnFirstFailure()) {
            final Alerter msg = alerterHolder.get("msg");
            try {
                msg.alertOnFirstError(flow);
            } catch (Exception e) {
                logger.error("Failed to send first error email." + e.getMessage(), e);
            }
        }

    }

    /**
     * Get the reasons to finalize the flow.
     * 获取完成流的原因。
     *
     * @param reason        the reason
     * @param originalError the original error
     * @return the reasons to finalize the flow
     */
    public static String[] getFinalizeFlowReasons(final String reason, final Throwable
            originalError) {
        final List<String> reasons = new LinkedList<>();
        reasons.add(reason);
        if (originalError != null) {
            reasons.add(ExceptionUtils.getStackTrace(originalError));
        }
        return reasons.toArray(new String[reasons.size()]);
    }

    /**
     * 从数据库中查询报错日志
     * @param flow
     * @param executorLoader
     * @return
     */
    public static List<String> getErrorLogs(final ExecutableFlow flow,final ExecutorLoader executorLoader){
        //        错误的job
        final List<String> failedJobs = findFailedJobs(flow);
        final int execId = flow.getExecutionId();
        List<String> erros=new ArrayList<>();
        for (String job:failedJobs
             ) {
            try {
                FileIOUtils.LogData logData = executorLoader.fetchLogs(execId, job, 0, 0, 500000);
                String data = logData.getData();
                if(StringUtils.isBlank(data)||!data.contains("ERROR - Job run failed!\n")){
                    continue;
                }else {
                    String[] split = data.split("ERROR - Job run failed!\n");
                    if(split.length>1){
                        String erroLine = job+"====>"+getErroLine(split[1]);
                        logger.debug("处理后的一条错误信息=========》"+erroLine);
                        erros.add(erroLine);
                    }
                }
            } catch (ExecutorManagerException e) {
                logger.error("组装错误日志出错"+e.getLocalizedMessage());
            }
        }
        return erros;

    }

    /***
     * 获取报错信息中第一行报错信息
     * @param errorData
     * @return
     */
    public static String getErroLine(String errorData){
        if(StringUtils.isBlank(errorData)){
            return null;
        }else {
            String[] split = errorData.split("\n");
            return split[0];
        }
    }

    /**
     * 获取错误的job
     * @param flow
     * @return
     */
    private static List<String> findFailedJobs(final ExecutableFlow flow) {
        final ArrayList<String> failedJobs = new ArrayList<>();
        for (final ExecutableNode node : flow.getExecutableNodes()) {
            if (node.getStatus() == Status.FAILED) {
                failedJobs.add(node.getId());
            }
        }
        return failedJobs;
    }

    /**
     * Set the flow status to failed and fail every node inside the flow.
     * 将流状态设置为failed，并使流中的每个节点失效。
     *
     * @param exFlow the executable flow
     */
    public static void failEverything(final ExecutableFlow exFlow) {
        final long time = System.currentTimeMillis();
        for (final ExecutableNode node : exFlow.getExecutableNodes()) {
            switch (node.getStatus()) {
                case SUCCEEDED:
                case FAILED:
                case KILLED:
                case SKIPPED:
                case DISABLED:
                    continue;
                    // case UNKNOWN:
                case READY:
                    node.setStatus(Status.KILLING);
                    break;
                default:
                    node.setStatus(Status.FAILED);
                    break;
            }

            if (node.getStartTime() == -1) {
                node.setStartTime(time);
            }
            if (node.getEndTime() == -1) {
                node.setEndTime(time);
            }
        }

        if (exFlow.getEndTime() == -1) {
            exFlow.setEndTime(time);
        }

        exFlow.setStatus(Status.FAILED);
    }

    /**
     * Check if the flow status is finished.
     *
     * @param flow the executable flow
     * @return the boolean
     */
    public static boolean isFinished(final ExecutableFlow flow) {
        switch (flow.getStatus()) {
            case SUCCEEDED:
            case FAILED:
            case KILLED:
                return true;
            default:
                return false;
        }
    }

    /**
     * Dynamically create the job link url. Construct the job link url from resource manager url.
     * If it's valid, just return the job link url. Otherwise, construct the job link url from
     * Hadoop/Spark job history server.
     *动态创建作业链接url。从资源管理器url构造作业链接url。如果是有效的，就返回工作链接url。否则，从Hadoop/Spark作业历史服务器构造作业链接url。
     * @param exFlow        The executable flow.
     * @param jobId         The job id.
     * @param applicationId The application id.
     * @param azkProps      The azkaban props.
     * @return the job link url.
     */
    public static String createJobLinkUrl(final ExecutableFlow exFlow, final String jobId,
                                          final String applicationId, final Props azkProps) {
        if (applicationId == null) {
            return null;
        }

        final URL url;
        final String jobLinkUrl;
        boolean isRMJobLinkValid = true;

        try {
            url = new URL(azkProps.getString(ConfigurationKeys.RESOURCE_MANAGER_JOB_URL)
                    .replace(APPLICATION_ID, applicationId));
            final String keytabPrincipal = requireNonNull(
                    azkProps.getString(ConfigurationKeys.AZKABAN_KERBEROS_PRINCIPAL));
            final String keytabPath = requireNonNull(azkProps.getString(ConfigurationKeys
                    .AZKABAN_KEYTAB_PATH));
            final HttpURLConnection connection = AuthenticationUtils.loginAuthenticatedURL(url,
                    keytabPrincipal, keytabPath);

            try (final BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (FAILED_TO_READ_APPLICATION_PATTERN.matcher(inputLine).find()
                            || INVALID_APPLICATION_ID_PATTERN.matcher(inputLine).find()) {
                        logger.info(
                                "RM job link is invalid or has expired for application_" + applicationId);
                        isRMJobLinkValid = false;
                        break;
                    }
                }
            }
        } catch (final Exception e) {
            logger.error("Failed to get job link for application_" + applicationId, e);
            return null;
        }

        if (isRMJobLinkValid) {
            jobLinkUrl = url.toString();
        } else {
            // If RM job link is invalid or has expired, fetch the job link from JHS or SHS.
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
                logger.error(
                        "Invalid job link. Job " + jobId + " doesn't exist in " + exFlow.getExecutionId());
                return null;
            }

            if (node.getType().equals(SPARK_JOB_TYPE)) {
                jobLinkUrl =
                        azkProps.get(ConfigurationKeys.SPARK_HISTORY_SERVER_JOB_URL).replace
                                (APPLICATION_ID, applicationId);
            } else {
                jobLinkUrl =
                        azkProps.get(ConfigurationKeys.HISTORY_SERVER_JOB_URL).replace(APPLICATION_ID,
                                applicationId);
            }
        }

        logger.info(
                "Job link url is " + jobLinkUrl + " for execution " + exFlow.getExecutionId() + ", job "
                        + jobId);
        return jobLinkUrl;
    }

    /**
     * Find the application id from job log by matching "application_<id>" pattern.
     *通过匹配“application_<id>”模式从作业日志中查找应用程序id。
     * @param logData the log data
     * @return the application id
     */
    public static String findApplicationIdFromLog(final String logData) {
        final Matcher matcher = APPLICATION_ID_PATTERN.matcher(logData);
        String appId = null;
        if (matcher.find()) {
            appId = matcher.group().substring(12);
        }
        logger.info("Application ID is " + appId);
        return appId;
    }
}
