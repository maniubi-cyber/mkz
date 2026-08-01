package com.mkz.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkz.data.model.po.CourseProfile;
import com.mkz.data.model.po.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Description：课程画像Mapper接口
 */
@Mapper
public interface CourseProfileMapper extends BaseMapper<CourseProfile> {

}
