var sessionPipelineWorkFlowStatus = {};


function findLabel(key) {
    return key.indexOf('identifier') > 0;
}

$(document).ready(function () {
    if (window.performance) {
        console.info("window.performance works fine on this browser");
    }

    xmodal.loading.open({title: 'Loading information...'});

    var xml = document.getElementById("xss").value;
    var identifierKey = "session_id";
    var subjectIdentifierKey = "xnat_subjectdata_subjectid";
    var subjectLabelKey = "";
    var sessionLabelKey = "";

    XNAT.xhr.post({
        url: XNAT.url.restUrl('REST/search?format=json&XNAT_CSRF=' + window.csrfToken),
        data: xml,
        success: function (responseData) {
            dataType = responseData.ResultSet.rootElementName;
            var opts = responseData.ResultSet.Columns;
            $.each(opts, function (i, d) {
                var header, label;
                header = label = d.header;
                if (header) {
                    var showColumn = true;
                    if (header == "Scans" || header == "Age" || header == "Date" || header == "Scanner" || header == "Type" || header == "M/F") {
                        showColumn = false;
                    }
                    if (d.key.startsWith('xnat_subjectdata_sub_project_identifier') > 0 || d.key == "xnat_subjectdata_subjectid" || d.key == "xnat_subjectdata_subject_label") {
                        subjectLabelKey = d.key;
                    } else if (d.key.indexOf('_project_identifier_') > 0 || d.key == "session_id") {
                        sessionLabelKey = d.key;
                    } else if (d.key == "label") {
                        sessionLabelKey = d.key;
                    }
                    if (showColumn) {
                        // Rename "MR ID" to Session
                        if (header === "MR ID") {
                            label = "Session";
                        }
                        keyAndHeaderMap[header] = d.key;
                        columnsToShow[header] = {show: 0, label: label};
                    }
                }
            });

            columnsToShow['Project']['show'] = 1;
            columnsToShow['MR ID']['show'] = 1;
            columnsToShow['Subject']['show'] = 1;
            var rows = responseData.ResultSet.Result;

            // Dont add columns for workflows which have not been executed for
            // the project at all
            for (var wrk_col in columnsToShow) {
                if (columnsToShow[wrk_col]['show'] === 1) {
                    continue;
                }
                $.each(rows, function (i, d) {
                    if (columnsToShow[wrk_col]['show'] === 0) {
                        if (keyAndHeaderMap.hasOwnProperty(wrk_col)) {
                            var key = keyAndHeaderMap[wrk_col];
                            if (wrk_col != 'Project' && key != sessionLabelKey && key != subjectLabelKey) {
                                var workFlowStatusIndx = d[key].indexOf("#");
                                var workFlowStatus = d[key].substring(0, workFlowStatusIndx);
                                if (workFlowStatus || key.startsWith("res_file")) {
                                    columnsToShow[wrk_col]['show'] = 1;
                                    return false;
                                }
                            }
                        }
                    }
                });
            }

            // Add thead
            var $filterInput, label;
            for (var header_col in columnsToShow) {
                if (columnsToShow[header_col]['show'] === 1) {
                    label = columnsToShow[header_col]['label'];
                    $('tr#xnat-table-header-row1').append('<th class="left sort"  style="width:120px;word-wrap:break-word;">' + label + '</th>');
                    //Filter for each column
                    $filterInput = $.spawn('input#filter-' + label + '.filter-data', {
                        type: 'text',
                        title: label + ':filter',
                        placeholder: 'Search...'
                    });
                    $filterInput.on('change', function(){
                        $('#xnat-table tr').show();
                        $('input.filter-data').each(function(){
                            var val = $(this).val();
                            if (val) {
                                var colClass = this.id.replace('filter-','');
                                $("#xnat-table td." + colClass + ":not(:contains('" + val + "'))").parent().hide();
                            }
                        });
                    });
                    $('tr#xnat-table-header-row2').append($("<td></td>").append($filterInput));
                }
            }

            // AddDataTableRows:
            var workFlowStatusFirstLetterCapital;
            $.each(rows, function (i, d) {
                var session_id = d.session_id;
                var subject_id = d.xnat_subjectdata_subjectid;
                var session_project = d.project;
                var sessionLabel = d[sessionLabelKey];
                var sessionWorkFlowStatus = {};
                if (!projectId) {
                    projectId = d.project;
                }

                var single_select_checkbox_id = "select-" + session_id;
                var id_json = '{&quot;accession-id&quot;:&quot;' + session_id + '&quot;,&quot;label&quot;:&quot;' + sessionLabel + '&quot;,&quot;project&quot;:&quot;' + session_project + '&quot;,&quot;xsiType&quot;:&quot;' + dataType + '&quot;}';
                var session_url = 'app/action/DisplayItemAction/search_element/' + dataType + '/search_field/' + dataType + '.ID/search_value/' + session_id + '/popup/$popup';
                var subject_url = 'app/action/DisplayItemAction/search_element/xnat:subjectData/search_field/xnat:subjectData.ID/search_value/' + subject_id + '/popup/$popup';

                var rowDataWithColumns = '<tr valign="top" id="session-' + session_id + '">';
                rowDataWithColumns += '<td class="session-actions-controls session-selector center" style="width: 45px;">';
                rowDataWithColumns += '<input type="checkbox" class="selectable-select-one" id="' + single_select_checkbox_id + '" value="' + id_json + '"/>';
                rowDataWithColumns += '</td>';

                for (var hdr in columnsToShow) {
                    if (keyAndHeaderMap.hasOwnProperty(hdr)) {
                        var key = keyAndHeaderMap[hdr];
                        label = columnsToShow[hdr]['label'];
                        if (hdr == 'Project') {
                            rowDataWithColumns += '<td class="' + label + ' session-' + session_id + '-' + d[key] + '" style="width:120px;"><span  title="' + label + '">' + d[key] + '</span></td>';
                        } else if (key == sessionLabelKey) {
                            var url = XNAT.url.rootUrl(session_url);
                            rowDataWithColumns += '<td class="' + label + '"  style="width:120px;"><a href="' + url + '"  target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';

                        } else if (key == subjectLabelKey) {
                            var url = XNAT.url.rootUrl(subject_url);
                            rowDataWithColumns += '<td class="' + label + '"  style="width:120px;"><a href="' + url + '" target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';
                        } else if (key.startsWith("res_file")) {
                            rowDataWithColumns += '<td class="' + label + '"  style="width:120px;">' + d[key] + '</td>';
                        } else {
                            if (columnsToShow[hdr]['show'] === 1) {
                                // d.key contains status#workflow id
                                var workFlowStatusIndx = d[key].indexOf("#");
                                var workFlowStatus = d[key].substring(0, workFlowStatusIndx);
                                var workFlowId = d[key].substring(workFlowStatusIndx + 1);
                                var fontColor = "";
                                if (workFlowStatus.startsWith("Killed") || workFlowStatus.startsWith("Failed") || workFlowStatus == "Failed" || workFlowStatus.startsWith("Error") || workFlowStatus == "Error") {
                                    fontColor = 'color="red"';
                                } else if (workFlowStatus == "Queued" || workFlowStatus == "Created") {
                                    fontColor = 'color="orange"';
                                } else if (workFlowStatus == "Complete") {
                                    fontColor = 'color="green"';
                                }
                                rowDataWithColumns += '<td class="' + label + '" style="width:120px;">';
                                if (workFlowStatus) {
                                    workFlowStatusFirstLetterCapital = workFlowStatus.charAt(0).toUpperCase() + workFlowStatus.slice(1);
                                    rowDataWithColumns += '<span  title="' + label + '"><font ' + fontColor + '>' + workFlowStatusFirstLetterCapital + '</font></span>';
                                    rowDataWithColumns += ' 	 <span class="inline-actions">';
                                    rowDataWithColumns += '          <i class="fa fa-eye"  title="View Details" onclick="viewContainerDetails(' + workFlowId + ')"></i>';
                                    // rowDataWithColumns += '          <i class="fa fa-eye"  title="View Std Log" onclick="viewWorkflowFile('+workFlowId+',\'stdout\')"></i>';
                                    // rowDataWithColumns += '          <i class="fa fa-eye"  title="View Std Error" onclick="viewWorkflowFile('+workFlowId+',\'stderr\')"></i>';
                                    rowDataWithColumns += '          <i class="fa fa-trash" title="Terminate Process" onclick="killProcess(' + workFlowId + ')"></i>';
                                    rowDataWithColumns += '     </span>';
                                    sessionWorkFlowStatus[hdr] = workFlowStatus;
                                } else {
                                    fontColor = 'color="gray"';
                                    rowDataWithColumns += '<span><font ' + fontColor + '>Ready</font></span>';
                                    sessionWorkFlowStatus[hdr] = '--';
                                }
                                //console.log('Added ' + sessionLabel + ' hdr' + hdr + ' Workflow ' + workFlowStatus);
                                rowDataWithColumns += '</td>';
                            }
                        }
                    }
                }
                sessionPipelineWorkFlowStatus[sessionLabel] = sessionWorkFlowStatus;
                rowDataWithColumns += '</tr>';
                $('tbody#xnat-table-datarows-tbody').append(rowDataWithColumns);

            });
            xmodal.loading.close();
            setTableWidth('div-xnat-table', 'data-table-titlerow');
            setTableHeight('div-xnat-table');
            $('#searchRootElement').val(dataType);
            $('#searchProjectId').val(projectId);
            populateBreadCrumbs();
            // Now get the actions associated with the datatype
            renderActionOptions();
            var displayContainer = $("#active-processes");
            setTableWidth('div-xnat-table', 'active-processes');
            setTableHeight('active-processes');

            XNAT.plugin.batchLaunch.historyTable.init(projectId, displayContainer);
        },
        error: function (o) {
            XNAT.dialog.open({
                title: 'Error!',
                content: 'Could not GET the search results encounetered ' + o.responseText,
                width: 400,
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });

        }
    });
});

