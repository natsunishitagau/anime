package com.anime.dto;

import java.util.List;

public class FolderReorderRequest {
    private List<Long> folderOrder;

    public List<Long> getFolderOrder() {
        return folderOrder;
    }

    public void setFolderOrder(List<Long> folderOrder) {
        this.folderOrder = folderOrder;
    }
}