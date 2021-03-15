package com.pcode.demo.service.Impl;

import com.alibaba.fastjson.JSONObject;
import com.pcode.demo.dao.ProjectDao;
import com.pcode.demo.dao.UserInfoDao;
import com.pcode.demo.dto.*;
import com.pcode.demo.service.ProjectService;
import com.pcode.demo.util.ParemeterUstil;
import com.pcode.demo.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service(value = "projectService")
public class ProjectServiceImpl implements ProjectService {
     static int MAX_NUMBER=5;
    @Resource
    ProjectDao projectDao;
    @Resource
    UserInfoDao userInfoDao1;
    @Autowired
    private StringRedisTemplate redisTemplate;
    /*
    * 创建一个项目，添加组员关系，并给创建者将项目id加入redis中(最近项目)详情
    */
    @Override
    public GeneralDto addProject(ProjectVo projectVo) {
        String userId = redisTemplate.opsForValue().get("user");
        ProjectInfo info = new ProjectInfo();
        info.setCreated_at(System.currentTimeMillis());
        info.setCreated_by(userId);
        info.setName(projectVo.getName());
        info.setLogo(projectVo.getLogo());
        String projectId = RandomUtil.getRandomString(20);
        info.setId(projectId);
        projectDao.addProject(info);
        String key="recent_browse_lis:"+userId;
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> range = zSet.reverseRangeWithScores(key, 0, MAX_NUMBER - 1);
        ArrayList<String> list = new ArrayList<>();
        for(ZSetOperations.TypedTuple<String> temp:range){
            list.add(temp.getValue());
        }
        GeneralDto<Object> generalDto = new GeneralDto<>();
        generalDto.setRetMsg("操作成功");
        generalDto.setRetCode("000000");
        return generalDto;
    }
    /*
    * 查最近浏览数据
    */
    @Override
    public GeneralDto recentProjectList() throws ParseException {
        GeneralDto<ProjectInfo<CusServiceInfo>> generalDto = new GeneralDto<>();
        String userId = redisTemplate.opsForValue().get("user");
        String key="recent_browse_lis:"+userId;
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> range = zSet.reverseRangeWithScores(key, 0, MAX_NUMBER - 1);
        ArrayList<String> list = new ArrayList<>();
        for(ZSetOperations.TypedTuple<String> temp:range){
            list.add(temp.getValue());
        }
        if(list.size()>0){
            List<ProjectInfo<CusServiceInfo>> projects = projectDao.projectById(list);
            for(ProjectInfo<CusServiceInfo>temp:projects){
                temp.setNumber(temp.getTeam().split(",").length);
            }
            generalDto.setItems(this.turnKey(projects));
        }
        generalDto.setRetCode("000000");
        generalDto.setRetMsg("操作成功");

        return generalDto;
    }

    /*
    * 最近浏览数据放入redis中，
    * 要求：数据去重，限制存放最大个数，过期时间（未实现）
    */
    @Override
    public GeneralDto enterProject(String projectId) throws ParseException {
        GeneralDto<ProjectInfo<CusServiceInfo>> generalDto = new GeneralDto<>();
        String userId = redisTemplate.opsForValue().get("user");
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        String key="recent_browse_lis:"+userId;
        zSet.add(key,projectId,System.currentTimeMillis());
        Long size = zSet.size(key);
        Set<ZSetOperations.TypedTuple<String>> set = zSet.reverseRangeByScoreWithScores(key, 0, MAX_NUMBER - 1);
        if(size>MAX_NUMBER){
            zSet.add(key,set);
        }
        ArrayList<String> list = new ArrayList<>();
        list.add(projectId);
        List<ProjectInfo<CusServiceInfo>> project = projectDao.projectById(list);
        generalDto.setItems( this.turnKey(project));
        generalDto.setRetCode("000000");
        generalDto.setRetMsg("操作成功");
        return generalDto;
    }

