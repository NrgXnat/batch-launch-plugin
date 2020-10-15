/*
 */

var XNAT = getObject(XNAT || {});
XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.batchLaunch = getObject(XNAT.plugin.batchLaunch || {});
XNAT.plugin.containerService = getObject(XNAT.plugin.containerService || {});

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
}(function() {
    XNAT.plugin.batchLaunch.containerInfo =
        getObject(XNAT.plugin.batchLaunch.containerInfo || {});

    XNAT.plugin.batchLaunch.buildDir =
        getObject(XNAT.plugin.batchLaunch.buildDir || {});

    // Workflow
    XNAT.plugin.batchLaunch.isWorkflowFailed = function(status) {
        if (status) {
            status = status.toLowerCase();
        } else {
            return false;
        }
        return (status.startsWith("killed") || status.startsWith("failed") ||
            status.startsWith("error") || status.startsWith("destroy")) &&
            !status.includes("dismissed");
    };
    XNAT.plugin.batchLaunch.isWorkflowComplete = function(status) {
        return status === "Complete" || status.includes('(Dismissed)');
    };
    XNAT.plugin.batchLaunch.isWorkflowQueued = function(status) {
        return status.includes("Queued");
    };
    XNAT.plugin.batchLaunch.isWorkflowFinalizing = function(status) {
        return status.includes("Finalizing") || status.includes("Waiting");
    };
    XNAT.plugin.batchLaunch.canTerminateWorkflow = function(status) {
        return !XNAT.plugin.batchLaunch.isWorkflowQueued(status) &&
            !XNAT.plugin.batchLaunch.isWorkflowFinalizing(status) &&
            !XNAT.plugin.batchLaunch.isWorkflowComplete(status) &&
            !XNAT.plugin.batchLaunch.isWorkflowFailed(status);
    };
    XNAT.plugin.batchLaunch.isWorkflowContainer = function(entryMap) {
        return entryMap['justification'] === "Container launch" && entryMap['comments'];
    };
    XNAT.plugin.batchLaunch.getContainerId = function(entryMap) {
        return entryMap['comments'];
    };

    function upcaseFirstLetter(my_string) {
        return my_string.charAt(0).toUpperCase() + my_string.slice(1);
    }

    XNAT.plugin.batchLaunch.renderPercentComplete = function(status, percent) {
        if (XNAT.plugin.batchLaunch.isWorkflowComplete(status)) {
            return spawn("div.progressbar-div", {}, spawn("div.progress-bar-done", {style: "width: 100%;"}, "100%"));
        }
        if (!percent) {
            return '';
        }
        percent = Math.round(parseFloat(percent));
        if (XNAT.plugin.batchLaunch.isWorkflowFailed(status)) {
            return spawn("div.progressbar-div", {}, spawn("div.progress-bar-done",
                {style: "width: "+percent+"%;"}, percent.toString() + "%"));
        }
        if (percent >= 100) {
            percent = 99;
        }
        return spawn("div.progressbar-div", {}, spawn("div.progress-bar",
            {style: "width: "+percent+"%;"}, percent.toString() + "%"));
    };

    XNAT.plugin.batchLaunch.spawnStatusCell = function(status) {
        var cclass, icon;
        if (XNAT.plugin.batchLaunch.isWorkflowFailed(status)) {
            cclass = ".text-error";
            icon = ".fa-warning";
        } else if (XNAT.plugin.batchLaunch.isWorkflowQueued(status)) {
            cclass = ".text-warning";
            icon = ".fa-clock-o";
        } else if (XNAT.plugin.batchLaunch.isWorkflowComplete(status)) {
            cclass = "";
            icon = ".fa-check-circle";
        } else if (status === "Ready") {
            cclass = ".text-default";
            icon = "";
        } else {
            cclass = ".text-success";
            icon = ".fa-cogs";
        }
        return spawn("span" + cclass,
            [spawn("i.fa" + icon), "&nbsp;&nbsp;" + upcaseFirstLetter(status)]);
    };

    XNAT.plugin.batchLaunch.refreshWorkflowRow = function($link, workflowId) {
        var $status_td = $link.closest("td");
        var $perc = $status_td.siblings("td.percentageComplete");
        var single_cell = $perc.length === 0;

        var prev_status_html = $status_td.html();
        $status_td.html("Refreshing...");
        XNAT.xhr.getJSON({
            url: XNAT.url.restUrl('/xapi/workflows/'+workflowId),
            success: function(data) {
                //status
                $status_td.html(XNAT.plugin.batchLaunch.spawnStatusCell(data['status']));
                $status_td.append(XNAT.plugin.batchLaunch.spawnInlineActions(data));

                if (!single_cell) {
                    //step
                    $status_td.siblings("td.stepDescription").html(data['stepDescription'] || "");

                    //percent complete
                    $perc.html(
                        XNAT.plugin.batchLaunch.renderPercentComplete(data['status'], data['percentageComplete'] || "")
                    );

                    //details
                    $status_td.siblings("td.details").html(data['details'] || "");
                }

                if (XNAT.plugin.batchLaunch.isWorkflowContainer(data)) {
                    // Force reload of container details
                    XNAT.plugin.batchLaunch.containerInfo[XNAT.plugin.batchLaunch.getContainerId(data)] = undefined;
                }
            },
            error: function() {
                $status_td.html(prev_status_html);
            }
        });
    };

    XNAT.plugin.batchLaunch.spawnInlineActions = function(entryMap) {
        if (!entryMap['wfid']) {
            return spawn("span.inline-actions", []);
        }
        var idstr = '|data-id="' + entryMap['wfid'] + '"|data-curstatus="' + entryMap['status'] + '"';
        var children = [
            spawn('i.fa.fa-download.wf-builddir|title="View build directory' + idstr)
        ];
        var summary_str = 'i.fa.fa-eye.wf-view-details|title="View workflow summary"' + idstr;

        if (XNAT.plugin.batchLaunch.isWorkflowContainer(entryMap)) {
            var contstr = '|data-containerid="' + entryMap['comments'] + '"';
            children.push(spawn(summary_str + contstr));
            if (XNAT.plugin.batchLaunch.canTerminateWorkflow(entryMap['status'])) {
                children.push(spawn('i.fa.fa-ban.wf-terminate|title="Terminate job"' + idstr + contstr));
            }
        } else {
            children.push(spawn(summary_str));
        }

        if (XNAT.plugin.batchLaunch.isWorkflowFailed(entryMap['status'])) {
            children.push(spawn('i.fa.fa-archive.wf-dismiss|title="Dismiss failure status"' + idstr));
        } else if (!XNAT.plugin.batchLaunch.isWorkflowComplete(entryMap['status'])) {
            children.push(spawn('i.fa.fa-refresh.wf-refresh|title="Refresh workflow status"' + idstr));
            children.push(spawn('i.fa.fa-times.wf-fail|title="Mark workflow as failure"' + idstr));
        }
        return spawn("span.inline-actions",children);
    };

    XNAT.plugin.batchLaunch.addClickActions = function($parent_element) {
        $parent_element = $parent_element || $("document");
        $parent_element.on("click", ".wf-refresh", function(){
            XNAT.plugin.batchLaunch.refreshWorkflowRow($(this), $(this).data("id"));
        });
        $parent_element.on("click", ".wf-view-details", function(){
            XNAT.plugin.batchLaunch.viewWorkflowDetails($(this).data("id"), $(this).data("containerid"));
        });
        $parent_element.on("click", ".wf-builddir", function(){
            XNAT.plugin.batchLaunch.viewWorkflowBuilddir($(this).data("id"));
        });
        $parent_element.on("click", ".wf-terminate", function(){
            var $link = $(this), id = $(this).data("id");
            XNAT.plugin.batchLaunch.killProcess(id,
                function(){XNAT.plugin.batchLaunch.refreshWorkflowRow($link, id);});
        });
        $parent_element.on("click", ".wf-dismiss", function(){
            var $link = $(this), id = $(this).data("id"), curStatus = $(this).data("curstatus");
            XNAT.plugin.batchLaunch.dismissNotification(id, curStatus, 'Failed (Dismissed)',
                function(){XNAT.plugin.batchLaunch.refreshWorkflowRow($link, id);});
        });
        $parent_element.on("click", ".wf-fail", function(){
            var $link = $(this), id = $(this).data("id"), curStatus = $(this).data("curstatus");
            XNAT.plugin.batchLaunch.dismissNotification(id, curStatus, 'Failed (User-set)',
                function(){XNAT.plugin.batchLaunch.refreshWorkflowRow($link, id);});
        });
    };

    XNAT.plugin.batchLaunch.viewWorkflowFile = function(workflowId, fileType) {
        // FileType is stdout or stderr
        var logFileUrl = XNAT.url.rootUrl('xapi/workflows/' + workflowId + '/logs/' + fileType);
        XNAT.ui.dialog.iframe(logFileUrl, 'File: ' + fileType, 580, 600);
    };

    XNAT.plugin.batchLaunch.getContainerInfo = function(containerId, callbackSuccess, callbackFailure) {
        var historyEntry  = XNAT.plugin.batchLaunch.containerInfo[containerId];

        var loadingDialog = XNAT.ui.dialog.loading;
        loadingDialog.open();
        if (!historyEntry) {
            XNAT.xhr.getJSON({
                url: XNAT.url.restUrl('/xapi/containers/' + containerId),
                success: function(data) {
                    XNAT.plugin.batchLaunch.containerInfo[containerId] = historyEntry = data;
                    callbackSuccess(data);
                },
                error: callbackFailure
            });
        } else {
            callbackSuccess(historyEntry);
        }
        loadingDialog.close();
    };

    XNAT.plugin.batchLaunch.viewWorkflowDetails = function(workflowId, containerId) {
        // Do we have a container id?
        if (containerId) {
            XNAT.plugin.batchLaunch.getContainerInfo(containerId,
                XNAT.plugin.containerService.historyTable.viewHistoryEntry,
                function() {XNAT.plugin.containerService.historyTable.workflowModal(workflowId);});
        } else {
            var loadingDialog = XNAT.ui.dialog.loading;
            loadingDialog.open();
            XNAT.plugin.containerService.historyTable.workflowModal(workflowId);
            loadingDialog.close();
        }
    };

    XNAT.plugin.batchLaunch.killProcess = function(workflowId, callback) {
        callback = isFunction(callback) ? callback : function(){};
        XNAT.ui.dialog.open({
            title: 'Terminate job confirmation',
            content: 'Are you sure you want to terminate the job?',
            buttons: [
                {
                    label: 'Cancel',
                    isDefault: false,
                    close: true
                },
                {
                    label: 'Yes',
                    isDefault: true,
                    close: true,
                    action: function (obj) {
                        var loadingDialog = XNAT.ui.dialog.loading;
                        loadingDialog.open();

                        function killProcessOK(data, status, o) {
                            loadingDialog.close();
                            XNAT.ui.dialog.message('Success', 'Successfully terminated job; note that status may not update immediately');
                            callback();
                        }

                        function killProcessFailed(o, status, error) {
                            loadingDialog.close();
                            XNAT.ui.dialog.message('Error', 'An unexpected error has occurred while killing job ' + workflowId + '. Please contact your administrator.');
                        }

                        XNAT.xhr.post({
                            url: XNAT.url.restUrl('/xapi/workflows/' + workflowId + '/kill'),
                            success: killProcessOK,
                            error: killProcessFailed
                        });
                    }
                }
            ]
        });
    };

    XNAT.plugin.batchLaunch.dismissNotification = function(id, curStatus, newStatus, callback) {
        var afterShowFn = function(){};
        callback = isFunction(callback) ? callback : function(){};
        function workflowUpdate() {
            var loadingDialog = XNAT.ui.dialog.loading;
            loadingDialog.open();

            function workflowUpdateOK() {
                loadingDialog.close();
                XNAT.ui.dialog.message('Success', 'Successfully updated workflow status to "<b>' + newStatus + '</b>".');
                callback();
            }

            function workflowUpdateFailed(o, status, error) {
                loadingDialog.close();
                XNAT.ui.dialog.message('Error', 'An unexpected error has occurred. Please contact your administrator.');
                console.log('Status: ' + status + '. Error: ' + error);
            }

            var url = '/data/workflows/' + id + '?' + 'wrk:workflowData/status=' + newStatus;

            jQuery.ajax({
                type: 'PUT',
                url: XNAT.url.csrfUrl(url),
                success: workflowUpdateOK,
                error: workflowUpdateFailed
            });
        }

        var confirmP = spawn('p', {style: 'margin-bottom:20px'},
            'Are you <b>sure</b> you want to change the status of this workflow to "<b>' + newStatus + '</b>"?');
        var confirmation;
        if (XNAT.plugin.batchLaunch.canTerminateWorkflow(curStatus)) {
            var terminateLinkId = 'terminate-' + id;
            var terminateLink = spawn('a', {id: terminateLinkId}, 'terminate the job');
            confirmation = spawn('div', {style: 'margin-bottom:20px'}, [
                spawn('div.warning', {style: 'margin-bottom:20px'}, ['<b>Warning:</b> Your job is in a state that can ' +
                    'be terminated. You should first attempt to ', terminateLink, ', and only mark as failed if you ' +
                    'are confident it is no longer active.']),
                confirmP
            ]);
            afterShowFn = function() {
                $(document).off('click', '#' + terminateLinkId); // remove any previous
                $(document).on('click', '#' + terminateLinkId, function(){
                    XNAT.ui.dialog.close();
                    XNAT.plugin.batchLaunch.killProcess(id, callback);
                });
            };
        } else {
            confirmation = spawn('div', {style: 'margin-bottom:20px'}, [
                confirmP,
                spawn('div.message', {}, '<b>Note:</b> This does <b>not</b> affect the actual ' +
                    'job; it merely changes the displayed status. If the job is able to run, it will do so, and may ' +
                    'change the status back.')
            ]);
        }

        XNAT.ui.dialog.confirm({
            content: confirmation,
            afterShow: afterShowFn,
            okAction: workflowUpdate,
            cancelAction: function () {},
            width: 420
        });
    };

    function getBuildDir(workflowId, callbackSuccess, callbackFailure) {
        var buildEntry = XNAT.plugin.batchLaunch.buildDir[workflowId];
        if (!buildEntry) {
            XNAT.xhr.get({
                url: XNAT.url.restUrl('/xapi/workflows/'+workflowId+'/build_dir'),
                dataType: "json",
                success: function(data) {
                    XNAT.plugin.batchLaunch.buildDir[workflowId] = buildEntry = data;
                    callbackSuccess(buildEntry, workflowId);
                },
                error: callbackFailure,
                complete: function(){XNAT.ui.dialog.loading.close();}
            });
        } else {
            callbackSuccess(buildEntry, workflowId);
            XNAT.ui.dialog.loading.close();
        }
    }

    XNAT.plugin.batchLaunch.viewWorkflowBuilddir = function(workflowId) {
        function noBuilddir() {
            XNAT.ui.dialog.open({
                title: 'Build directory',
                content: 'Sorry, no build directory available for this item.',
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });
        }
        function displayBuildDir(buildEntry, workflowId) {
            XNAT.ui.dialog.open({
                title: 'Build directory',
                header: true,
                maxBtn: true,
                content: spawn("div",
                    [
                        spawn("form", {
                            name: "buildDirZipForm",
                            id: "buildDirZipForm",
                            method: "POST",
                            action: XNAT.url.csrfUrl("/xapi/workflows/"+workflowId+"/get_zip")
                        }),
                        spawn("div#buildDirZipTree")
                    ]),
                buttons: [
                    {
                        label: 'Download',
                        isDefault: true,
                        close: false,
                        action: function(modal) {
                            let paths = [];
                            $("#buildDirZipTree").fancytree('getTree').getRootNode().visit(function(node) {
                                // get selected file nodes, selected folder nodes with downloadAll (>50 children, not displayed),
                                // and selected lazy-load folder nodes without expanded children (meaning never loaded)
                                if (node.selected && !node.isPagingNode() &&
                                    (!node.folder || node.data.downloadAll || (node.lazy && !node.children))) {
                                    paths.push(node.data.path);
                                }
                            });
                            if (paths.length === 0) {
                                XNAT.ui.dialog.alert("Nothing selected for download");
                                return;
                            }
                            $("form#buildDirZipForm").append($('<input>').attr({
                                type: 'hidden',
                                id: 'inputPaths',
                                name: 'inputPaths',
                                value: paths
                            })).submit();
                            modal.close();
                        }
                    },
                    {
                        label: 'Close',
                        close: true
                    }
                ]
            }).ready(function(){
                $("#buildDirZipTree").fancytree({
                    // Don't use fancytree loading bc if the REST call errors, we want to default to a nice error modal
                    source: buildEntry,
                    checkbox: true,
                    selectMode: 3,
                    lazyLoad: function(event, data){
                        let node = data.node;
                        data.result = {
                            url: XNAT.url.rootUrl(node.data.url),
                            data: {inputPath: node.data.fullpath},
                            cache: true
                        };
                    },
                    loadChildren: function(event, data) {
                        let node = data.node;
                        if (node.children && node.children.length === 1 && node.children[0].isPagingNode()) {
                            // we need to change this node, now that it has been loaded, to a folder with > 50 children
                            node.data.downloadAll = true;
                        } else {
                            // apply parent's state to newly loaded child nodes
                            node.fixSelection3AfterClick();
                        }
                    },
                    click: function(event, data) {
                        let node = data.node, targetType = data.targetType;
                        if (node.folder && targetType !== 'checkbox' && targetType !== 'expander') {
                            node.setExpanded(!node.expanded);
                        }
                    },
                    clickPaging: function(event, data) {
                        let parent = data.node.parent;
                        parent.setSelected(!parent.selected);
                        parent.setExpanded(false);
                    }
                });
            });
        }

        XNAT.ui.dialog.loading.open();
        getBuildDir(workflowId, displayBuildDir, noBuilddir);
    };

    // UI
    XNAT.plugin.batchLaunch.setTableWidth = function(div_id, div_title_id) {
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
    };

    XNAT.plugin.batchLaunch.setTableHeight = function(div_id) {
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
            var minTableHeight = 500;

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
    };
}));