function setItemWidth(div_id, width) {
    var d = YUIDOM.get(div_id);
    console.log("Resetting " + div_id + " Width: " + width);
    if (d != null) {
        var d2 = $(d);
        d2.css('width', width);
        console.log("Done");
    }
}

function setTableWidth(div_id, div_title_id) {
    var tableC = YUIDOM.get(div_id);// have to use YUI here because jquery fails
                                    // to find it. I think because it contains a
                                    // period. But, the YUI element can be
                                    // passed into jquery.
    if (tableC != null) {
        var tableWidth = $(YUIDOM.getFirstChild(tableC)).width();// need the
        // width of
        // the table
        // within
        // the
        // container
        // div.
        var tabsWidth = $('#processing_tabs').width();
        console.log("Table Width: " + tableWidth + " Tab Width:" + tabsWidth);
        if ((tableWidth + 18) < tabsWidth) {// if table + scrollbar doesn't take
            // up the whole tab
            setItemWidth(div_id, (tableWidth + 18));// set table overflow
            // container to barely
            // contain table, so the
            // scrollbar isn't way off
            // to the right.
            setItemWidth(div_title_id, (tableWidth + 18));
        }
    }
}

function setTableHeight(div_id) {
    var container = document.getElementById(div_id);
    container = $(container);
    if ($(container) != null) {
        var windowHeight = $(window).innerHeight();
        var tableHeight = $('table.xnat-table').height();
        var tablePosition = $(container).offset();

        /*
		 * max height is total screen height minus space for table header &
		 * chrome
		 */
        var maxTableHeight = (tableHeight < windowHeight) ? tableHeight + 60 : windowHeight - 60;
        var minTableHeight = 300;

        /*
		 * available height is visible screen height below the starting Y point
		 * of the table, plus room for table header & chrome
		 */
        var availableTableHeight = windowHeight - 30;
        availableTableHeight = (availableTableHeight > maxTableHeight) ? maxTableHeight : availableTableHeight;
        availableTableHeight = (availableTableHeight < minTableHeight) ? minTableHeight : availableTableHeight;

        /* set dimensions of table containers */
        $(container).css('height', availableTableHeight);
    }
}


