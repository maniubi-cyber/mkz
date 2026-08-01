package com.mkz.learning.service;

import com.mkz.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.learning.domain.vo.PointsBoardSeasonVO;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author fsq
 * @since 2023-10-26
 */

public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {


    void createPointsBoardLatestTable(Integer id);
}
