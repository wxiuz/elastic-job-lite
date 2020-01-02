package com.xiuz.stux.elastic.job.lite.jobs;

import io.elasticjob.lite.api.ShardingContext;
import io.elasticjob.lite.api.simple.SimpleJob;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Auther: wuxiuzhao
 * @Date: 2020/1/2 16:19
 * @Description:
 */
@Slf4j
public class MySimpleJob implements SimpleJob {
    @Override
    public void execute(ShardingContext shardingContext) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.warn("stux elastic simple job: >>>>>>>>>>>>> sharding current time : {} , job name : {}, sharding total count : {}, task id : {} , sharding item : {}, job parameter : {}, sharding parameter : {}",
                format.format(new Date()), shardingContext.getJobName(), shardingContext.getShardingTotalCount(), shardingContext.getTaskId(),
                shardingContext.getShardingItem(), shardingContext.getJobParameter(), shardingContext.getShardingParameter());
    }
}
