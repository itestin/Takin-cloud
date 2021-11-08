package io.shulie.takin.cloud.biz.service.scene;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.pamirs.takin.entity.domain.vo.report.SceneTaskNotifyParam;
import io.shulie.takin.cloud.biz.cloudserver.SceneManageDTOConvert;
import io.shulie.takin.cloud.biz.config.AppConfig;
import io.shulie.takin.cloud.biz.utils.DataUtils;
import io.shulie.takin.cloud.common.utils.CommonUtil;
import io.shulie.takin.cloud.common.utils.NumberUtil;
import io.shulie.takin.ext.content.enginecall.BusinessActivityExt;
import io.shulie.takin.ext.content.enginecall.ScheduleStartRequestExt;
import io.shulie.takin.ext.content.enginecall.ScheduleStopRequestExt;
import io.shulie.takin.cloud.biz.output.scene.manage.SceneManageWrapperOutput;
import io.shulie.takin.cloud.biz.service.engine.EnginePluginFilesService;
import io.shulie.takin.cloud.common.bean.scenemanage.SceneManageQueryOpitons;
import io.shulie.takin.cloud.common.bean.task.TaskResult;
import io.shulie.takin.cloud.common.constants.ScheduleConstants;
import io.shulie.takin.cloud.common.constants.ScheduleEventConstant;
import io.shulie.takin.cloud.common.enums.scenemanage.TaskStatusEnum;
import io.shulie.takin.cloud.data.result.report.ReportResult;
import io.shulie.takin.eventcenter.Event;
import io.shulie.takin.eventcenter.EventCenterTemplate;
import io.shulie.takin.eventcenter.annotation.IntrestFor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author 莫问
 * @date 2020-04-22
 */
@Component
@Slf4j
public class SceneTaskEventServie {

    @Autowired
    private SceneTaskService sceneTaskService;

    @Autowired
    private SceneManageService sceneManageService;

    @Autowired
    private EventCenterTemplate eventCenterTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EnginePluginFilesService enginePluginFilesService;
    @Resource
    private AppConfig appConfig;

    @IntrestFor(event = "failed")
    public void failed(Event event) {
        log.info("监听到启动失败事件.....");
        Object object = event.getExt();
        TaskResult taskBean = (TaskResult)object;
        if (taskBean != null) {
            sceneTaskService.handleSceneTaskEvent(taskBean);
        }
    }

    @IntrestFor(event = "started")
    public void started(Event event) {
        log.info("监听到启动成功事件.....");
        Object object = event.getExt();
        TaskResult taskBean = (TaskResult)object;
        if (taskBean != null) {
            sceneTaskService.handleSceneTaskEvent(taskBean);
        }
    }

