package com.mkz.learning.service;

import com.mkz.learning.domain.dto.ShareDetailDTO;
import com.mkz.learning.domain.dto.ShareLinkDTO;

/**
 * @author fsq
 * @date 2025/6/15 14:39
 */
public interface IShareService {
    ShareLinkDTO generateShareLink(Long userId, Long courseId);

    ShareDetailDTO parseShareLink(String shortCode);
}
