package com.mkz.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.data.mapper.DuvMapper;
import com.mkz.data.model.po.Duv;
import com.mkz.data.service.IDuvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Description：日用户访问数接口实现类
 */
@Slf4j
@Service
public class DuvServiceImpl extends ServiceImpl<DuvMapper, Duv> implements IDuvService {

}
