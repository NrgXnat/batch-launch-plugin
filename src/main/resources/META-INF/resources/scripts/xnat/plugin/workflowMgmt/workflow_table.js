/*
 */

XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.containerService = getObject(XNAT.plugin.containerService || {});
XNAT.plugin.batchLaunch = getObject(XNAT.plugin.batchLaunch || {});

(function(factory){
    if (typeof define === 'function' && define.amd) {
        define(factory);
    }
    else if (typeof exports === 'object') {
        module.exports = factory();
    }
    else {
        return factory();
    }
}(function(){
    XNAT.plugin.batchLaunch.workflowTable = {};
    XNAT.plugin.batchLaunch.workflowTableParams = getObject(XNAT.plugin.batchLaunch.workflowTableParams ||
        {sortable: true, days: -1});

    const tableId = 'workflows-data-table';
    const tableContainerId = tableId + '-container'; //must exist within a div.tab-container
    const labelMap = {
        externalId: {label: 'Project', show: true, type: 'string'},
        label: {label: 'Label', show: true, type: 'string'},
        status: {label: 'Status', show: true, type: 'string'},
        pipelineName: {label: 'Name', show: true, type: 'string'},
        launchTime: {label: 'Launch time', show: true, type: 'datetime'},
        modTime: {label: 'Last mod', show: false, type: 'datetime'},
        details: {label: 'Details', show: true, type: 'string'},
        percentageComplete: {label: '&percnt;', show: true, type: 'number'},
        stepDescription: {label: 'Progress', show: true, type: 'string'},
        createUser: {label: 'User', show: true, type: 'string'}
    };
    
    const noLinkDataTypes = ['xdat:element_action_type', 'xdat:user', 'xdat:userGroup', 'xdat:element_security',
        'xnat:fieldDefinitionGroup', 'xnat:investigatorData', 'pipe:PipelineRepository', 'arc:ArchiveSpecification'];
    
    function makeTableObject() {
        if (XNAT.plugin.batchLaunch.workflowTableParams.sortable) {
            XNAT.plugin.batchLaunch.sortableCols = 'externalId, label, status, pipelineName, launchTime, modTime, createUser';
            XNAT.plugin.batchLaunch.filterCols = 'externalId, label, status, pipelineName, createUser';
        } else {
            XNAT.plugin.batchLaunch.sortableCols = '';
            XNAT.plugin.batchLaunch.filterCols = '';
        }

        return {
            before: {
                filterCss: {
                    tag: 'style|type=text/css',
                    content:
                        '#' + tableId + ' tbody { height: 300px; } \n' +
                        '#' + tableId + ' td.status { min-width: 90px; } \n' +
                        '#' + tableId + ' td.status .inline-actions { width: 90px; } \n' +
                        '#' + tableId + ' td.details{ max-width: 200px; } \n' +
                        '#' + tableId + ' td.percentageComplete { min-width: 100px; } \n'
                }
            },
            sortable: XNAT.plugin.batchLaunch.sortableCols,
            filter: XNAT.plugin.batchLaunch.filterCols,
            items: {
                // by convention, name 'custom' columns with ALL CAPS
                // 'custom' columns do not correspond directly with
                // a data item
                externalId: {
                    th: {className: 'externalId'},
                    label: labelMap['externalId']['label'],
                    apply: function () {
                        if (this['externalId'] !== 'ADMIN') {
                            return spawn('a', {href: getDisplayUrl('xnat:projectData', this['externalId'])},
                                this['externalId']);
                        } else {
                            return this['externalId'];
                        }
                    }
                },
                label: {
                    th: {className: 'label'},
                    label: labelMap['label']['label'],
                    apply: function () {
                        if (!this['label']) {
                            return this['id'];
                        } else {
                            if (this['dataType'] && !noLinkDataTypes.includes(this['dataType'])) {
                                return spawn('a', {href: getDisplayUrl(this['dataType'], this['id'])}, this['label']);
                            } else {
                                return this['label'];
                            }
                        }
                    }
                },
                status: {
                    th: {className: 'status'},
                    label: labelMap['status']['label'],
                    apply: function () {
                        return [XNAT.plugin.batchLaunch.spawnStatusCell(this['status']),
                            XNAT.plugin.batchLaunch.spawnInlineActions(this)];
                    }
                },
                pipelineName: {
                    th: {className: 'pipelineName'},
                    label: labelMap['pipelineName']['label'],
                    apply: function () {
                        return this['pipelineName'];
                    }
                },
                launchTime: {
                    th: {className: 'launchTime'},
                    label: labelMap['launchTime']['label'],
                    apply: function () {
                        return new Date(this['launchTime']).toLocaleString();
                    }
                },
                modTime: {
                    th: {className: 'modTime'},
                    label: labelMap['modTime']['label'],
                    apply: function () {
                        return new Date(this['modTime']).toLocaleString();
                    }
                },
                details: {
                    th: {className: 'details'},
                    label: labelMap['details']['label'],
                    apply: function () {
                        if (this['details']) {
                            const details = this['details'],
                                pipelineName = this['pipelineName'],
                                launchTime = new Date(this['launchTime']).toLocaleString(),
                                anchorTxt = details.length > 8 ? details.substring(0, 8) + '...' : details;
                            return spawn('a', {
                                onclick: function () {
                                    XNAT.ui.dialog.open({
                                        title: 'Details for ' + pipelineName + ' (launched: ' + launchTime + ')',
                                        content: details,
                                        destroyOnClose: true,
                                        buttons: [{
                                            label: 'OK',
                                            isDefault: true,
                                            close: true
                                        }]
                                    });
                                }
                            }, anchorTxt);
                        } else {
                            return '';
                        }
                    }
                },
                percentageComplete: {
                    th: {className: 'percentageComplete'},
                    label: labelMap['percentageComplete']['label'],
                    apply: function () {
                        return XNAT.plugin.batchLaunch.renderPercentComplete(this['status'], this['percentageComplete']);
                    }
                },
                stepDescription: {
                    th: {className: 'stepDescription'},
                    label: labelMap['stepDescription']['label'],
                    apply: function () {
                        return this['stepDescription'];
                    }
                },
                createUser: {
                    th: {className: 'createUser'},
                    label: labelMap['createUser']['label'],
                    apply: function () {
                        return this['createUser'];
                    }
                }
            }
        }
    }

    XNAT.plugin.batchLaunch.workflowTable = XNAT.ui.ajaxTable.AjaxTable(XNAT.url.restUrl('/xapi/workflows'),
        tableId, tableContainerId, 'History', 'Activity', makeTableObject(), tableSetupFn,
        firstLoadDataCallback, null, tableReloadCallback, labelMap, false);


    function getDisplayUrl(data_type, id) {
        return XNAT.url.rootUrl('/app/action/DisplayItemAction/search_element/' + data_type +
            '/search_field/' + data_type + '.ID/search_value/' + id);
    }

    function tableSetupFn() {
        // What kind of page are we on? What kind of table do we want?
        let id='', type='xdat:user', hide_proj = false, hide_label = false, title='';
        if (XNAT.data && XNAT.data.context) {
            if (XNAT.plugin.batchLaunch.projectId) {
                // processing dashboard
                id = XNAT.plugin.batchLaunch.projectId;
                type = 'xnat:projectData';
                hide_proj = true;
                title = id + ' processing history'
            } else {
                id = XNAT.data.context.ID || id;
                type = XNAT.data.context.xsiType || type;
                hide_proj = hide_label = id !== '';
                title = id !== '' ? 'History' : '';
            }
        }
        if (hide_proj) {
            labelMap['externalId']['show'] = false;
        }
        if (hide_label) {
            labelMap['label']['show'] = false;
        }
        let dataObj = {
            id: id,
            data_type: type,
            sortable: XNAT.plugin.batchLaunch.workflowTableParams.sortable,
            days: XNAT.plugin.batchLaunch.workflowTableParams.days,
            admin_workflows: XNAT.plugin.batchLaunch.workflowTableParams.admin
        };
        return {
            title: title,
            dataObj: dataObj
        }
    }

    function firstLoadDataCallback(data) {
        XNAT.plugin.batchLaunch.addClickActions($('table#' + tableId));

        // Only calls out active/failed workflows in first 'page' of results
        if (data.some(function (item) {
            return !XNAT.plugin.batchLaunch.isWorkflowComplete(item['status']) &&
                !XNAT.plugin.batchLaunch.isWorkflowFailed(item['status']);
        })) {
            $('#active-workflows-callout').show();
        }
        if (data.some(function (item) {
            return XNAT.plugin.batchLaunch.isWorkflowFailed(item['status']);
        })) {
            $('#failed-workflows-callout').show();
        }
    }

    function tableReloadCallback() {
        XNAT.plugin.batchLaunch.containerInfo = {};
    }

    // Workflow alerts
    let $callout = $('#workflow-callouts-container');
    $callout.on('click', 'span.close', function() {
        $(this).parent('div').hide();
    });
    $callout.on('click', 'a.scroll-to', function(e) {
        let jump = $(this).attr('href');
        let new_position = $(jump).offset();
        $('html, body').stop().animate({ scrollTop: new_position.top }, 500);
        e.preventDefault();
    });

    $(document).on('click', '.toggle-days', function(){
        $('.toggle-days').css("color", "#084fab");
        $(this).css("color", "black");
        XNAT.plugin.batchLaunch.workflowTableParams.admin = $(this).data('admin') === 1;
        XNAT.plugin.batchLaunch.workflowTableParams.days = $(this).data('days');
        XNAT.plugin.batchLaunch.workflowTableParams.sortable = XNAT.plugin.batchLaunch.workflowTableParams.admin ||
            XNAT.plugin.batchLaunch.workflowTableParams.days !== -1;
        // fully clear the table so we can start fresh with filter/sort (in contrast to a within-table reload)
        $('#workflows-data-table-container').empty();
        XNAT.plugin.batchLaunch.workflowTable.tableObject = makeTableObject();
        XNAT.plugin.batchLaunch.workflowTable.emptyTable = false;
        XNAT.plugin.batchLaunch.workflowTable.tableBody = undefined;
        XNAT.plugin.batchLaunch.workflowTable.reload();
    });
}));
