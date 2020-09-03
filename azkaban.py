#!/usr/bin/python
# -*- coding: UTF-8 -*-

import requests
import os
import io
import time
import json,sys
import threading

#关闭调用api请求返回的警告
requests.packages.urllib3.disable_warnings()
if sys.getdefaultencoding() != 'utf-8':
    reload(sys)
    sys.setdefaultencoding('utf-8')
denpends_status=1
lock = threading.Lock()  # 锁对象！
##登录azkaban
# username: 登录用户名
# password: 登录密码
def Login(username, password):
    #登录信息
    postdata = {'username':username,'password':password,'session.id':sessionId}

    #登录，通过verify=False关闭安全验证
    login_url = str_url + '?action=login'
    r = requests.post(login_url, postdata, verify=False).json()

    return r


##创建一个项目
# projectname:指定的项目名称
# description:项目说明，不允许为空。
def Create_Project(projectname, description):
    postdata = {'session.id': sessionId, 'name': projectname, 'description': description}

    upload_url = str_url + '/manager?action=create'
    r = requests.post(upload_url, postdata, verify=False).json()
    
    return r


##删除项目
# projectname:需要删除的项目名称
def Delete_Project(projectname):
    postdata = {'session.id': sessionId, 'project': projectname}

    upload_url = str_url + '/manager?delete=true'
    r = requests.get(upload_url, params=postdata, verify=False)

    return str(r.status_code)


##上传项目zip文件
# projectname:需要上传zip包的项目名称
# filepath:项目zip文件
def Upload_Project_Zip( projectname, filepath):
    #从路径中获取文件名
    filename = os.path.basename(os.path.realpath(filepath))
    
    files = {'file':(filename, open(filepath,'rb'), 'application/zip')}
    postdata = {'session.id':sessionId, 'project':projectname, 'file':files, 'ajax':'upload'}

    upload_url = str_url + '/manager?ajax=upload'
    r = requests.post(upload_url, postdata, files=files, verify=False).json()

    return r


##获取一个项目的flow信息
# projectname:项目名称
def Fetch_Flows( projectname):
    postdata = {'session.id':sessionId, 'project':projectname, 'ajax':'fetchprojectflows'}

    fetch_url = str_url + '/manager?ajax=fetchprojectflows'
    #r = requests.get(fetch_url, postdata, verify=False).json()
    r = requests.get(fetch_url, params=postdata, verify=False).json()
    return r


##获取一个工作流的jobs
# projectname:项目名称
# flowid:flow名称
def Fetch_Jobs(projectname, flowid):
    postdata = {'session.id':sessionId, 'project':projectname, 'ajax':'fetchflowgraph', 'flow':flowid}

    fetch_url = str_url + '/manager?ajax=fetchflowgraph'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r
    
    
##获取工作流执行总体信息
# projectname:项目名称
# flowid:flow名称
# start:从最后第几次运行结果开始查
# length:查询几次的运行结果
def Fetch_Executions(projectname, flowid, start, length):
    postdata = {'session.id':sessionId, 'project':projectname, 'ajax':'fetchFlowExecutions', 'flow':flowid, 'start':start, 'length':length}

    fetch_url = str_url + '/manager?ajax=fetchFlowExecutions'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##获取一个正在执行的工作流的情况
# projectname:项目名称
# flowid:flow名称
def Fetch_Running_Executions(projectname, flowid):
    postdata = {'session.id':sessionId, 'project':projectname, 'ajax':'getRunning', 'flow':flowid}
    
    fetch_url = str_url + '/executor?ajax=getRunning'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##根据指定的Execution Id，获取工作流执行的详细信息
# execid:Execution Id
def Fetch_Flow_Execution_byid( execid):
    postdata = {'session.id': sessionId, 'ajax': 'fetchexecflow', 'execid': execid}

    fetch_url = str_url + '/executor?ajax=fetchexecflow'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##根据指定的session.id、jobid、exec_id，获取对应的任务日志
