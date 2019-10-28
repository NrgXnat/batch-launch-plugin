/*
 * Copyright 2019 Radiologics, Inc
 */

var XNAT = getObject(XNAT || {});
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
    XNAT.plugin.batchLaunch.workflowTable =
        getObject(XNAT.plugin.batchLaunch.workflowTable || {});

    XNAT.plugin.batchLaunch.workflowTable.tableId = "workflows-data-table";
    var columnIds = ["externalId", "label", "itemTime", "status", "pipelineName", "launchTime", "modTime", "details",
        "percentageComplete", "stepDescription"];
    var labelMap = {
        externalId: {label: "Project", show: true, type: "string"},
        label: {label: "Label", show: true, type: "string"},
        itemTime: {label: "Expt time", show: true, type: "datetime"},
        status: {label: "Status", show: true, type: "string"},
        pipelineName: {label: "Name", show: true, type: "string"},
        launchTime: {label: "Launch time", show: true, type: "datetime"},
        modTime: {label: "Last mod", show: true, type: "datetime"},
        details: {label: "Details", show: true, type: "string"},
        percentageComplete: {label: "&percnt;", show: true, type: "number"},
        stepDescription: {label: "Progress", show: true, type: "string"}
    };
    var $container;

    function parseSortAndFilterParams(sortOrFilter, column, value) {
        // Always pull filterMap from DOM; ignore column & value
        var filters = {}, label;
        var $table = $("#" + XNAT.plugin.batchLaunch.workflowTable.tableId);
        $table.find("tr.filter").children().each(function(){
            var value = $(this).find("input.filter-data").val();
            if (value) {
                // TODO add support for date filtering to UI, already in backend
                label = this.id.replace("filter-by-", "");
                filters[label] = {like: value, type: labelMap[label].type};
            }
        });
        if (filters.length === 0) {
            XNAT.plugin.batchLaunch.workflowTable.filters = undefined;
        } else {
            XNAT.plugin.batchLaunch.workflowTable.filters = filters;
        }

        if (sortOrFilter === 'filter') {
            // Need to determine how to sort
            var sortth = $table.find('th.sort.asc, th.sort.desc');
            if (sortth.length !== 1) {
                $table.find('th.sort').removeClass("asc desc");
                column = undefined;
                value = undefined;
            } else {
                column = sortth[0].id.replace("sort-by-", "");
                value = (/asc/i.test(sortth[0].className)) ? "asc" : "desc";
            }
        }
        XNAT.plugin.batchLaunch.workflowTable.sortCol = column;
        XNAT.plugin.batchLaunch.workflowTable.sortDir = value;

        //Clear old table and load new one
        XNAT.plugin.batchLaunch.workflowTable.reload();
    }

    function getDisplayUrl(data_type, id) {
        return XNAT.url.rootUrl('/app/action/DisplayItemAction/search_element/' + data_type +
            '/search_field/' + data_type + '.ID/search_value/' + id);
    }

    function spawnWorkflowTable(data, style_str){
        style_str = style_str || '';
        return {
            kind: 'table.dataTable',
            name: 'workflowsDataTable',
            id: XNAT.plugin.batchLaunch.workflowTable.tableId,
            data: data,
            before: {
                filterCss: {
                    tag: 'style|type=text/css',
                    content: style_str +
                        '#' + XNAT.plugin.batchLaunch.workflowTable.tableId + ' tbody { height: 300px; } \n' +
                        '#' + XNAT.plugin.batchLaunch.workflowTable.tableId + ' td.status { min-width: 90px; } \n' +
                        '#' + XNAT.plugin.batchLaunch.workflowTable.tableId + ' td.status .inline-actions { width: 90px; } \n' +
                        '#' + XNAT.plugin.batchLaunch.workflowTable.tableId + ' td.details{ max-width: 200px; } \n' +
                        '#' + XNAT.plugin.batchLaunch.workflowTable.tableId + ' td.percentageComplete { min-width: 100px; } \n'
                }
            },
            table: {
                classes: "clean fixed-header selectable scrollable-table",
                style: "width: auto;"
            },
            sortable: 'externalId, label, itemTime, status, pipelineName, launchTime, modTime',
            filter: 'externalId, label, status, pipelineName',
            sortAndFilterAjax: parseSortAndFilterParams,
            items: {
                // by convention, name 'custom' columns with ALL CAPS
                // 'custom' columns do not correspond directly with
                // a data item
                externalId: {
                    th: {className: 'externalId'},
                    label: labelMap['externalId']['label'],
                    apply: function(){
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
                    apply: function(){
                        if (this['dataType']) {
                            return spawn('a', {href: getDisplayUrl(this['dataType'], this['id'])}, this['label']);
                        } else {
                            return this['label'];
                        }
                    }
                },
                itemTime: {
                    th: {className: 'itemTime'},
                    label: labelMap['itemTime']['label'],
                    apply: function(){
                        return new Date(this['itemTime']).toLocaleString();
                    }
                },
                status: {
                    th: {className: 'status'},
                    label: labelMap['status']['label'],
                    apply: function(){
                        return [XNAT.plugin.batchLaunch.spawnStatusCell(this['status']),
                            XNAT.plugin.batchLaunch.spawnInlineActions(this)];
                    }
                },
                pipelineName: {
                    th: {className: 'pipelineName'},
                    label: labelMap['pipelineName']['label'],
                    apply: function(){
                        return this['pipelineName'];
                    }
                },
                launchTime: {
                    th: {className: 'launchTime'},
                    label: labelMap['launchTime']['label'],
                    apply: function(){
                        return new Date(this['launchTime']).toLocaleString();
                    }
                },
                modTime: {
                    th: {className: 'modTime'},
                    label: labelMap['modTime']['label'],
                    apply: function(){
                        return new Date(this['modTime']).toLocaleString();
                    }
                },
                details: {
                    th: {className: 'details'},
                    label: labelMap['details']['label'],
                    apply: function(){
                        return this['details'];
                    }
                },
                percentageComplete: {
                    th: {className: 'percentageComplete'},
                    label: labelMap['percentageComplete']['label'],
                    apply: function(){
                        return XNAT.plugin.batchLaunch.renderPercentComplete(this['status'], this['percentageComplete']);
                    }
                },
                stepDescription: {
                    th: {className: 'stepDescription'},
                    label: labelMap['stepDescription']['label'],
                    apply: function(){
                        return this['stepDescription'];
                    }
                }
            }
        }
    }

    function unendingScroll() {
        if ($(this).scrollTop() + $(this).innerHeight() >= $(this)[0].scrollHeight) {
            XNAT.plugin.batchLaunch.workflowTable.load();
        }
    }

    function addActions() {
        // Since $table is destroyed on reload, we want to rerun this with each reload
        var $table = $("table#" + XNAT.plugin.batchLaunch.workflowTable.tableId);

        XNAT.plugin.batchLaunch.addClickActions($table);

        // Unending table
        XNAT.plugin.batchLaunch.workflowTable.tableBody.scroll(unendingScroll);

        XNAT.plugin.batchLaunch.resizeTableCols($table);
    }

    XNAT.plugin.batchLaunch.workflowTable.reload = function() {
        XNAT.plugin.batchLaunch.containerInfo = {};
        XNAT.plugin.batchLaunch.workflowTable.tableBody.find("tr").remove();
        XNAT.plugin.batchLaunch.workflowTable.page = 0;
        XNAT.plugin.batchLaunch.workflowTable.load();
    };

    XNAT.plugin.batchLaunch.workflowTable.load = function(){
        var modalId = 'workflow_table';
        // Don't rerun on concurrent scrolling
        if (XNAT.plugin.batchLaunch.workflowTable.loading) {
            return;
        } else {
            XNAT.plugin.batchLaunch.workflowTable.loading = true;
        }

        // Add loading indicator
        openModalPanel(modalId, 'Loading processing history');

        // What kind of page are we on? What kind of table do we want?
        var id="", type="xdat:user", hide_proj = false, hide_label = false, title='History';
        if (XNAT.data && XNAT.data.context) {
            if (XNAT.plugin.batchLaunch.projectId) {
                // processing dashboard
                id = XNAT.plugin.batchLaunch.projectId;
                type = "xnat:projectData";
                hide_proj = true;
                title = id + " processing history"
            } else {
                id = XNAT.data.context.ID || id;
                type = XNAT.data.context.xsiType || type;
                hide_proj = hide_label = id !== "";
            }
        }
        if (hide_proj) {
            labelMap['externalId']['show'] = false;
        }
        if (hide_label) {
            labelMap['label']['show'] = false;
            labelMap['itemTime']['show'] = false;
        }

        // Do we have a table yet?
        var div_id = "workflows-data-table-container-content";
        $container = $('#workflows-data-table-container');
        if (!XNAT.plugin.batchLaunch.workflowTable.tableBody) {
            $container.append([
                $('<div class="data-table-titlerow"><h3 class="data-table-title">'+title+'</h3></div>'),
                $('<div class="data-table-actionsrow clearfix">' +
                    '<span class="textlink-sm data-table-action">All workflows</span>' +
                    '<button class="btn btn-sm" id="wf-table-reload">Reload</button>' +
                    '</div>'),
                $('<div id="'+div_id+'"></div>')
            ]);
            $container.on('click', '#wf-table-reload', XNAT.plugin.batchLaunch.workflowTable.reload);
        }
        var $content = $("#" + div_id);

        // Track "page" for API call
        XNAT.plugin.batchLaunch.workflowTable.page = XNAT.plugin.batchLaunch.workflowTable.page || 0;
        XNAT.plugin.batchLaunch.workflowTable.page++;

        var dataObj = {"id": id, "data_type": type, "page": XNAT.plugin.batchLaunch.workflowTable.page};
        if (XNAT.plugin.batchLaunch.workflowTable.sortCol) {
            dataObj['sort_col'] = XNAT.plugin.batchLaunch.workflowTable.sortCol;
        }
        if (XNAT.plugin.batchLaunch.workflowTable.sortDir) {
            dataObj['sort_dir'] = XNAT.plugin.batchLaunch.workflowTable.sortDir;
        }
        if (XNAT.plugin.batchLaunch.workflowTable.filters) {
            dataObj['filters'] = XNAT.plugin.batchLaunch.workflowTable.filters;
        }

        // API call
        XNAT.xhr.postJSON({
            url: XNAT.url.restUrl('/xapi/workflows'),
            data: JSON.stringify(dataObj),
            success: function (data) {
                if (!XNAT.plugin.batchLaunch.workflowTable.tableBody) {
                    // First load
                    if (data.length) {
                        // Only calls out active/failed workflows in first "page" of results
                        if (data.some(function (item) {
                            return !XNAT.plugin.batchLaunch.isWorkflowComplete(item['status']) &&
                                !XNAT.plugin.batchLaunch.isWorkflowFailed(item['status']);
                        })) {
                            $("#active-workflows-callout").show();
                        }
                        if (data.some(function (item) {
                            return XNAT.plugin.batchLaunch.isWorkflowFailed(item['status']);
                        })) {
                            $("#failed-workflows-callout").show();
                        }
                        var style_str = "";
                        var showHideList = $.map(columnIds, function(e) {
                            if (labelMap.hasOwnProperty(e)) {
                                style_str += (labelMap[e]['show']) ? '' :  '#' + XNAT.plugin.batchLaunch.workflowTable.tableId +
                                    ' .' + e +  '{ display:none; } \n';
                                return $.spawn("span.bl-dropdown-item", {},
                                    XNAT.plugin.batchLaunch.addColumnToggleContents(e, labelMap[e]['label'], labelMap[e]['show'])
                                );
                            }
                        });

                        XNAT.plugin.batchLaunch.addColumnToggle(showHideList, $container);
                        XNAT.spawner.spawn({
                            workflowTable: spawnWorkflowTable(data, style_str)
                        }).done(function () {
                            XNAT.plugin.batchLaunch.workflowTable.tableBody = this.get$().find("tbody.table-body");
                            this.render($content, addActions);
                        });
                    } else {
                        $content.html('No history to display.');
                    }
                } else {
                    // Next "page" of results
                    if (data.length) {
                        XNAT.spawner.spawn({
                            workflowTable: spawnWorkflowTable(data)
                        }).done(function () {
                            // Append only the table rows to the existing tbody
                            XNAT.plugin.batchLaunch.workflowTable.tableBody.append(this.get$().find("tbody.table-body").children());

                            XNAT.plugin.batchLaunch.applyColumnToggle($container);
                            XNAT.plugin.batchLaunch.resizeTableCols($(XNAT.plugin.batchLaunch.workflowTable.tableBody).parents("table"));
                        });
                    } else {
                        // Stop trying, no more results
                        XNAT.plugin.batchLaunch.workflowTable.tableBody.off("scroll");
                    }
                }
            },
            error: function() {
                $content.html('Issue loading history.');
            },
            complete: function() {
                closeModalPanel(modalId);
                XNAT.plugin.batchLaunch.workflowTable.loading = false;
            }
        });
    };

    // Workflow alerts
    var $callout = $('#workflow-callouts-container');
    $callout.on("click", "span.close", function() {
        $(this).parent("div").hide();
    });
    $callout.on("click", "a.scroll-to", function(e) {
        var jump = $(this).attr('href');
        var new_position = $(jump).offset();
        $('html, body').stop().animate({ scrollTop: new_position.top }, 500);
        e.preventDefault();
    });
}));