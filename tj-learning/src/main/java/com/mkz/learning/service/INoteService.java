package com.mkz.learning.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.learning.domain.dto.NoteFormDTO;
import com.mkz.learning.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.learning.domain.query.NoteAdminPageQuery;
import com.mkz.learning.domain.query.NotePageQuery;
import com.mkz.learning.domain.vo.NoteAdminDetailVO;
import com.mkz.learning.domain.vo.NoteAdminVO;
import com.mkz.learning.domain.vo.NoteVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 */
public interface INoteService extends IService<Note> {

    void saveNote(NoteFormDTO noteDTO);

    void gatherNote(Long id);

    void removeGatherNote(Long id);

    void updateNote(NoteFormDTO noteDTO);

    PageDTO<NoteVO> queryNotePage(NotePageQuery query);

    PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query);

    NoteAdminDetailVO queryNoteDetailForAdmin(Long id);

    void hiddenNote(Long id, boolean hidden);

    void removeMyNote(Long id);
}