# offset:日志的偏移量，返回的日志将从第10个字符开始
# length:日志数据的长度
def Fetch_Execution_Job_Logs(execid, jobid, offset, length):
    postdata = {'session.id': sessionId, 'ajax': 'fetchExecJobLogs', 'execid': execid, 'jobId': jobid, 'offset': offset,
                'length': length}

    fetch_url = str_url + '/executor?ajax=fetchExecJobLogs'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##获取工作流执行的更新情况
# lastUpdateTime:按上次更新时间筛选的条件。如果需要所有作业信息，则将值设置为-1。
def Fetch_Flow_Execution_Updates(execid, lastUpdateTime):
    postdata = {'session.id': sessionId, 'ajax': 'fetchexecflowupdate', 'execid': execid,
                'lastUpdateTime': lastUpdateTime}

    fetch_url = str_url + '/executor?ajax=fetchexecflowupdate'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##执行一个工作流
# projectname:项目名称
# flowid:flow名称
# settings:可选参数字典
#   disabled(可选):此次执行禁用的作业名称列表。格式化为JSON数组字符串。如：["job_name_1","job_name_2","job_name_N"]
#   successEmails(可选):执行成功发送的邮件列表。多个邮箱用[,|;|\s+]分隔。如：zh@163.com,zh@126.com
#   failureEmails(可选):执行成功发送的邮件列表。多个邮箱用[,|;|\s+]分隔。如：zh@163.com,zh@126.com
#   successEmailsOverride(可选):是否使用系统默认电子邮件设置覆盖成功邮件。值：true, false
#   failureEmailsOverride(可选):是否使用系统默认电子邮件设置覆盖失败邮件。值：true, false
#   notifyFailureFirst(可选):是否在第一次失败时发送电子邮件通知。值：true, false
#   notifyFailureLast(可选):是否在最后失败时发送电子邮件通知。值：true, false
#   failureAction(可选):如果发生错误，如何操作。值：finishcurrent、cancelimmediately、finishpossible
#   concurrentOption(可选):如果不需要任何详细信息，请使用ignore。值：ignore, pipeline, skip
#   flowOverride[flowProperty](可选):用指定的值重写指定的流属性。如：flowoverride[failure.email]=abc@163.com
def Execute_Flow(projectname, flowid, settings):
    postdata = {'session.id': sessionId, 'ajax': 'executeFlow', 'project': projectname, 'flow': flowid}
    for key in settings:
        postdata[key] = settings[key]

    fetch_url = str_url + '/executor?ajax=executeFlow'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##取消一个正在运行的工作流
# execid:Execution id
def Cancel_Flow_Execution(execid):
    postdata = {'session.id': sessionId, 'ajax': 'cancelFlow', 'execid': execid}

    fetch_url = str_url + '/executor?ajax=cancelFlow'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##暂停一个正在运行的工作流
# execid:Execution id
def Pause_Flow_Execution(execid):
    postdata = {'session.id': sessionId, 'ajax': 'pauseFlow', 'execid': execid}

    fetch_url = str_url + '/executor?ajax=pauseFlow'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##恢复一个已暂停的工作流
# execid:Execution id
def Resume_Flow_Execution(execid):
    postdata = {'session.id': sessionId, 'ajax': 'resumeFlow', 'execid': execid}

    fetch_url = str_url + '/executor?ajax=resumeFlow'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r


##使用cron灵活设置调度
# projectname:项目名称
# flowid:flow名称
# cronExpression:cron表达式。如："0 23/30 5,7-10 ? * 6#3"
def Flexible_scheduling_using_Cron(projectname, flowid, cronExpression):
    postdata = {'session.id': sessionId, 'ajax': 'resumeFlow', 'projectName': projectname, 'flow': flowid, 'cronExpression': cronExpression}

    fetch_url = str_url + '/schedule?ajax=scheduleCronFlow'
    r = requests.post(fetch_url, postdata, verify=False).json()

    return r


