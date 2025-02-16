
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
package com.baidu.rigel.biplatform.tesseract.qsservice.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baidu.rigel.biplatform.ac.exception.MiniCubeQueryException;
import com.baidu.rigel.biplatform.ac.minicube.CallbackLevel;
import com.baidu.rigel.biplatform.ac.minicube.CallbackMember;
import com.baidu.rigel.biplatform.ac.minicube.MiniCube;
import com.baidu.rigel.biplatform.ac.minicube.MiniCubeMeasure;
import com.baidu.rigel.biplatform.ac.minicube.MiniCubeMember;
import com.baidu.rigel.biplatform.ac.model.Cube;
import com.baidu.rigel.biplatform.ac.model.Dimension;
import com.baidu.rigel.biplatform.ac.model.Level;
import com.baidu.rigel.biplatform.ac.model.LevelType;
import com.baidu.rigel.biplatform.ac.model.Member;
import com.baidu.rigel.biplatform.ac.query.data.DataSourceInfo;
import com.baidu.rigel.biplatform.ac.query.model.AxisMeta;
import com.baidu.rigel.biplatform.ac.query.model.AxisMeta.AxisType;
import com.baidu.rigel.biplatform.ac.query.model.DimensionCondition;
import com.baidu.rigel.biplatform.ac.query.model.MetaCondition;
import com.baidu.rigel.biplatform.ac.query.model.QueryData;
import com.baidu.rigel.biplatform.ac.query.model.QuestionModel;
import com.baidu.rigel.biplatform.ac.util.DeepcopyUtils;
import com.baidu.rigel.biplatform.ac.util.MetaNameUtil;
import com.baidu.rigel.biplatform.tesseract.exception.MetaException;
import com.baidu.rigel.biplatform.tesseract.meta.MetaDataService;
import com.baidu.rigel.biplatform.tesseract.meta.impl.CallbackDimensionMemberServiceImpl;
import com.baidu.rigel.biplatform.tesseract.model.MemberNodeTree;
import com.baidu.rigel.biplatform.tesseract.qsservice.query.vo.QueryContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/** 
 *  
 * @author xiaoming.chen
 * @version  2014年12月26日 
 * @since jdk 1.8 or after
 */
@Service
public class QueryContextBuilder {
    
