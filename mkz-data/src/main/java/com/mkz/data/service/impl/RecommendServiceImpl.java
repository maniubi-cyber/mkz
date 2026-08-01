package com.mkz.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.api.client.course.CourseClient;
import com.mkz.api.client.user.UserClient;
import com.mkz.api.dto.course.CourseSimpleInfoDTO;
import com.mkz.api.dto.user.UserDTO;
import com.mkz.common.utils.UserContext;
import com.mkz.data.algorithm.FeatureBasedRecommendationAlgorithm;
import com.mkz.data.mapper.DnuMapper;
import com.mkz.data.model.po.CourseProfile;
import com.mkz.data.model.po.Dnu;
import com.mkz.data.model.po.UserProfile;
import com.mkz.data.service.ICourseProfileService;
import com.mkz.data.service.IDnuService;
import com.mkz.data.service.IRecommendService;
import com.mkz.data.service.IUserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description：推荐算法服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl  implements IRecommendService {

    private final IUserProfileService userProfileService;
    private final ICourseProfileService courseProfileService;
    private final UserClient userClient;
    private final CourseClient courseClient;

    @Override
    public List<Long> featureRecommend() {
        Long userId = UserContext.getUser();
        UserProfile userProfile = userProfileService.lambdaQuery().eq(UserProfile::getUserId, userId).one();
        List<CourseProfile> courseProfiles = courseProfileService.lambdaQuery().list();
        List<CourseSimpleInfoDTO> allCourses = courseClient.getSimpleInfoList(courseProfiles.stream().map(CourseProfile::getCourseId).collect(Collectors.toList()));
        UserDTO dto = userClient.queryUserById(userId);
        FeatureBasedRecommendationAlgorithm featureBasedRecommendationAlgorithm = new FeatureBasedRecommendationAlgorithm();
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOS = featureBasedRecommendationAlgorithm.recommendCourses(
                dto,
                userProfile,
                allCourses,
                courseProfiles,
                3);
        return courseSimpleInfoDTOS.stream().map(CourseSimpleInfoDTO::getId).collect(Collectors.toList());
    }
}
