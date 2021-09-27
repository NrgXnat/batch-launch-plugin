// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.services;

import org.nrg.xnatx.plugins.batch.model.Workflow;
import org.nrg.xnatx.plugins.batch.model.WorkflowFilter;
import org.nrg.xnatx.plugins.batch.repositories.WorkflowRepository;
import org.nrg.xnatx.plugins.batch.xapi.PageRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xdat.model.WrkXnatexecutionenvironmentParameterI;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.WrkXnatexecutionenvironment;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.security.UserI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WorkflowService {
    public enum WorkflowType {
        CONTAINER,
        PIPELINE,
        OTHER
    }

    @Autowired
    public WorkflowService(final WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    public List<Workflow> getWorkflows(String id, String dataType, UserI user,
                                       String sortColumn, String sortDir, @Nullable Integer page, @Nullable Integer size,
                                       @Nullable Map<String, WorkflowFilter> filtersMap, Boolean sortable, Integer days) throws Exception {
        return workflowRepository.getWorkflows(id, dataType, user,
                new PageRequest(workflowRepository, sortColumn, sortDir, filtersMap, page, size, sortable, days));
    }

    public Workflow getWorkflowModelFromWorkflowI(PersistentWorkflowI wrk, UserI user) {
        return workflowRepository.getWorkflow(wrk, user);
    }

    public String getContainerId(PersistentWorkflowI wrk) {
        return wrk.getComments();
    }

    public WorkflowType getWorkflowType(PersistentWorkflowI wrk) {
        String justification = wrk.getJustification();
        if (justification != null && justification.equals("Container launch") && StringUtils.isNotEmpty(getContainerId(wrk))) {
            return WorkflowType.CONTAINER;
        } else if (wrk.getPipelineName().endsWith(".xml")) {
            return WorkflowType.PIPELINE;
        } else {
            return WorkflowType.OTHER;
        }
    }

    public String getBuildDir(PersistentWorkflowI wrk) {
        List<WrkXnatexecutionenvironmentParameterI> params = ((WrkXnatexecutionenvironment) ((WrkWorkflowdata) wrk)
                .getExecutionenvironment()).getParameters_parameter();
        for (WrkXnatexecutionenvironmentParameterI param : params) {
            if (param.getName().equals("builddir")) {
                return param.getParameter();
            }
        }
        return null;
    }

    private final WorkflowRepository workflowRepository;
}