package com.mkz.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.exam.domain.dto.ExamCommitDTO;
import com.mkz.exam.domain.dto.ExamFormDTO;
import com.mkz.exam.domain.po.ExamRecord;
import com.mkz.exam.domain.query.ExamPageQuery;
import com.mkz.exam.domain.vo.ExamQuestionVO;
import com.mkz.exam.domain.vo.ExamRecordAdminVO;
import com.mkz.exam.domain.vo.ExamRecordDetailVO;
import com.mkz.exam.domain.vo.ExamRecordVO;

import java.util.List;

/**
 * <p>
 * 考试记录表 服务类
 * </p>
 *
 * @author 虎哥
 */
public interface IExamRecordService extends IService<ExamRecord> {

    PageDTO<ExamRecordVO> queryMyExamRecordsPage(PageQuery query);

    ExamQuestionVO saveExamRecord(ExamFormDTO examFormDTO);

    void saveExamRecordDetails(ExamCommitDTO examCommitDTO);

    List<ExamRecordDetailVO> queryDetailsByExamId(Long examId);

    PageDTO<ExamRecordAdminVO> queryAdminExamRecordsPage(ExamPageQuery query);

    List<ExamRecordDetailVO> queryAdminDetailsByExamId(Long examId);

}
