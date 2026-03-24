package com.ai_kids_care.v1.vo;

import com.ai_kids_care.v1.vo.CommonCodeVO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AnnouncementMetaVO {
    private boolean canWrite;
    private List<CommonCodeVO> statusOptions;
}
