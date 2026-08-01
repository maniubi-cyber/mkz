package com.mkz.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.data.mapper.DnuMapper;
import com.mkz.data.model.po.Dnu;
import com.mkz.data.service.IDnuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Description：用户日活跃数服务实现类
 */
@Slf4j
@Service
public class DnuServiceImpl extends ServiceImpl<DnuMapper, Dnu> implements IDnuService {

}
