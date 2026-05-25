package com.lightbot.workflow;

/**
 * Agent.config 中工作流相关键名
 */
public final class WorkflowConfigKeys {

    private WorkflowConfigKeys() {
    }

    public static final String WORKFLOW_LEGACY = "workflow";
    public static final String WORKFLOW_DRAFT = "workflowDraft";
    public static final String WORKFLOW_PUBLISHED = "workflowPublished";
    public static final String WORKFLOW_VERSIONS = "workflowVersions";
    public static final String PUBLISHED_VERSION = "publishedVersion";
    public static final String WORKFLOW_STATUS = "workflowStatus";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_PUBLISHED_EDITING = "published_editing";
}