##获取指定Project、flow的调度
# projectid:项目id
# flowid:flow名称
def Fetch_Schedule(projectid, flowid):
    postdata = {'session.id':sessionId, 'projectId':projectid, 'ajax':'fetchSchedule', 'flowId':flowid}
    
    fetch_url = str_url + '/schedule?ajax=fetchSchedule'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r
    
    
##取消工作流的调度
# scheduleId:计划的ID。可以在scheduling页面找到
def Unschedule_Flow(scheduleId):
    postdata = {'session.id': sessionId, 'scheduleId': scheduleId}

    fetch_url = str_url + '/schedule?action=removeSched'
    r = requests.post(fetch_url, postdata, verify=False).json()

    return r
    

##设置告警模块
# scheduleId:计划的ID
# slaEmails:SLA警报电子邮件列表。如：zh@163.com;zh@126.com
# settings:SLA规则字典。格式为settings[…]=[id],[rule],[durati on],[emailAction],[killAction]。
#       如：{"settings[0]":"aaa,SUCCESS,5:00,true,false";"settings[1]":"bbb,SUCCESS,10:00,false,true"}
def Set_SLA(scheduleId, slaEmails, settings):
    postdata = {'session.id': sessionId, 'ajax': 'setSla', 'scheduleId': scheduleId, 'slaEmails': slaEmails}
    for key in settings:
        postdata[key] = settings[key]

    fetch_url = str_url + '/schedule?ajax=setSla'
    r = requests.post(fetch_url, postdata, verify=False).json()

    return r


##获取资源调度的报警模块
# scheduleId:计划的ID
def Fetch_SLA(scheduleId):
    postdata = {'session.id': sessionId, 'ajax': 'slaInfo', 'scheduleId': scheduleId}

    fetch_url = str_url + '/schedule?ajax=slaInfo'
    r = requests.get(fetch_url, params=postdata, verify=False).json()

    return r
#根据execid获取错误日志
def Get_Execid_Log(execid,flowName,projectName):
    jobs=Fetch_Jobs(projectName,flowName)['nodes']
    rt=[]
    for job in jobs:
        jobName=job['id']
        try:
            logs=str(Fetch_Execution_Job_Logs(execid,jobName,0,50000)['data'])
        except Exception:
            continue
        else:
            error_string_log='ERROR - Job run failed!\n'
            if error_string_log in logs:
                ix=logs.find(error_string_log)
                l=logs[ix:]
                strlist = l.split('\n')
                rt.append(str(jobName)+"=====>"+strlist[1])
            else:
                continue
    return rt
#根据key获取配置文件的value
def getProperties(fileName):
    try:
        pro_file = io.open(fileName, 'r',encoding='utf-8')
        properties = {}
        for line in pro_file:
            if line.find('=') > 0:
                strs = line.replace('\n', '').split('=')
                properties[strs[0]] = strs[1]
    except Exception as e:
        raise e
    else:
        pro_file.close()
    return properties