function reload() {
    window.location.reload();
}

function displaySessionDetails(sessionLabel, sessionUrl) {
    if (!sessionLabel) return false;
    if (!sessionUrl) return false;
    XNAT.ui.dialog.iframe(sessionUrl, 'Session: ' + sessionLabel, 580, 600);
};

function displaySubjectDetails(subjectLabel, subjectUrl) {
    if (!subjectLabel) return false;
    if (!subjectUrl) return false;
    XNAT.ui.dialog.iframe(subjectUrl, 'Subject: ' + subjectLabel, 580, 600);
};

function viewWorkflowFile(workFlowId, fileType) {
    // FileType is stdout or stderr
    var logFileUrl = XNAT.url.rootUrl('xapi/workflows/' + workFlowId + '/logs/' + fileType);
    XNAT.ui.dialog.iframe(logFileUrl, 'File: ' + fileType, 580, 600);
};

function viewContainerDetails(workFlowId) {
    var containerDetailsUrl = XNAT.url.rootUrl('xapi/workflows/' + workFlowId + '/container');
    XNAT.xhr.getText({
        url: containerDetailsUrl,
        success: function (responseData) {
            XNAT.plugin.batchLaunch.historyTable.viewHistory(responseData)
        },
        error: function (o) {
            XNAT.dialog.open({
                title: 'Error!',
                content: 'Could not get container assigned for this workflow ' + workFlowId + ' encounetered ' + o,
                width: 400,
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });
        }
    });

};


function populateBreadCrumbs() {
    var projectId = $('#searchProjectId').val();

    // wrap it up to keep things
    // out of global scope
    (function () {

        var crumbs = [];
        crumbs.push({
            id: projectId,
            type: 'PROJECT',
            link: '/app/action/DisplayItemAction/search_element/xnat%3AprojectData/search_field/xnat%3AprojectData.ID/search_value/' + projectId,
            label: projectId
        });
        XNAT.ui.breadcrumbs.render('#breadcrumbs', crumbs);
    })();


}

