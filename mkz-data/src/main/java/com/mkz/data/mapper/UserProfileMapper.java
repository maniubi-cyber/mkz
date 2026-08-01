package com.mkz.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkz.data.model.po.Dau;
import com.mkz.data.model.po.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Description：用户画像Mapper接口
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

}