#监听任务状态
def monitorExecStatus(projectName,flowName,maxWaitTime,stepTime,lk):
    print ("启动监控"+str(projectName)+"------------"+str(flowName)+"的线程")
    global denpends_status
    if projectName.strip()=='' or flowName.strip()=='':
        print ("输入的参数为空")
        lk.acquire()  # 开始加锁!!!
        denpends_status=0
        lk.release()   # 释放锁
    else:
        if str(maxWaitTime).strip()=='':
            maxWaitTime=7200
        if str(stepTime).strip()=='':
            stepTime=300
        excutuin_info=Fetch_Executions(projectName,flowName,0,1)
        print (excutuin_info)
        if "error" in excutuin_info:
            print ("配置的项目找不到======》"+projectName+"------"+flowName)
            return
        status=excutuin_info["executions"][0]["status"]
        flag=0
        while flag<maxWaitTime and denpends_status==1:
            if status in ready:
                time.sleep(stepTime)
                excutuin_info=Fetch_Executions(projectName,flowName,0,1)
                status=excutuin_info["executions"][0]["status"]
                flag=flag+stepTime
            elif status in success:
                break
            elif status in faild:
                break
            else:
                print ("出现了一个未知的状态="+str(status))
                break
        if flag>=maxWaitTime:
            print ("注意！！！！"+projectName+"等待超时，继续执行！！！！")
        print (projectName+"-----------"+flowName+"任务状态="+str(status))
        if status in success:
            return
        elif status in faild:
            lk.acquire()  # 开始加锁!!!
            denpends_status=0
            lk.release()   # 释放锁
        elif status in ready:
            return
        else:
            print ("faild未知状态"+str(status))
            lk.acquire()  # 开始加锁!!!
            denpends_status=0
            lk.release()   # 释放锁
    return

projects=sys.argv[1]
print ("Python取到输入值====》"+projects)
if projects.strip()=='':
    print ("传入的参数为空")
    sys.exit(1)
projectsDepends=getProperties(projects)['projectsDepends']
print ("配置文件取到的值====》"+projectsDepends)
if projectsDepends.strip()=='':
    print ("配置文件没有值")
    sys.exit(1)
#定义azkaban地址、登录信息
# web_url=getProperties(projects)['web_url']
# str_url = web_url
# print 'web访问地址====》'+str_url
# user_name=getProperties(projects)['user_name']
# user_pwd=getProperties(projects)['user_pwd']
web_url=getProperties(projects)['web_url']
print ("web的IP==》"+web_url)
web_port=getProperties(projects)['web_port']
print ("web的端口号===》"+web_port)
str_url = 'https://'+str(web_url)+':'+str(web_port)
print ('web访问地址====》'+str_url)
user_name=getProperties(projects)['user_name']
user_pwd=getProperties(projects)['user_pwd']
##################################################
sessionId='4fdb8734-19ba-4e59-a5f2-86173a274a99'
print (Login(user_name,user_pwd))
ready=["READY","PREPARING","RUNNING","QUEUED"]
success=["SUCCEEDED"]
faild=["PAUSED","KILLING","KILLED","FAILED","FAILED_FINISHING","SKIPPED","DISABLED","FAILED_SUCCEEDED","CANCELLED"]

##################################################
#执行任务，根据需求，只监听状态，所以这里不执行
#run_info=Execute_Flow('test','job1','')
#run_info=json.dumps(run_info)
#execid=json.loads(run_info)['execid']
#print "执行任务的id="+str(execid)
#status_json=Fetch_Flow_Execution_byid(execid)
#status_json=json.dumps(status_json)
#status=json.loads(status_json)['status']
#####################################################
#####################################################
#########判断任务状态，在执行状态就等待，失败就抛出异常###
#########成功就正常执行###############################
project_status_list=[]
projectsDepends=json.loads(projectsDepends)
threadNameList=[]
for project in projectsDepends:
    print (project)
    projectName=project["projectName"]
    flowName=project["flowName"]
    if "max_wait_time" in project:
        maxWaitTime=project["max_wait_time"]*60
    else:
        maxWaitTime=3600
    if "step_time" in project:
        stepTime=project["step_time"]*60
    else:
        stepTime=300
    if "flag" in project:
        projectFlag=project["flag"]
    else:
        projectFlag=1
    t = threading.Thread(target=monitorExecStatus, args=(projectName,flowName,maxWaitTime,stepTime,lock))
    if projectFlag==1:
        threadNameList.append(t)
    else:
        t.setDaemon(True)#守护进程
    t.start()

print ("启动的线程============>")
time.sleep(1)
for threadName in threadNameList:
    threadName.join()#主线程等待子线程执行完毕，flag=0的不用等待

if denpends_status!=1:
    sys.exit(1)