    /**
     * 场景任务启动事件
     *
     * @param scene    场景
     * @param reportId 报告主键
     */
    public void callStartEvent(SceneManageWrapperOutput scene, Long reportId) {
        Long sceneId = scene.getId();
        Long customerId = scene.getCustomerId();
        ScheduleStartRequestExt scheduleStartRequest = new ScheduleStartRequestExt();
        scheduleStartRequest.setContinuedTime(scene.getTotalTestTime());
        scheduleStartRequest.setSceneId(sceneId);
        scheduleStartRequest.setTaskId(reportId);
        // 客户id
        scheduleStartRequest.setCustomerId(customerId);
        String consoleUrl = DataUtils.mergeUrl(appConfig.getConsole(), ScheduleConstants.getConsoleUrl(sceneId, reportId, customerId));
        scheduleStartRequest.setConsole(consoleUrl);
        String callbackUrl = DataUtils.mergeUrl(appConfig.getConsole(), "/api/engine/callback");
        scheduleStartRequest.setCallbackUrl(callbackUrl);

        scheduleStartRequest.setPressureScene(scene.getPressureType());
//        String pressureMode = scene.getPressureMode() == 1 ? "fixed"
//            : scene.getPressureMode() == 2 ? "linear" : "stair";
//        scheduleStartRequest.setPressureMode(pressureMode);
//        scheduleStartRequest.setPressureType(scene.getPressureType());
//        scheduleStartRequest.setRampUp(scene.getIncreasingSecond());
//        scheduleStartRequest.setSteps(scene.getStep());
        scheduleStartRequest.setTotalIp(scene.getIpNum());
        scheduleStartRequest.setExpectThroughput(scene.getConcurrenceNum());
        scheduleStartRequest.setThreadGroupConfigMap(scene.getThreadGroupConfigMap());

//        Map<String, String> businessRtData = Maps.newHashMap();
//        Map<String, Integer> businessTpsData = Maps.newHashMap();
//        Map<String, String> businessNameData = Maps.newHashMap();
//        int tps = 0;
//        for (SceneManageWrapperOutput.SceneBusinessActivityRefOutput config : scene.getBusinessActivityConfig()) {
//            if (null != config.getTargetRT()) {
//                businessRtData.put(config.getBindRef(), config.getTargetRT().toString());
//            }
//            businessTpsData.put(config.getBindRef(), config.getTargetTPS());
//            if (null != config.getTargetTPS()) {
//                tps += config.getTargetTPS();
//            }
//            businessNameData.put(config.getBindRef(), config.getBusinessActivityName());
//        }
//        scheduleStartRequest.setTotalTps(tps);
//        scheduleStartRequest.setTps(NumberUtil.getRate(tps, scene.getIpNum()));
//        scheduleStartRequest.setBusinessData(businessRtData);
//        scheduleStartRequest.setBusinessTpsData(businessTpsData);
//        List<Map<String, String>> businessActivities = new ArrayList<>();
//        for (Map.Entry<String, Integer> entry : businessTpsData.entrySet()) {
//            Map<String, String> businessActivity = new HashMap<>();
//            businessActivity.put("elementTestName", entry.getKey());
//            businessActivity.put("throughputPercent", String.valueOf(NumberUtil.getPercentRate(entry.getValue(), tps)));
//            businessActivities.add(businessActivity);
//        }
//        scheduleStartRequest.setBusinessActivities(businessActivities);

        Map<String, BusinessActivityExt> businessData = Maps.newHashMap();
        Integer tps = CommonUtil.sum(scene.getBusinessActivityConfig(), SceneManageWrapperOutput.SceneBusinessActivityRefOutput::getTargetTPS);
        List<BusinessActivityExt> activities = CommonUtil.getList(scene.getBusinessActivityConfig(), SceneManageDTOConvert.INSTANCE::of);
        if (CollectionUtils.isNotEmpty(activities)) {
            for (BusinessActivityExt d : activities) {
                if (null != d.getTps()) {
                    d.setRate(NumberUtil.getRate(d.getTps(), tps));
                }
                businessData.put(d.getBindRef(), d);
            }
        }
        scheduleStartRequest.setBusinessData(businessData);
        scheduleStartRequest.setBindByXpathMd5(StringUtils.isNoneBlank(scene.getScriptAnalysisResult()));

        //一个插件可能会有多个版本，需要根据版本号来获取相应的文件路径 modified by xr.l 20210712
        if (CollectionUtils.isNotEmpty(scene.getEnginePlugins())) {
            scheduleStartRequest.setEnginePluginsFilePath(enginePluginFilesService.findPluginFilesPathByPluginIdAndVersion(scene.getEnginePlugins()));
        } else {
            scheduleStartRequest.setEnginePluginsFilePath(Lists.newArrayList());
        }

        //添加巡检参数
        scheduleStartRequest.setLoopsNum(scene.getLoopsNum());
        scheduleStartRequest.setFixedTimer(scene.getFixedTimer());
        scheduleStartRequest.setInspect(scene.isInspect());
        scheduleStartRequest.setTryRun(scene.isTryRun());

        List<ScheduleStartRequestExt.DataFile> dataFileList = new ArrayList<>();
        scene.getUploadFile().forEach(file -> {
            if (file.getFileType() == 0) {
                scheduleStartRequest.setScriptPath(file.getUploadPath());
            } else {
                ScheduleStartRequestExt.DataFile dataFile = new ScheduleStartRequestExt.DataFile();
                dataFile.setName(file.getFileName());
                dataFile.setPath(file.getUploadPath());
                dataFile.setSplit(file.getIsSplit() != null && file.getIsSplit() == 1);
                dataFile.setOrdered(file.getIsOrderSplit() != null && file.getIsOrderSplit() == 1);
                dataFile.setRefId(file.getId());
                dataFile.setFileType(file.getFileType());
                dataFile.setBigFile(file.getIsBigFile() != null && file.getIsBigFile() == 1);
                dataFile.setFileMd5(file.getFileMd5());
                dataFileList.add(dataFile);
            }
        });
        scheduleStartRequest.setDataFile(dataFileList);
        scheduleStartRequest.setFileContinueRead(scene.isContinueRead());
        Event event = new Event();
        event.setEventName(ScheduleEventConstant.START_SCHEDULE_EVENT);
        event.setExt(scheduleStartRequest);
        eventCenterTemplate.doEvents(event);
        log.info("场景[{}]任务启动事件.....{}", scene, reportId);
    }

