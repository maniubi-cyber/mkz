package com.mkz.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.data.mapper.DauRangeMapper;
import com.mkz.data.model.po.DauRange;
import com.mkz.data.service.IDauRangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
/**
 * @Description：用户日活跃数范围服务实现类
 */
@Slf4j
@Service
public class DauRangeServiceImpl extends ServiceImpl<DauRangeMapper, DauRange> implements IDauRangeService {


}
