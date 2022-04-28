package io.shulie.takin.cloud.app.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import javax.annotation.Resource;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.shulie.takin.cloud.app.entity.JobEntity;
import io.shulie.takin.cloud.app.mapper.JobMapper;
import io.shulie.takin.cloud.app.service.JobService;
import io.shulie.takin.cloud.app.entity.ResourceEntity;
import io.shulie.takin.cloud.app.service.CommandService;
import io.shulie.takin.cloud.app.entity.JobExampleEntity;
import io.shulie.takin.cloud.app.service.ResourceService;
import io.shulie.takin.cloud.model.response.JobConfig;
import io.shulie.takin.cloud.app.entity.ThreadConfigEntity;
import io.shulie.takin.cloud.model.request.StartRequest;
import io.shulie.takin.cloud.app.entity.ResourceExampleEntity;
import io.shulie.takin.cloud.app.entity.ThreadConfigExampleEntity;
import io.shulie.takin.cloud.app.service.mapper.JobExampleMapperService;
import io.shulie.takin.cloud.app.service.mapper.ThreadConfigMapperService;
import io.shulie.takin.cloud.model.request.StartRequest.ThreadConfigInfo;
import io.shulie.takin.cloud.app.service.mapper.ThreadConfigExampleMapperService;

/**
 * 任务服务 - 实例
 *
 * @author <a href="mailto:472546172@qq.com">张天赐</a>
 */
