package com.mkz.media.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.media.domain.dto.MediaDTO;
import com.mkz.media.domain.dto.MediaUploadResultDTO;
import com.mkz.media.domain.po.Media;
import com.mkz.media.domain.query.MediaQuery;
import com.mkz.media.domain.vo.MediaVO;
import com.mkz.media.domain.vo.VideoPlayVO;

/**
 * <p>
 * 媒资表，主要是视频文件 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
public interface IMediaService extends IService<Media> {

    String getUploadSignature();

    VideoPlayVO getPlaySignatureBySectionId(Long fileId);

    MediaDTO save(MediaUploadResultDTO mediaResult);

    void updateMediaProcedureResult(Media media);

    void deleteMedia(String fileId);

    /**
     * 按媒资id删除：先删云端文件，再删本地记录
     */
    void deleteMediaById(Long id);

    VideoPlayVO getPlaySignatureByMediaId(Long mediaId);

    PageDTO<MediaVO> queryMediaPage(MediaQuery query);
}
