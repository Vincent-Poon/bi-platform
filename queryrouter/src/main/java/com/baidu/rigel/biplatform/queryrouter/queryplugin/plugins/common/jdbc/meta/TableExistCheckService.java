/**
 * Copyright (c) 2014 Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baidu.rigel.biplatform.queryrouter.queryplugin.plugins.common.jdbc.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.baidu.rigel.biplatform.ac.query.data.impl.SqlDataSourceInfo;
import com.baidu.rigel.biplatform.queryrouter.handle.QueryRouterContext;
import com.baidu.rigel.biplatform.queryrouter.queryplugin.plugins.common.jdbc.JdbcHandler;
import com.baidu.rigel.biplatform.queryrouter.queryplugin.plugins.model.SqlConstants;

/**
 * 
 * Description: TableExistCheck验证sql中from中的table是否在数据库中存在
 * 
 * @author 罗文磊
 *
 */
@Service("tableExistCheckService")
@Scope("prototype")
public class TableExistCheckService {

    /**
     * mysql中tablename的字段标示
     */
    private static final String META_MYSQL_TABLENAME = "table_name";
    /**
     * logger
     */
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * JdbcConnectionPool
     */
    @Resource(name = "jdbcHandler")
    private JdbcHandler jdbcHandler;

    /**
     * @param cubeSource
     * @return List<String> 存在的表
     */
    public String getExistTableList(String cubeSource,
            SqlDataSourceInfo dataSourceInfo) {
        // 多事实表的情况
        List<String> result = new ArrayList<String>();
        Set<String> set = new HashSet<String>(Arrays.asList(cubeSource
                .split(SqlConstants.COMMA)));
        logger.info("queryId:{} search tables from database, check tables '{}' exists.",
                QueryRouterContext.getQueryId(), cubeSource);
        if (SqlConstants.DRIVER_MYSQL.equals(dataSourceInfo.getDataBase()
                .getDriver())) {
            String sql = "select table_name from information_schema.tables where table_schema='"
                            + dataSourceInfo.getInstanceName() + "'";
            List<Map<String, Object>> datas = jdbcHandler.queryForList(
                    sql, new ArrayList<Object>(), dataSourceInfo);
            datas.forEach((row) -> {
                String tableName = row.get(META_MYSQL_TABLENAME).toString();
                if (set.contains(tableName)) {
                    result.add(tableName);
                }
            });
        } else {
            logger.info("queryId:{} no available driver handler match:{}",
                    QueryRouterContext.getQueryId(), dataSourceInfo.getDataBase().getDriver());
        }
        String resultStr = "";
        if (CollectionUtils.isEmpty(result)) {
            logger.warn("queryId:{} Table :'{}' not in database. please check the source table!",
                    QueryRouterContext.getQueryId(), cubeSource);
            return resultStr;
        } else {
            for (String tableName : result) {
                resultStr = resultStr + tableName + SqlConstants.COMMA;
            }
            resultStr = resultStr.substring(0, resultStr.length() - 1);
        }
        logger.info("queryId:{} found tables: '{}' in database.",
                QueryRouterContext.getQueryId(), resultStr);
        return resultStr;
    }
}