    /**
     * TODO 此处需要移动到指定类中
     */
    public static final String FILTER_DIM_KEY = "filter_Dim_Key";
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    
    @Resource
    private MetaDataService metaDataService;
    
    
    @Autowired
    private CallbackDimensionMemberServiceImpl callbackDimensionService;
    
    
    public static Map<String, String> getRequestParams(QuestionModel questionModel, Cube cube) {
        Map<String, String> rs = Maps.newHashMap();
        rs.putAll(questionModel.getRequestParams());
        StringBuilder filterDimNames = new StringBuilder();
        if (questionModel.getQueryConditions() != null && questionModel.getQueryConditions().size() > 0) {
            questionModel.getQueryConditions().forEach((k, v) -> {
                Dimension dim = cube.getDimensions().get(k);
                MiniCube miniCube = (MiniCube) cube;
                if (dim != null && miniCube.getSource().equals (dim.getTableName())) {
                    DimensionCondition cond = (DimensionCondition) v;
                    StringBuilder sb = new StringBuilder();
                    List<QueryData> queryDataNodes = cond.getQueryDataNodes();
                    int size = queryDataNodes.size();
                    String[] strArray = null;
                    for (int index = 0; index < size; ++index) {
                            QueryData data = queryDataNodes.get(index);
                            strArray = MetaNameUtil.parseUnique2NameArray(data.getUniqueName());
                            sb.append(strArray[strArray.length - 1]);
                            if (index < size - 1) {
                                sb.append(",");
                            }
                    }
                    filterDimNames.append(cond.getMetaName());
                    rs.put(cond.getMetaName(), sb.toString());
                }
            }); 
        }
        rs.put(FILTER_DIM_KEY, filterDimNames.toString());
        return rs;
    }
    /**
     * 构建查询上下文
     * 
     * @param questionModel 问题模型
     * @param dsInfo 数据源信息
     * @param cube cube模型
     * @param queryContext 查询上下文
     * @return 根据问题模型构建的查询上下文
     * @throws MiniCubeQueryException 查询维值异常
     * @throws MetaException
     */
    public QueryContext buildQueryContext(QuestionModel questionModel, DataSourceInfo dsInfo, Cube cube,
            QueryContext queryContext) throws MiniCubeQueryException, MetaException {
        if (queryContext == null) {
            queryContext = new QueryContext();
            QuestionModel cloneQuestionModel = DeepcopyUtils.deepCopy(questionModel);
            long current = System.currentTimeMillis();
            AxisMeta axisMeta = null;
            AxisType axisType = AxisType.COLUMN;
            Map<String, String> requestParams = questionModel.getRequestParams();
            while (axisType != null && (axisMeta = cloneQuestionModel.getAxisMetas().get(axisType)) != null) {
                if (CollectionUtils.isNotEmpty(axisMeta.getCrossjoinDims())) {
                    int i = 0;
                    for (String dimName : axisMeta.getCrossjoinDims()) {
                        DimensionCondition dimCondition =
                                (DimensionCondition) cloneQuestionModel.getQueryConditions().remove(dimName);
                        if (dimCondition == null) {
                            dimCondition = new DimensionCondition(dimName);
                        }
                        queryContext.addMemberNodeTreeByAxisType(axisType,
                                buildQueryMemberTree(dsInfo, cube, dimCondition, i == 0, 
                                questionModel.getRequestParams()));
                        i++;
                    }
                }
                logger.info("cost:{}ms in build axisTye:{},axisMeta:{}",System.currentTimeMillis() - current,axisType,axisMeta);
                current = System.currentTimeMillis();
                if (CollectionUtils.isNotEmpty(axisMeta.getQueryMeasures())) {
                    for (String measureName : axisMeta.getQueryMeasures()) {
                        if (cube.getMeasures().containsKey(measureName)) {
                            queryContext.getQueryMeasures().add((MiniCubeMeasure) cube.getMeasures().get(measureName));
                        }
                        // 需要判断，如果cube里面不包含的话，那么这个名称可能是个计算公式，需要进行构造一个虚拟的名称扔进去
                    }
                }
                logger.info("cost:{}ms in build axisTye:{},axisMeta:{}",System.currentTimeMillis() - current,axisType,axisMeta);
                current = System.currentTimeMillis();
                if (axisType.equals(AxisType.ROW)) {
                    axisType = null;
                } else {
                    axisType = AxisType.ROW;
                }
            }
            
            if (!cloneQuestionModel.getQueryConditions().isEmpty()) {
                for (MetaCondition condition : cloneQuestionModel.getQueryConditions().values()) {
                    if (condition == null) {
                        logger.warn("meta condition is null,skip.");
                        continue;
                    }
                    if (condition instanceof DimensionCondition) {
                        DimensionCondition dimCondition = (DimensionCondition) condition;
                        Map<String, Set<String>> filterCondition = buildFilterCondition(dsInfo, cube, dimCondition, requestParams);
                        if (MapUtils.isNotEmpty(filterCondition)) {
                            queryContext.getFilterMemberValues().putAll(filterCondition);
                        }
                    } 
//                    else {
//                        MeasureCondition measureCon = (MeasureCondition) condition;
                        // TODO 暂时这个还不会生效
//                        SQLCondition sqlCondition = measureCon.getMeasureConditions();
                        // TODO
//                        List<String> expression = Lists.newArrayList();
//                        for (SQLCondition sqlCondition : conditions) {
//                            String expressStr = sqlCondition.parseToExpression();
//                            expression.add(expressStr);
//                        }
//                        queryContext.getFilterExpression().put(measureCon.getMetaName(), expression);
//                    }
                    logger.info ("cost:{}ms,in build filter conditon:{}",System.currentTimeMillis() - current,condition);
//                    logger.info("cost:{}ms,in build filter",System.currentTimeMillis() - current);
                    current = System.currentTimeMillis();
                }
            }
        }
        return queryContext;
    }
    
