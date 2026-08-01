package com.mkz.learning.service.impl;

import com.mkz.learning.domain.po.NoteUser;
import com.mkz.learning.mapper.NoteUserMapper;
import com.mkz.learning.service.INoteUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
public class NoteUserServiceImpl extends ServiceImpl<NoteUserMapper, NoteUser> implements INoteUserService {

}
