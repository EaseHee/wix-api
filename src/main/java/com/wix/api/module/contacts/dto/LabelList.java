package com.wix.api.module.contacts.dto;

import java.util.List;

import com.wix.api.common.PagingMetadata;
import lombok.Data;

@Data
public class LabelList {

    private List<Label> labels;
    private PagingMetadata pagingMetadata;
}
