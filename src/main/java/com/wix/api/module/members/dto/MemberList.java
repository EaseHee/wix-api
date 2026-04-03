package com.wix.api.module.members.dto;

import java.util.List;

import com.wix.api.common.PagingMetadata;
import lombok.Data;

@Data
public class MemberList {

    private List<Member> members;
    private PagingMetadata pagingMetadata;
}
