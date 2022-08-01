package io.shulie.takin.cloud.app.service.impl;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.text.CharSequenceUtil;
import org.springframework.stereotype.Service;

import io.shulie.takin.cloud.data.entity.MetricsEntity;
import io.shulie.takin.cloud.app.service.JobConfigService;
import io.shulie.takin.cloud.data.entity.ThreadConfigEntity;
import io.shulie.takin.cloud.constant.enums.ThreadGroupType;
import io.shulie.takin.cloud.data.service.MetricsMapperService;
import io.shulie.takin.cloud.data.entity.ThreadConfigExampleEntity;
import io.shulie.takin.cloud.data.service.ThreadConfigMapperService;
import io.shulie.takin.cloud.data.service.ThreadConfigExampleMapperService;

/**
 * 任务配置服务 - 实例
 *
 * @author <a href="mailto:472546172@qq.com">张天赐</a>
 */
@Slf4j
@Service
public class JobConfigServiceImpl implements JobConfigService {
    @javax.annotation.Resource(name = "metricsMapperServiceImpl")
    MetricsMapperService metricsMapper;
    @javax.annotation.Resource(name = "threadConfigMapperServiceImpl")
    ThreadConfigMapperService threadConfigMappe;
    @javax.annotation.Resource(name = "threadConfigExampleMapperServiceImpl")
    ThreadConfigExampleMapperService threadConfigExampleMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MetricsEntity> metricsList(long jobId) {
        return metricsMapper.lambdaQuery()
            .eq(MetricsEntity::getJobId, jobId)
            .list();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ThreadConfigExampleEntity> threadList(long jobId) {
        return threadConfigExampleMapper.lambdaQuery()
            .eq(ThreadConfigExampleEntity::getJobId, jobId)
            .list();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ThreadConfigExampleEntity> threadExampleItem(long jobId, String ref) {
        return threadConfigExampleMapper.lambdaQuery()
            .eq(ThreadConfigExampleEntity::getJobId, jobId)
            .eq(CharSequenceUtil.isNotBlank(ref), ThreadConfigExampleEntity::getRef, ref)
            .list();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifThreadConfigExample(long threadConfigExampleId, ThreadGroupType type, String context) {
        threadConfigExampleMapper.updateById(new ThreadConfigExampleEntity()
            .setContext(context)
            .setType(type.getCode())
            .setId(threadConfigExampleId)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createMetrics(List<MetricsEntity> metricsEntityList) {
        metricsMapper.saveBatch(metricsEntityList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createThread(List<ThreadConfigEntity> metricsEntityList) {
        threadConfigMappe.saveBatch(metricsEntityList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createThreadExample(List<ThreadConfigExampleEntity> threadConfigExampleEntityList) {
        threadConfigExampleMapper.saveBatch(threadConfigExampleEntityList);
    }
}