    @Override
    public GeneralDto projectList() throws ParseException {
        GeneralDto<ProjectInfo<CusServiceInfo>> generalDto = new GeneralDto<>();
        String userId = redisTemplate.opsForValue().get("user");
        List<ProjectInfo> infoList = projectDao.getIdAneTeam();
        List<String> projectIds = infoList.stream().filter(projectInfo -> projectInfo.getTeam().contains(userId)).map(ProjectInfo::getId).collect(Collectors.toList());
        if(projectIds.size()>0){
            List<ProjectInfo<CusServiceInfo>> projectInfoList = this.turnKey(projectDao.projectById(projectIds));
            for (ProjectInfo<CusServiceInfo>temp:projectInfoList){
                temp.setNumber(temp.getTeam().split(",").length);
            }
            log.info("projectList:{}",projectInfoList);
            generalDto.setItems(projectInfoList);
        }
        generalDto.setRetCode("000000");
        generalDto.setRetMsg("操作成功");
        return generalDto;
    }

    @Override
    public GeneralDto itemListBackLog(String browseId,Integer pageSize,Integer pageNo) {
        GeneralDto<ItemInfo<CusServiceInfo>> generalDto = new GeneralDto<>();
        ArrayList<ItemInfo<CusServiceInfo>> itemInfos = projectDao.itemListBackLog(browseId);
        if(!itemInfos.isEmpty()){
            List<String> ids = itemInfos.stream().filter(cusService -> {
                return Strings.isNotEmpty(cusService.getPrincipal_id());
            }).map(ItemInfo::getPrincipal_id).collect(Collectors.toList());
            log.info("itemListBackLog: ids:{}",ids);
            if(!ids.isEmpty()){
                List<CusServiceInfo> userInfo = userInfoDao1.getUserNameById(ids);
                log.info("itemListBackLog userInfo:{}",userInfo);
                for(ItemInfo<CusServiceInfo> var1:itemInfos){
                    for (CusServiceInfo var2:userInfo){
                        if (var2.getCusId().equals(var1.getPrincipal_id())){
                            var1.setPrincipal(var2);
                            var1.setStartTime(new SimpleDateFormat("yyyy年MM月dd日").format(new Date(var1.getStart_time())));
                            var1.setEndTime(new SimpleDateFormat("yyyy年MM月dd日").format(new Date(var1.getEnd_time())));
                            List<String> participants = Arrays.asList(var1.getParticipant().split(","));
                            var1.setParticipantList(userInfoDao1.getUserNameById(participants));
                        }
                    }
                }
            }
        }
        generalDto.setItems(ParemeterUstil.getPaging(pageNo,pageSize,itemInfos));
        int totalCount = itemInfos.size();
        generalDto.setTotalCount(totalCount);
        if (0 == totalCount) {
            generalDto.setPageCount(0);
        } else {
            if (0 == (totalCount % pageSize)) {
                generalDto.setPageCount((int) (totalCount / pageSize));
            } else {
                generalDto.setPageCount((int) (totalCount / pageSize) + 1);
            }
        }
        generalDto.setPageSize(pageSize);
        generalDto.setPageNo(pageNo);
        generalDto.setRetCode("000000");
        generalDto.setRetMsg("操作成功");
        return generalDto;
    }

    @Override
    public GeneralDto createItem(Integer type) {
        GeneralDto<ItemDictionaryInfo> generalDto = new GeneralDto<>();
        ItemDictionaryInfo item = projectDao.getItemByType(type);
        generalDto.setItem(item);
        generalDto.setRetCode("000000");
        generalDto.setRetMsg("操作成功");
        return generalDto;
    }

    public List<ProjectInfo<CusServiceInfo>> turnKey(List<ProjectInfo<CusServiceInfo>> project) throws ParseException {
        ArrayList<ProjectInfo<CusServiceInfo>> projectList= (ArrayList<ProjectInfo<CusServiceInfo>>) project;
        if(!projectList.isEmpty()){
            ArrayList<String> ids = new ArrayList<>();
            for(ProjectInfo temp:projectList){
                ids.add(temp.getCreated_by());
            }
            log.info(JSONObject.toJSONString(ids));
            if(!ids.isEmpty()){
                List<CusServiceInfo> userNameById = userInfoDao1.getUserNameById(ids);
                log.info(JSONObject.toJSONString(userNameById));
                for(ProjectInfo temp:projectList){
                    for (CusServiceInfo var1:userNameById){
                        if(temp.getCreated_by().equals(var1.getCusId())){
                            temp.setMember(var1);
                            temp.setTime(new SimpleDateFormat("yyyy年MM月dd日").format(new Date(temp.getCreated_at())));
                        }
                    }
                }
            }
        }
        log.info(JSONObject.toJSONString(projectList));
        return projectList;
    }
}