    /**
     * 停止场景压测任务
     *
     * @param reportResult 报告结果
     */
    public void callStopEvent(ReportResult reportResult) {
        ScheduleStopRequestExt scheduleStopRequest = new ScheduleStopRequestExt();
        scheduleStopRequest.setSceneId(reportResult.getSceneId());
        scheduleStopRequest.setTaskId(reportResult.getId());
        scheduleStopRequest.setCustomerId(reportResult.getCustomerId());
        Event event = new Event();
        event.setEventName(ScheduleEventConstant.STOP_SCHEDULE_EVENT);
        event.setExt(scheduleStopRequest);
        eventCenterTemplate.doEvents(event);
        log.info("主动停止场景[{}]任务事件.....{}", reportResult.getSceneId(), reportResult.getId());
    }

    /**
     * 启动结果
     *
     * @param param 参数
     */
    public String callStartResultEvent(SceneTaskNotifyParam param) {
        String index = "";
        if (param != null) {
            log.info("收到pod通知参数:{}", param);
            Event event = new Event();
            TaskResult result = new TaskResult();
            result.setSceneId(param.getSceneId());
            result.setTaskId(param.getTaskId());
            result.setCustomerId(param.getCustomerId());
            result.setMsg(param.getMsg());

            boolean isNotify = true;
            if ("started".equals(param.getStatus())) {
                // 压力节点 启动成功
                result.setStatus(TaskStatusEnum.STARTED);
                event.setEventName("started");
                // 扩展配置
                Map<String, Object> extendMap = Maps.newHashMap();
                SceneManageQueryOpitons options = new SceneManageQueryOpitons();
                options.setIncludeBusinessActivity(true);
                SceneManageWrapperOutput dto = sceneManageService.getSceneManage(param.getSceneId(), options);
                if (dto != null && CollectionUtils.isNotEmpty(dto.getBusinessActivityConfig())) {
                    extendMap.put("businessActivityCount", dto.getBusinessActivityConfig().size());
                    extendMap.put("businessActivityBindRef", dto.getBusinessActivityConfig().stream()
                        .map(SceneManageWrapperOutput.SceneBusinessActivityRefOutput::getBindRef)
                        .filter(StringUtils::isNoneBlank)
                        .map(String::trim).distinct().collect(Collectors.toList()));
                }
                result.setExtendMap(extendMap);
                String key = ScheduleConstants.getFileSplitQueue(param.getSceneId(), param.getTaskId(),
                    param.getCustomerId());
                index = stringRedisTemplate.opsForList().leftPop(key);

            } else if ("failed".equals(param.getStatus())) {
                result.setStatus(TaskStatusEnum.FAILED);
                event.setEventName("failed");
            } else {
                isNotify = false;
            }

            if (isNotify) {
                event.setExt(result);
                eventCenterTemplate.doEvents(event);
                log.info("成功处理压力引擎节点通知事件: {}", param);
            }
            log.info("pressureNode {}-{}-{}: Accept the start result ,pressureNode number :{}",
                param.getSceneId(), param.getTaskId(), param.getCustomerId(), index);
        }
        return index;
    }

}
