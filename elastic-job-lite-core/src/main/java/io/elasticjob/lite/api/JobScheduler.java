/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.elasticjob.lite.api;

import com.google.common.base.Optional;
import io.elasticjob.lite.api.listener.AbstractDistributeOnceElasticJobListener;
import io.elasticjob.lite.api.listener.ElasticJobListener;
import io.elasticjob.lite.api.script.ScriptJob;
import io.elasticjob.lite.api.strategy.JobInstance;
import io.elasticjob.lite.config.LiteJobConfiguration;
import io.elasticjob.lite.event.JobEventBus;
import io.elasticjob.lite.event.JobEventConfiguration;
import io.elasticjob.lite.exception.JobConfigurationException;
import io.elasticjob.lite.exception.JobSystemException;
import io.elasticjob.lite.executor.JobFacade;
import io.elasticjob.lite.internal.guarantee.GuaranteeService;
import io.elasticjob.lite.internal.schedule.*;
import io.elasticjob.lite.reg.base.CoordinatorRegistryCenter;
import lombok.Getter;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * 作业调度器. 每个作业都有一个单独的作业调度器
 *
 * @author zhangliang
 * @author caohao
 */
public class JobScheduler {

    public static final String ELASTIC_JOB_DATA_MAP_KEY = "elasticJob";

    private static final String JOB_FACADE_DATA_MAP_KEY = "jobFacade";

    // 作业配置
    private final LiteJobConfiguration liteJobConfig;

    // 注册中心配置
    private final CoordinatorRegistryCenter regCenter;

    // TODO 为测试使用,测试用例不能反复new monitor service,以后需要把MonitorService重构为单例
    @Getter
    private final SchedulerFacade schedulerFacade;

    private final JobFacade jobFacade;

    public JobScheduler(final CoordinatorRegistryCenter regCenter, final LiteJobConfiguration liteJobConfig, final ElasticJobListener... elasticJobListeners) {
        this(regCenter, liteJobConfig, new JobEventBus(), elasticJobListeners);
    }

    public JobScheduler(final CoordinatorRegistryCenter regCenter, final LiteJobConfiguration liteJobConfig, final JobEventConfiguration jobEventConfig,
                        final ElasticJobListener... elasticJobListeners) {
        this(regCenter, liteJobConfig, new JobEventBus(jobEventConfig), elasticJobListeners);
    }

    private JobScheduler(final CoordinatorRegistryCenter regCenter, final LiteJobConfiguration liteJobConfig, final JobEventBus jobEventBus, final ElasticJobListener... elasticJobListeners) {
        // JobRegistry可看做为缓存信息
        JobRegistry.getInstance().addJobInstance(liteJobConfig.getJobName(), new JobInstance());
        this.liteJobConfig = liteJobConfig;
        this.regCenter = regCenter;
        List<ElasticJobListener> elasticJobListenerList = Arrays.asList(elasticJobListeners);
        setGuaranteeServiceForElasticJobListeners(regCenter, elasticJobListenerList);
        // 调度工具类
        schedulerFacade = new SchedulerFacade(regCenter, liteJobConfig.getJobName(), elasticJobListenerList);
        // 作业工具类
        jobFacade = new LiteJobFacade(regCenter, liteJobConfig.getJobName(), Arrays.asList(elasticJobListeners), jobEventBus);
    }

    private void setGuaranteeServiceForElasticJobListeners(final CoordinatorRegistryCenter regCenter, final List<ElasticJobListener> elasticJobListeners) {
        GuaranteeService guaranteeService = new GuaranteeService(regCenter, liteJobConfig.getJobName());
        for (ElasticJobListener each : elasticJobListeners) {
            if (each instanceof AbstractDistributeOnceElasticJobListener) {
                ((AbstractDistributeOnceElasticJobListener) each).setGuaranteeService(guaranteeService);
            }
        }
    }

