package com.xiuz.stux.elastic.job.lite.jobs;

import io.elasticjob.lite.api.ShardingContext;
import io.elasticjob.lite.api.dataflow.DataflowJob;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Auther: wuxiuzhao
 * @Date: 2020/1/2 16:25
 * @Description:
 */
@Slf4j
public class MyDataflowJob implements DataflowJob<Integer> {
    @Override
    public List<Integer> fetchData(ShardingContext shardingContext) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.warn("stux elastic dataflow job fetch data: >>>>>>>>>>>>> sharding current time : {} , job name : {}, sharding total count : {}, task id : {} , sharding item : {}, job parameter : {}, sharding parameter : {}",
                format.format(new Date()), shardingContext.getJobName(), shardingContext.getShardingTotalCount(), shardingContext.getTaskId(),
                shardingContext.getShardingItem(), shardingContext.getJobParameter(), shardingContext.getShardingParameter());
        final List<Integer> list = new ArrayList<>();
        list.add(1);
        return list;
    }

    @Override
    public void processData(ShardingContext shardingContext, List<Integer> data) {
        log.warn("stux elastic dataflow job process data : {}", data);
    }
}
