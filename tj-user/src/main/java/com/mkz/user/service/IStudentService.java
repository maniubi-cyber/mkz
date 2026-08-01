package com.mkz.user.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.user.domain.dto.StudentFormDTO;
import com.mkz.user.domain.dto.StudentUpdateDTO;
import com.mkz.user.domain.dto.StudentUpdatePasswordDTO;
import com.mkz.user.domain.query.UserPageQuery;
import com.mkz.user.domain.vo.StudentPageVO;

/**
 * <p>
 * 学员详情表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
public interface IStudentService {

    void saveStudent(StudentFormDTO studentFormDTO);

    void updateMyPassword(StudentFormDTO studentFormDTO);

    PageDTO<StudentPageVO> queryStudentPage(UserPageQuery pageQuery);

    void updateStudent(StudentUpdateDTO studentUpdateDTO);

    void updateBindPhone(String cellPhone, String code);

    void updatePassword(StudentUpdatePasswordDTO dto);
}