    /**
     * 初始化作业.
     */
    public void init() {
        // 将作业配置信息写入到zk上的config节点
        LiteJobConfiguration liteJobConfigFromRegCenter = schedulerFacade.updateJobConfiguration(liteJobConfig);
        // 缓存作业的分片总数
        JobRegistry.getInstance().setCurrentShardingTotalCount(liteJobConfigFromRegCenter.getJobName(), liteJobConfigFromRegCenter.getTypeConfig().getCoreConfig().getShardingTotalCount());
        // 创建quartz调度器和quartz作业
        JobScheduleController jobScheduleController = new JobScheduleController(
                createScheduler(), createJobDetail(liteJobConfigFromRegCenter.getTypeConfig().getJobClass()), liteJobConfigFromRegCenter.getJobName());
        JobRegistry.getInstance().registerJob(liteJobConfigFromRegCenter.getJobName(), jobScheduleController, regCenter);
        // 初始化作业信息至zk上
        schedulerFacade.registerStartUpInfo(!liteJobConfigFromRegCenter.isDisabled());
        // 调度作业，将作业交由quartz来调度
        jobScheduleController.scheduleJob(liteJobConfigFromRegCenter.getTypeConfig().getCoreConfig().getCron());
    }

    /**
     * 创建quartz的作业
     *
     * @param jobClass
     * @return
     */
    private JobDetail createJobDetail(final String jobClass) {
        // elastic-job中的所有定义的作业都转换为quartz的LiteJob，quartz每次触发作业调度都会创建一个新的LiteJob实例
        JobDetail result = JobBuilder.newJob(LiteJob.class).withIdentity(liteJobConfig.getJobName()).build();
        result.getJobDataMap().put(JOB_FACADE_DATA_MAP_KEY, jobFacade);
        Optional<ElasticJob> elasticJobInstance = createElasticJobInstance();
        if (elasticJobInstance.isPresent()) {
            result.getJobDataMap().put(ELASTIC_JOB_DATA_MAP_KEY, elasticJobInstance.get());
        } else if (!jobClass.equals(ScriptJob.class.getCanonicalName())) {
            try {
                result.getJobDataMap().put(ELASTIC_JOB_DATA_MAP_KEY, Class.forName(jobClass).newInstance());
            } catch (final ReflectiveOperationException ex) {
                throw new JobConfigurationException("Elastic-Job: Job class '%s' can not initialize.", jobClass);
            }
        }
        return result;
    }

    protected Optional<ElasticJob> createElasticJobInstance() {
        return Optional.absent();
    }

    /**
     * 创建Quartz的调度器，该调度器基于内存的操作，线程池中工作线程数量为1，
     * 所以Elastic-Job的底层还是依赖于Quartz来完成作业的调度
     *
     * @return
     */
    private Scheduler createScheduler() {
        Scheduler result;
        try {
            // 创建Quartz的调度器工厂
            StdSchedulerFactory factory = new StdSchedulerFactory();
            // 初始化Quartz调度器工厂，用于配置调度器
            factory.initialize(getBaseQuartzProperties());
            // 获取Quartz调度器
            result = factory.getScheduler();
            result.getListenerManager().addTriggerListener(schedulerFacade.newJobTriggerListener());
        } catch (final SchedulerException ex) {
            throw new JobSystemException(ex);
        }
        return result;
    }

    private Properties getBaseQuartzProperties() {
        Properties result = new Properties();
        // 使用Quartz自带的线程池：线程池用于来执行具体的Job任务
        result.put("org.quartz.threadPool.class", org.quartz.simpl.SimpleThreadPool.class.getName());
        // 线程池数量配置为1：在quartz中，任务调度线程会在线程池有可用线程后才能继续触发，所以当一个Job执行时间太长，会导致
        // 调度线程没法查询出需要触发的任务进行触发【因为调度线程被Block住】
        result.put("org.quartz.threadPool.threadCount", "1");
        // job名称就是调度器名称
        result.put("org.quartz.scheduler.instanceName", liteJobConfig.getJobName());
        result.put("org.quartz.jobStore.misfireThreshold", "1");
        result.put("org.quartz.plugin.shutdownhook.class", JobShutdownHookPlugin.class.getName());
        result.put("org.quartz.plugin.shutdownhook.cleanShutdown", Boolean.TRUE.toString());
        return result;
    }
}