    /**
     * 构造过滤条件
     * 
     * @param dataSourceInfo 数据源信息
     * @param cube cube信息
     * @param dimCondition 维度信息
     * @param params 查询条件
     * @return 过滤条件
     * @throws MiniCubeQueryException 查询异常
     * @throws MetaException 元数据异常信息
     */
    public Map<String, Set<String>> buildFilterCondition(DataSourceInfo dataSourceInfo, Cube cube,
            DimensionCondition dimCondition, Map<String, String> params) throws MiniCubeQueryException, MetaException {
        if (dimCondition == null) {
            throw new IllegalArgumentException("dimension condition is null");
        }
        if (dimCondition.getQueryDataNodes().isEmpty()) {
            logger.info("filter axises ignore all member filter");
            return null;
        }
        Map<String, Set<String>> filterValues = new HashMap<>();
        
        Dimension dimension = cube.getDimensions().get(dimCondition.getMetaName());
        boolean hasCallbackLevel = false;
        int callbackLevelIndex = 0;
        List<String> callbackParams = null;
        if (dimension == null) {
            return null;
        }
        List<Level> levels = Lists.newArrayList(dimension.getLevels().values());
        if (levels.size () <= 0) {
            throw new RuntimeException("当前维度定义错误");
        }
        
        for(int i = 0; i < levels.size(); i++) {
            if(levels.get(i).getType().equals(LevelType.CALL_BACK)) {
                hasCallbackLevel = true;
                callbackLevelIndex = i;
                callbackParams = new ArrayList<>();
                break;
            }
        }
        boolean onlyOneTable = true;
        if (!hasCallbackLevel) {
            String tableName = levels.get (0).getDimTable ();
            for (int i = 1; i < levels.size (); ++i) {
                if (StringUtils.isEmpty(tableName) && StringUtils.isNotEmpty(levels.get (i).getDimTable ())) {
                    onlyOneTable = false;
                    break;
                } else if (StringUtils.isNotEmpty(tableName) && !tableName.equals(levels.get (i).getDimTable ())) {
                    onlyOneTable = false;
                    break;
                }
            }
        }
         // 优化查询逻辑
        // 1.优先过滤全部节点查询条件，避免后续做更多无用功
        for (QueryData queryData : dimCondition.getQueryDataNodes()) {
            if (MetaNameUtil.isAllMemberUniqueName(queryData.getUniqueName()) 
                && !dimension.isTimeDimension ()) {
                logger.info("filter axises ignore all member filter");
                return null;
            }
        }
        
        /**
         *  2. 对于同一张表中的维度过滤，进行一次查询，返回结果
         *  当前查询不考虑跨层级过滤问题
         */
        if (!hasCallbackLevel && onlyOneTable && !dimension.isTimeDimension ()) {
            List<String> uniqueNameList = dimCondition.getQueryDataNodes ().stream ()
                    .filter (data -> MetaNameUtil.isUniqueName (data.getUniqueName()))
                    .map (data -> data.getUniqueName ())
                    .collect (Collectors.toList ());
            try {
                List<MiniCubeMember> members = metaDataService.lookUp (dataSourceInfo, cube, uniqueNameList, params);
                if (CollectionUtils.isNotEmpty (members)) {
                    final Set<String> queryNodes = Sets.newHashSet ();
                    members.stream ().forEach (m -> {
                        if (CollectionUtils.isEmpty (m.getQueryNodes ())) {
                            queryNodes.add (m.getName());
                        } else {
                            queryNodes.addAll(m.getQueryNodes ());
                        }
                    });
                    filterValues.put (members.get (0).getLevel ().getFactTableColumn (), queryNodes);
                }
                return filterValues;
            } catch (Exception e) {
                logger.error (e.getMessage (), e);
            }
            return null;
        }
        
        for (QueryData queryData : dimCondition.getQueryDataNodes()) {
            String[] names = MetaNameUtil.parseUnique2NameArray(queryData.getUniqueName());
            if (hasCallbackLevel && (names.length - 2 == callbackLevelIndex)) {
                callbackParams.add(names[names.length - 1]);
                continue;
            } else {
                MiniCubeMember member = metaDataService.lookUp(dataSourceInfo, cube, queryData.getUniqueName(), params);
                if (member != null) {
                    String querySource = member.getLevel().getFactTableColumn();
                    Set<String> nodes =
                            CollectionUtils.isEmpty(member.getQueryNodes()) 
                            ? Sets.newHashSet(member.getName()) : member.getQueryNodes();
                    if (filterValues.containsKey(querySource)) {
                        filterValues.get(querySource).addAll(nodes);
                    } else {
                        filterValues.put(querySource, nodes);
                    }
                } else {
                    logger.warn("can not found member by query data:{}", queryData);
                }
            }
        }
        if(hasCallbackLevel && CollectionUtils.isNotEmpty(callbackParams)) {
            Map<String, String> newParams = new HashMap<>(params);
            newParams.put(dimCondition.getMetaName(), StringUtils.join(callbackParams, ","));
            Level callbackLevel = levels.get(callbackLevelIndex);
            List<MiniCubeMember> callbackMembers = 
                callbackDimensionService.getMembers(cube, callbackLevel, dataSourceInfo, null, newParams);
            String querySource = null;
            if(CollectionUtils.isNotEmpty(callbackMembers)) {
                for(MiniCubeMember member : callbackMembers) {
                    querySource = member.getLevel().getFactTableColumn();
                    Set<String> nodes =
                            CollectionUtils.isEmpty(member.getQueryNodes()) 
                            ? Sets.newHashSet(member.getName()) : member.getQueryNodes();
                    if (filterValues.containsKey(querySource)) {
                        filterValues.get(querySource).addAll(nodes);
                    } else {
                        filterValues.put(querySource, nodes);
                    }
                }
            }
        }

        return filterValues;
    }
    
    
    /**
     * 根据维值选中条件构造维值查询的树
     * 
     * @param dataSourceInfo 数据源信息
     * @param cube cube模型
     * @param dimCondition 维值查询条件
     * @param isFirstInRow 是否是行上的第一个维度
     * @return 维值树
     * @throws MiniCubeQueryException 查询维值异常
     * @throws MetaException
     */
    public MemberNodeTree buildQueryMemberTree(DataSourceInfo dataSourceInfo, Cube cube,
            DimensionCondition dimCondition, boolean isFirstInRow, Map<String, String> params)
            throws MiniCubeQueryException, MetaException {
        if (dimCondition == null) {
            throw new IllegalArgumentException("dimension condition is null");
        }
        long current = System.currentTimeMillis();
        MemberNodeTree nodeTree = new MemberNodeTree(null);
        if (dimCondition.getQueryDataNodes().isEmpty()) {
            String allMemberUniqueName =
                    cube.getDimensions().get(dimCondition.getMetaName()).getAllMember().getUniqueName();
            QueryData queryData = new QueryData(allMemberUniqueName);
            queryData.setExpand(isFirstInRow);
            queryData.setShow(true);
            dimCondition.getQueryDataNodes().add(queryData);
            logger.info("cost:{}ms,in build default member:{}",System.currentTimeMillis() - current, dimCondition);
            current = System.currentTimeMillis();
        }
        Dimension dimension = cube.getDimensions().get(dimCondition.getMetaName());
        boolean hasCallbackLevel = false;
        int callbackLevelIndex = 0;
        List<String> callbackParams = null;
        List<Level> levels = Lists.newArrayList(dimension.getLevels().values());
        for(int i = 0; i < levels.size(); i++) {
            if(levels.get(i).getType().equals(LevelType.CALL_BACK)) {
                hasCallbackLevel = true;
                callbackLevelIndex = i;
                callbackParams = new ArrayList<>();
                break;
            }
        }
        
        Map<Level, List<String>> tmp = Maps.newConcurrentMap ();
        for (QueryData queryData : dimCondition.getQueryDataNodes()) {
            String[] names = MetaNameUtil.parseUnique2NameArray(queryData.getUniqueName());
            if (hasCallbackLevel && (names.length - 2 == callbackLevelIndex)) {
                callbackParams.add(names[names.length - 1]);
                continue;
            } else {
                Level level = levels.get (names.length - 2);
                if (tmp.containsKey (level)) {
                    tmp.get (level).add (queryData.getUniqueName ());
                } else {
                    List<String> list = Lists.newArrayList ();
                    list.add (queryData.getUniqueName ());
                    tmp.put (level, list);
                }
            }
        }    
        
        Map<String, MiniCubeMember> memberRepository = Maps.newConcurrentMap ();
        for (Level level : tmp.keySet ()) {
            List<String> datas = tmp.get (level);
            try {
                List<MiniCubeMember> rs = metaDataService.lookUp (dataSourceInfo, cube, datas, params);
                if (rs != null) {
                    for (MiniCubeMember m : rs) {
                        memberRepository.put (m.getUniqueName (), m);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace ();
            }
        }
        
        for (QueryData queryData : dimCondition.getQueryDataNodes()) {
            String[] names = MetaNameUtil.parseUnique2NameArray(queryData.getUniqueName());
            
            if (hasCallbackLevel && (names.length - 2 == callbackLevelIndex)) {
                callbackParams.add(names[names.length - 1]);
                continue;
            } else {
                MiniCubeMember member = memberRepository.get (queryData.getUniqueName ());
                if (member == null) {
                    member = metaDataService.lookUp(dataSourceInfo, cube, queryData.getUniqueName(), params);
                }
                MemberNodeTree memberNode = new MemberNodeTree(nodeTree);
                List<MemberNodeTree> childNodes = new ArrayList<MemberNodeTree>();
                // 如果接到设置了下钻 或者 当前维度在行上第一个并且只有一个选中节点,
                // FIXME  需要考虑展开的下层是一个Callback层级的情况，这里未测试
                if (queryData.isExpand()) {
                    List<MiniCubeMember> children = Lists.newArrayList();
                    try {
                        children = metaDataService.getChildren(dataSourceInfo, cube, member, params);
                    } catch (Exception e) {
                        // TODO NONE 需要确认是否有问题 目前测试没有看出问题
                    }
                    if (CollectionUtils.isNotEmpty(children)) {
                        memberNode.setSummary(true);
                        children.forEach((child) -> {
                            MemberNodeTree childNode = new MemberNodeTree(nodeTree);
                            childNode = buildMemberNodeByMember(dataSourceInfo, cube, childNode, child, params);
                            childNodes.add(childNode);
//                        memberNode.getQueryNodes().addAll(child.getQueryNodes());
                        });
                    }
                }
                // 如果当前孩子为空或者当前节点是要展现，那么直接把本身扔到要展现列表中
                if (queryData.isShow() || CollectionUtils.isEmpty(childNodes)) {
                    memberNode = buildMemberNodeByMember(dataSourceInfo, cube, memberNode, member, params);
                    memberNode.setChildren(childNodes);
                    nodeTree.getChildren().add(memberNode);
//                return memberNode;
                } else {
                    nodeTree.getChildren().addAll(childNodes);
                }
            }
            
        } 
        if(hasCallbackLevel && CollectionUtils.isNotEmpty(callbackParams)) {
            Map<String, String> newParams = new HashMap<>(params);
            newParams.put(dimCondition.getMetaName(), StringUtils.join(callbackParams, ","));
            Level callbackLevel = levels.get(callbackLevelIndex);
            List<MiniCubeMember> callbackMembers = 
                callbackDimensionService.getMembers(cube, callbackLevel, dataSourceInfo, null, newParams);
            if(CollectionUtils.isNotEmpty(callbackMembers)) {
                if(callbackMembers.size() == 1) { 
                    List<Member> children = callbackMembers.get(0).getChildren();
                    MemberNodeTree parentNode = new MemberNodeTree(nodeTree);  
                    parentNode.setCallback (true);
                    parentNode.setSummaryIds (callbackMembers.get (0).getQueryNodes ());
                    buildMemberNodeByMember(dataSourceInfo, cube, parentNode, callbackMembers.get(0), params);
                    if (CollectionUtils.isNotEmpty (children)) { 
                        children.forEach((child) -> {
                            MemberNodeTree childNode = new MemberNodeTree(nodeTree);
                            MiniCubeMember child2 = (MiniCubeMember) child;
                            childNode = 
                                buildMemberNodeByMember(dataSourceInfo, cube, childNode, child2, params);
                            parentNode.getChildren().add(childNode);
                        });
                    }
                    nodeTree.getChildren().add(parentNode);
                } else {
                    callbackMembers.forEach((child) -> {
                        MemberNodeTree childNode = new MemberNodeTree(nodeTree);
                        childNode = buildMemberNodeByMember(dataSourceInfo, cube, childNode, child, params);
                        nodeTree.getChildren().add(childNode);
                    });
                }
            }
        }
        // 非DESC的都按ASC排序。
        nodeTree.sort(dimCondition.getMemberSortType());
        logger.info("cost:{}ms,in build dimCondition:{}",System.currentTimeMillis() - current, dimCondition);
        return nodeTree;
    }
    
    

    /**
     * 根据维值创建查询树的节点
     * @param node 查询节点
     * @param member 维值
     */
    private MemberNodeTree buildMemberNodeByMember(DataSourceInfo dataSource, 
            Cube cube, MemberNodeTree node, MiniCubeMember member, Map<String, String> params) {
        node.setCaption(member.getCaption());
        node.setTime(member.getLevel().getDimension().isTimeDimension());
        if (CollectionUtils.isNotEmpty(member.getQueryNodes())) {
            node.setLeafIds(member.getQueryNodes());
        } else {
            node.getLeafIds().add(member.getName());
        }
        node.setName(member.getName());
        node.setUniqueName(member.getUniqueName());
        node.setOrdinal(member.getName());
        // 设置查询的来源，如事实表的字段
        node.setQuerySource(member.getLevel().getFactTableColumn());

        // 后续需要对孩子节点进行下查询，本次对是否有孩子的判断只是按照是否有下一个层级
        if (member.isAll()) {
            node.setHasChildren(true);
        } else if (member.getLevel() instanceof CallbackLevel) {
            CallbackMember m = (CallbackMember) member;
            if (CollectionUtils.isNotEmpty(member.getQueryNodes()) ) {
                if (member.getQueryNodes ().size () == 1 && member.getQueryNodes ().contains (member.getName ())) {
                    node.setHasChildren (false);
                } else {
                    node.setHasChildren(true);
                }
                node.setCallback (true);
                node.setSummaryIds (node.getLeafIds ());
            } else {
                node.setHasChildren (m.isHasChildren ());
            }
        } else {
            // TODO 后续考虑维度预加载
            List<MiniCubeMember> children = null;
            try {
                children = metaDataService.getChildren(dataSource, cube, member, params);
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
            if (CollectionUtils.isNotEmpty(children)) {
                node.setHasChildren(true);
            }
//            Dimension dim = member.getLevel().getDimension();
//            List<String> levelNames = Lists.newArrayList(dim.getLevels().keySet());
//            for (int i = 0; i < levelNames.size(); i++) {
//                if (member.getLevel().getName().equals(levelNames.get(i))) {
//                    if (i < levelNames.size() - 1) {
//                        node.setHasChildren( member.isHasChildren ());
//                    }
//                    break;
//                }
//            }
        }
        final MiniCubeMember memberParent = (MiniCubeMember) member.getParent ();
        if (memberParent != null) {
            
            MemberNodeTree parent = new MemberNodeTree (node.getParent ());
            parent.setCaption (memberParent.getCaption ());
            parent.setUniqueName (memberParent.getUniqueName ());
            parent.setName (memberParent.getName ());
            parent.setLeafIds (memberParent.getQueryNodes ());
            parent.setSummaryIds (memberParent.getQueryNodes ());
            parent.setCallback (memberParent.getLevel () instanceof CallbackLevel);
            parent.setQuerySource (memberParent.getLevel ().getFactTableColumn ());
            node.setParent (parent);
            parent.getChildren ().add (node);
//            return parent;
        }
        return node;
    }

    /**
     * for test
     * @param service
     */
    public void setMetaDataService(MetaDataService service) {
        this.metaDataService = service;
    }
}