@Service
public class JobServiceImpl implements JobService {
    @Resource
    JobMapper jobMapper;
    @Resource
    CommandService commandService;
    @Resource
    ResourceService resourceService;
    @Resource
    JobConfigServiceImpl jobConfigService;
    @Resource
    JobExampleMapperService jobExampleMapperService;
    @Resource
    ThreadConfigMapperService threadConfigMapperService;
    @Resource
    ThreadConfigExampleMapperService threadConfigExampleMapperService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public String start(StartRequest jobInfo) throws JsonProcessingException {
        ResourceEntity resourceEntity = resourceService.entity(jobInfo.getResourceId());
        List<ResourceExampleEntity> resourceExampleEntityList = resourceService.listExample(resourceEntity.getId());
        JobEntity jobEntity = new JobEntity() {{
            setResourceId(resourceEntity.getId());
            setName(jobInfo.getName());
            // 时长取最大值
            setDuration(jobInfo.getThreadConfig().stream()
                .map(ThreadConfigInfo::getDuration)
                .max(Comparator.naturalOrder())
                .orElse(0));
            setSampling(jobInfo.getSampling());
            setMode(jobInfo.getType().getCode());
            setCallbackUrl(jobInfo.getCallbackUrl());
            setResourceExampleNumber(resourceEntity.getNumber());
        }};
        jobMapper.insert(jobEntity);
        // 填充job实例
        List<JobExampleEntity> jobExampleEntityList = new ArrayList<>(resourceEntity.getNumber());
        for (int i = 0; i < resourceEntity.getNumber(); i++) {
            int finalI = i;
            jobExampleEntityList.add(new JobExampleEntity() {{
                setJobId(jobEntity.getId());
                setDuration(jobEntity.getDuration());
                setResourceExampleId(resourceExampleEntityList.get(finalI).getId());
                setName(jobEntity.getName() + "-" + finalI);
            }});
        }
        jobExampleMapperService.saveBatch(jobExampleEntityList);
        // 填充线程组配置
        List<ThreadConfigEntity> threadConfigEntityList = new ArrayList<>(0);
        for (ThreadConfigInfo threadConfigInfo : jobInfo.getThreadConfig()) {
            threadConfigEntityList.add(new ThreadConfigEntity() {{
                setJobId(jobEntity.getId());
                setMode(threadConfigInfo.getType().getCode());
                setRef(threadConfigInfo.getRef());
                HashMap<String, Object> context = new HashMap<String, Object>(5) {{
                    put("number", threadConfigInfo.getNumber());
                    put("tps", threadConfigInfo.getTps());
                    put("duration", threadConfigInfo.getDuration());
                    put("growthTime", threadConfigInfo.getGrowthTime());
                    put("step", threadConfigInfo.getGrowthStep());
                }};
                setContext(objectMapper.writeValueAsString(context));
            }});
        }
        threadConfigMapperService.saveBatch(threadConfigEntityList);

        // 切分线程配置
        List<List<ThreadConfigInfo>> threadExampleList = splitThreadConfig(jobInfo.getThreadConfig(), resourceEntity.getNumber());
        // 填充线程配置实例
        List<ThreadConfigExampleEntity> threadConfigExampleEntityList = new ArrayList<>(resourceEntity.getNumber());
        for (int i = 0; i < threadExampleList.size(); i++) {
            JobExampleEntity jobExampleEntity = jobExampleEntityList.get(i);
            List<ThreadConfigInfo> threadConfigInfoList = threadExampleList.get(i);
            for (int j = 0; j < threadConfigInfoList.size(); j++) {
                ThreadConfigInfo t = threadConfigInfoList.get(j);
                int finalJ = j;
                threadConfigExampleEntityList.add(new ThreadConfigExampleEntity() {{
                    setRef(t.getRef());
                    setSerialNumber(finalJ);
                    setJobId(jobEntity.getId());
                    setType(t.getType().getCode());
                    setJobExampleId(jobExampleEntity.getId());
                    HashMap<String, Object> context = new HashMap<String, Object>(5) {{
                        put("number", t.getNumber());
                        put("tps", t.getTps());
                        put("duration", t.getDuration());
                        put("growthTime", t.getGrowthTime());
                        put("step", t.getGrowthStep());
                    }};
                    setContext(objectMapper.writeValueAsString(context));
                }});
            }
        }
        threadConfigExampleMapperService.saveBatch(threadConfigExampleEntityList);
        // 下发启动命令
        jobExampleEntityList.forEach(t -> commandService.startApplication(t.getId()));
        return jobEntity.getId() + "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(long jobId) {
        // 获取任务
        JobEntity jobEntity = jobMapper.selectById(jobId);
        // 获取任务实例
        List<JobExampleEntity> jobExampleEntityList = jobExampleMapperService.lambdaQuery()
            .eq(JobExampleEntity::getJobId, jobEntity.getId()).list();
        // 逐个停止
        jobExampleEntityList.forEach(t -> {
            ResourceExampleEntity exampleEntity = resourceService.exampleEntity(t.getResourceExampleId());
            commandService.stopApplication(t.getId());
            commandService.releaseResource(exampleEntity.getId());
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ThreadConfigExampleEntity> getConfig(long taskId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyConfig(long jobId, JobConfig context) throws JsonProcessingException {
        // 1. 找到要修改的配置项
        ThreadConfigExampleEntity threadConfigExampleEntity = jobConfigService.threadExampleItem(jobId, context.getRef());
        String contextString = objectMapper.writeValueAsString(context.getContext());
        // 2. 如果没有抛出异常
        if (threadConfigExampleEntity == null) {
            throw new RuntimeException("未找到可修改的配置");
        }
        // 存在即修改
        else {
            // 2.1 更新任务配置实例项
            jobConfigService.modifThreadConfigExample(threadConfigExampleEntity.getId(), context.getMode(), contextString);
            // 2.2 下发命令
        }
        commandService.updateConfig(jobId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobEntity jobEntity(long jobId) {
        return jobMapper.selectById(jobId);
    }

    /**
     * 切分线程组配置
     *
     * @param threadConfigInfoList 要切分的数量
     * @return 切分后的线程组配置
     */
    private List<List<ThreadConfigInfo>> splitThreadConfig(List<ThreadConfigInfo> threadConfigInfoList, int size) {
        List<List<ThreadConfigInfo>> result = new ArrayList<>(threadConfigInfoList.size());
        for (ThreadConfigInfo t : threadConfigInfoList) {
            List<ThreadConfigInfo> itemResult = new ArrayList<>(size);
            List<Integer> tpsList = splitInteger(t.getTps(), size);
            List<Integer> numberList = splitInteger(t.getNumber(), size);
            for (int j = 0; j < size; j++) {
                int finalJ = j;
                itemResult.add(new ThreadConfigInfo() {{
                    setDuration(t.getDuration());
                    setGrowthStep(t.getGrowthStep());
                    setGrowthTime(t.getGrowthTime());
                    setType(t.getType());
                    setRef(t.getRef());
                    setTps(tpsList.get(finalJ));
                    setNumber(numberList.get(finalJ));
                }});
            }
            result.add(itemResult);
        }
        return result;
    }

    /**
     * 切分数值
     * <p>余数平分到每一项</p>
     *
     * @param value 需要分隔的值
     * @param size  分隔的份数
     * @return 结果集合
     */
    private List<Integer> splitInteger(int value, int size) {
        List<Integer> result = new ArrayList<>(size);
        int quotient = value / size, remainder = value % size;
        if (quotient == 0 && remainder != 0) {
            throw new RuntimeException(StrUtil.format("无法把{}分隔成{}份", value, size));
        }
        // 处理商
        for (int i = 0; i < size; i++) {result.add(quotient);}
        // 处理余数
        for (int i = 0; i < remainder; i++) {result.set(i, result.get(i) + 1);}
        return result;
    }
}