function renderActionOptions() {
    $('#actionsDropdown')
        .find('option')
        .remove()
        .end()
        .append('<option value="Select" selected="true">Select Container to Launch</option>');
    var data_type_val = $('#searchRootElement').val();
    var projectId = $('#searchProjectId').val();
    xmodal.loading.open({title: 'Loading configured containers and pipelines...'});
    XNAT.xhr.getJSON({
        url: XNAT.url.rootUrl('/xapi/commands/available?project=' + projectId + '&xsiType=' + data_type_val),
        success: function (responseData) {
            responseData.forEach(function (availableCommand) {
                if (availableCommand.enabled) {
                    $('#actionsDropdown').append('<option value="{&quot;root-element-name&quot;:&quot;' + availableCommand['root-element-name'] + '&quot;,&quot;wrapper-id&quot;:&quot;' + availableCommand['wrapper-id'] + '&quot;,&quot;command-id&quot;:&quot;' + availableCommand['command-id'] + '&quot;,&quot;wrapper-name&quot;:&quot;' + availableCommand['wrapper-name'] + '&quot;}">' + availableCommand['wrapper-name'] + '</option>');
                }
            });
            $('#actionsDropdown').removeClass('disabled');
            $('#actionsDropdown').prop("disabled", false);
        },
        error: function (o) {
            XNAT.dialog.open({
                title: 'Error!',
                content: 'Could not get actions associated with ' + data_type_val + ' encounetered ' + o,
                width: 400,
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });
        }
    });
    //TODO: Load configured pipelines
    xmodal.loading.close();
}

$('#actionsDropdown').change(function () {
    var selectedStr = $(this).find(":selected").val();
    if (selectedStr != "Select") {
        var action = selectedStr;
        $(this).parents('.data-table-container').find('button').find('.data-table-action').removeClass('disabled');
    } else {
        $(this).parents('.data-table-container').find('button').find('.data-table-action').addClass('disabled');
    }
});


function launchContainer() {
    var commandDetails = $('#actionsDropdown').find(":selected").val();
    console.log("CommandDetails: " + commandDetails);
    if (commandDetails == "Select") {
        XNAT.dialog.open({
            title: 'Please select a container to launch!',
            content: 'Please select a container to launch first',
            width: 400,
            buttons: [
                {
                    label: 'OK',
                    isDefault: true,
                    close: true
                }
            ]
        });
        $(this).addClass('disabled');
        return false;
    } else {
        var targets = [];
        var targetLabels = [];
        $('input.selectable-select-one:checkbox').each(function () {
            if ($(this).is(':checked')) {
                // Get the JSON
                var jsonData = JSON.parse($(this).val());
                targets.push(jsonData['accession-id']);
                targetLabels.push(jsonData['label']);
            }
        });
        //Are there any sessions in the selected list which are in any state other than Failed or Complete?
        //If this change the selected sessions
        var projectId = $('#searchProjectId').val();
        var commandDetailsJsonObj = JSON.parse(commandDetails);
        var rootElementName = commandDetailsJsonObj['root-element-name'];
        var wrapperId = commandDetailsJsonObj['wrapper-id'];
        var commandId = commandDetailsJsonObj['command-id'];
        var pipelineName = commandDetailsJsonObj['wrapper-name'];
        //Are there any sessions in the selected list which are in any state other than Failed or Complete?
        //If this change the selected sessions
        var sessionsBeingProcessed = checkSelectedSessions(targetLabels, pipelineName);
        if (sessionsBeingProcessed && sessionsBeingProcessed.length > 0) {
            var sessionList = "";
            sessionsBeingProcessed.forEach(function (sessionId) {
                sessionList += "<p>" + sessionId + "</p>";
            });
            XNAT.dialog.open({
                title: 'Error!',
                content: 'The following session(s) can not be processed currently ' + sessionList + ' please exclude the above session(s) and relaunch.',
                width: 400,
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });
        } else {
            XNAT.plugin.containerService.launcher.bulkLaunchDialog(projectId, commandId, wrapperId, rootElementName, targets, targetLabels);
        }
    }
}

function checkSelectedSessions(targets, pipelineName) {
    var failedWorkflowStatus = "Failed";
    var completeWorkflowStatus = "Complete";
    var sessionsBeingProcessed = [];
    targets.forEach(function (sessionId) {
        var wrkFlowStatus = sessionPipelineWorkFlowStatus[sessionId];
        if (wrkFlowStatus) {
            var status = wrkFlowStatus[pipelineName];
            if (status != failedWorkflowStatus && status != completeWorkflowStatus) {
                sessionsBeingProcessed.push(sessionId);
            }
        }
    });
    return sessionsBeingProcessed;
}