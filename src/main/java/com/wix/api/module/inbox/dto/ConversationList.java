package com.wix.api.module.inbox.dto;

import java.util.List;

import com.wix.api.common.CursorPagingMetadata;
import lombok.Data;

@Data
public class ConversationList {

    private List<Conversation> conversations;
    private CursorPagingMetadata pagingMetadata;
}
