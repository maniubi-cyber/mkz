package com.mkz.learning.service.impl;

import com.mkz.common.utils.DateUtils;
import com.mkz.learning.domain.po.PointsBoardSeason;
import com.mkz.learning.domain.vo.PointsBoardSeasonVO;
import com.mkz.learning.mapper.PointsBoardSeasonMapper;
import com.mkz.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.mkz.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author fsq
 * @since 2023-10-26
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public void createPointsBoardLatestTable(Integer id) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX + id);
    }

}

