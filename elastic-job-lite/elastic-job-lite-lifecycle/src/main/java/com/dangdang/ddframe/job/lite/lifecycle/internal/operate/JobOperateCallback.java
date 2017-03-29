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

package com.dangdang.ddframe.job.lite.lifecycle.internal.operate;

/**
 * 作业操作的回调接口.
 *
 * @author zhangliang
 */
public interface JobOperateCallback {
    
    /**
     * 操作作业.
     * 
     * @param jobName 作业名称
     * @param serverIp 服务器地址
     * @param serverInstanceId 服务器实例ID
     * @return 操作是否成功
     */
    boolean doOperate(String jobName, String serverIp, String serverInstanceId);
}