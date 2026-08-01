package com.mkz.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.data.mapper.CourseProfileMapper;
import com.mkz.data.mapper.UserProfileMapper;
import com.mkz.data.model.po.CourseProfile;
import com.mkz.data.model.po.UserProfile;
import com.mkz.data.service.ICourseProfileService;
import com.mkz.data.service.IUserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Description：用户画像服务实现类
 */
@Slf4j
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements IUserProfileService {

}
