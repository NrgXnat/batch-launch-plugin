// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.workflows.services;

import org.nrg.containers.services.impl.ContainerServiceImpl;
import org.nrg.xnatx.plugins.batch.workflows.model.Workflow;
import org.nrg.xnatx.plugins.batch.workflows.model.WorkflowPaginatedRequest;
import org.nrg.xnatx.plugins.batch.workflows.repository.WorkflowRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xdat.model.WrkXnatexecutionenvironmentParameterI;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.WrkXnatexecutionenvironment;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.security.UserI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<Workflow> getWorkflows(String id, String dataType, UserI user, WorkflowPaginatedRequest request) throws Exception {
        return workflowRepository.getWorkflows(id, dataType, user, request);
    }

    public Workflow getWorkflowModelFromWorkflowI(PersistentWorkflowI wrk, UserI user) {
        return workflowRepository.getWorkflow(wrk, user);
    }

    public String getContainerId(PersistentWorkflowI wrk) {
        return wrk.getComments();
    }

    public WorkflowType getWorkflowType(PersistentWorkflowI wrk) {
        String justification = wrk.getJustification();
        if (justification != null && justification.equals(ContainerServiceImpl.containerLaunchJustification)
                && StringUtils.isNotEmpty(getContainerId(wrk))) {
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