package com.wix.api.module.inbox.dto;

import java.util.List;

import com.wix.api.common.CursorPagingMetadata;
import lombok.Data;

@Data
public class MessageList {

    private List<Message> messages;
    private CursorPagingMetadata pagingMetadata;
}
