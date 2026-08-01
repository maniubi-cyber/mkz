package com.mkz.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.learning.domain.po.PointsMallItems;
import com.mkz.learning.domain.query.PointsItemsPageQuery;
import com.mkz.learning.mapper.PointsMallItemsMapper;
import com.mkz.learning.service.IPointsMallItemsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointsMallItemsServiceImpl extends ServiceImpl<PointsMallItemsMapper, PointsMallItems> implements IPointsMallItemsService {

    @Override
    public PageDTO<PointsMallItems> queryPointsItemsByPage(PointsItemsPageQuery query) {
        LambdaQueryWrapper<PointsMallItems> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(PointsMallItems::getStatus,query.getStatus());
        }
        if (query.getMinPoints() != null) {
            wrapper.ge(PointsMallItems::getPointsRequired, query.getMinPoints() );
        }
        if (query.getMaxPoints() != null) {
            wrapper.le(PointsMallItems::getPointsRequired, query.getMaxPoints());
        }
        Page<PointsMallItems> page = lambdaQuery()
                .orderByDesc(PointsMallItems::getCreateTime)
                .page(query.toMpPage());
        return PageDTO.of(page);
    }
}