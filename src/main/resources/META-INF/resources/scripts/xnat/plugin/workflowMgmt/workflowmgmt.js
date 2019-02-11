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
        return (status.startsWith("killed") || status.startsWith("failed") || status.startsWith("error")) &&
            !status.includes("dismissed");
    };
    XNAT.plugin.batchLaunch.isWorkflowComplete = function(status) {
        return status === "Complete" || status.includes('(Dismissed)');
    };
    XNAT.plugin.batchLaunch.isWorkflowQueued = function(status) {
        return status === "Queued" || status === "Created";
    };
    XNAT.plugin.batchLaunch.isWorkflowContainer = function(entryMap) {
        return entryMap['justification'] === "Container launch" && entryMap['comments'];
    };

    function upcaseFirstLetter(my_string) {
        return my_string.charAt(0).toUpperCase() + my_string.slice(1);
    }

    XNAT.plugin.batchLaunch.renderPercentComplete = function(percent) {
        if (!percent) {
            return '';
        }
        if (percent >= 100) {
            percent = 99;
        }
        return spawn("div.progressbar-div", {}, spawn("div.progress-bar", {style: "width: "+percent+"%;"}, percent.toString() + "%"));
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
        var $status_td = $link.parents("td");
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
                    $link.parents("td").siblings("td.stepDescription").html(data['stepDescription'] || "");

                    //percent complete
                    $perc.html(
                        XNAT.plugin.batchLaunch.renderPercentComplete(data['percentageComplete'] || "")
                    );
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
        var idstr = '|data-id="' + entryMap['wfid'] + '"';
        var children = [
            spawn('i.fa.fa-download.wf-builddir|title="View build directory' + idstr)
        ];
        var summary_str = 'i.fa.fa-eye.wf-view-details|title="View workflow summary"' + idstr;

        if (XNAT.plugin.batchLaunch.isWorkflowContainer(entryMap)) {
            var contstr = '|data-containerid="' + entryMap['comments'] + '"';
            children.push(spawn(summary_str + contstr));
            if (!XNAT.plugin.batchLaunch.isWorkflowFailed(entryMap['status']) && !XNAT.plugin.batchLaunch.isWorkflowComplete(entryMap['status'])) {
                children.push(spawn('i.fa.fa-ban.wf-terminate|title="Terminate processing"' + idstr + contstr));
            }
        } else {
            children.push(spawn(summary_str));
        }

        if (XNAT.plugin.batchLaunch.isWorkflowFailed(entryMap['status'])) {
            children.push(spawn('i.fa.fa-archive.wf-dismiss|title="Dismiss failure status"' + idstr));
        } else if (!XNAT.plugin.batchLaunch.isWorkflowComplete(entryMap['status'])) {
            children.push(spawn('i.fa.fa-refresh.wf-refresh|title="Refresh workflow status"' + idstr));
            children.push(spawn('i.fa.fa-times.wf-fail"|title="Mark workflow as failure"' + idstr));
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
            XNAT.plugin.batchLaunch.killProcess($(this).data("id"),
                function(){XNAT.plugin.batchLaunch.refreshWorkflowRow($link, id);});
        });
        $parent_element.on("click", ".wf-dismiss", function(){
            var $link = $(this), id = $(this).data("id");
            XNAT.plugin.batchLaunch.dismissNotification($(this).data("id"), 'Failed (Dismissed)',
                function(){XNAT.plugin.batchLaunch.refreshWorkflowRow($link, id);});
        });
        $parent_element.on("click", ".wf-fail", function(){
            var $link = $(this), id = $(this).data("id");
            XNAT.plugin.batchLaunch.dismissNotification($(this).data("id"), 'Failed',
                function(){XNAT.plugin.batchLaunch.refreshWorkflowRow($link, id);});
        });
    };

    XNAT.plugin.batchLaunch.toggleColumn = function($container, target, show) {
        var $columns = $container.find("th." + target + ", td." + target);
        if (show) {
            $columns.show();
        } else {
            $columns.hide();
        }
    };

    XNAT.plugin.batchLaunch.addColumnToggleContents = function(colClass, displayName, show) {
        var checked = (show) ? "|checked='checked'" : "";
        return [
            $.spawn("input" + checked, {
                id: "show-" + colClass,
                type: "checkbox"
            }),
            $.spawn("label|for='show-" + colClass + "'", {}, displayName)
        ];
    };

    XNAT.plugin.batchLaunch.applyColumnToggle = function($container){
        var dropdown = "div.show-hide-columns-list.bl-dropdown-menu";
        $container.find(dropdown + ' input').each(function(){
            XNAT.plugin.batchLaunch.toggleColumn($container, this.id.replace("show-", ""), $(this).prop("checked"));
        });
    };

    XNAT.plugin.batchLaunch.addColumnToggle = function(showHideList, $container){
        // Toggle columns
        var $actionsRow = $container.find('.data-table-actionsrow');
        var button = "button.show-hide-columns.pull-right";
        var dropdown = "div.show-hide-columns-list.bl-dropdown-menu";
        var $button = $actionsRow.find(button);
        if ($button.length) {
            // Just remove so we don't get duplicate actions
            $button.remove();
            $actionsRow.find(dropdown).remove();
        }
        $button = $.spawn(button, {}, ["Columns", "&nbsp;", $.spawn("i.fa.fa-caret-down")]);
        $actionsRow.append($button);
        var $dropdown = $.spawn(dropdown, {}, showHideList);
        $actionsRow.append($dropdown);

        $container.on('click', button, function () {
            if ($dropdown.css("visibility") === "visible") {
                $button.find("i").removeClass("fa-caret-up").addClass("fa-caret-down");
                $dropdown.css({
                    visibility: "hidden",
                    transform: "translate3d(0,0,0)"
                });
            } else {
                var coords = $button.offset();
                var listcoords = $dropdown.offset();
                var leftt = coords['left'] - listcoords['left'],
                    topt = coords['top'] - listcoords['top'] + XNAT.plugin.batchLaunch.cssToNumber($button, "height");
                $(this).find("i").removeClass("fa-caret-down").addClass("fa-caret-up");
                $dropdown.css({
                    visibility: "visible",
                    transform: "translate3d(" + leftt + "px, " + topt + "px, 0)"
                });
            }
            return false;
        });
        $container.on('click', dropdown + ' input', function () {
            XNAT.plugin.batchLaunch.toggleColumn($container, this.id.replace("show-", ""), $(this).prop("checked"));
            XNAT.plugin.batchLaunch.resizeTableCols($container.find("table"));
            $button.click().click(); // keep it in view, but be sure to transform if table size changes
        });
    };

    XNAT.plugin.batchLaunch.viewWorkflowFile = function(workflowId, fileType) {
        // FileType is stdout or stderr
        var logFileUrl = XNAT.url.rootUrl('xapi/workflows/' + workflowId + '/logs/' + fileType);
        XNAT.ui.dialog.iframe(logFileUrl, 'File: ' + fileType, 580, 600);
    };

    XNAT.plugin.batchLaunch.getContainerInfo = function(containerId, callbackSuccess, callbackFailure) {
        var historyEntry  = XNAT.plugin.batchLaunch.containerInfo[containerId];

        XNAT.ui.dialog.loading.open();
        if (!historyEntry) {
            XNAT.xhr.getJSON({
                url: XNAT.url.restUrl('/xapi/containers/' + containerId),
                success: function(data) {
                    XNAT.plugin.batchLaunch.containerInfo[containerId] = historyEntry = data;
                    callbackSuccess(historyEntry);
                },
                error: callbackFailure
            });
        } else {
            callbackSuccess(historyEntry);
        }
        XNAT.ui.dialog.loading.close();
    };

    XNAT.plugin.batchLaunch.viewWorkflowDetails = function(workflowId, containerId) {
        function wfModal(workflowId) {
            // rptModal in xdat.js
            rptModal.call(this, workflowId, "wrk:workflowData", "wrk:workflowData.wrk_workflowData_id");
        }
        // Is container service installed and do we have a container id?
        if (XNAT.plugin.containerService.historyTable && containerId) {
            XNAT.plugin.batchLaunch.getContainerInfo(containerId,
                XNAT.plugin.containerService.historyTable.viewHistoryEntry,
                function() {wfModal(workflowId);});
        } else {
            XNAT.ui.dialog.loading.open();
            wfModal(workflowId);
            XNAT.ui.dialog.loading.close();
        }
    };

    XNAT.plugin.batchLaunch.killProcess = function(workflowId, callback) {
        callback = isFunction(callback) ? callback : function(){};
        XNAT.ui.dialog.open({
            title: 'Terminate process confirmation',
            content: 'Are you sure you want to terminate the process?',
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
                        XNAT.ui.dialog.loading.open();

                        function killProcessOK(data, status, o) {
                            XNAT.ui.dialog.loading.close();
                            XNAT.ui.dialog.message('Success', 'Successfully terminated process; note that status may not update immediately');
                            callback();
                        }

                        function killProcessFailed(o, status, error) {
                            XNAT.ui.dialog.loading.close();
                            XNAT.ui.dialog.message('Error', 'An unexpected error has occurred while killing process ' + workflowId + '. Please contact your administrator.');
                        }

                        XNAT.xhr.post({
                            url: XNAT.url.restUrl('/xapi/workflows/' + workflowId + '/kill'),
                            success: killProcessOK,
                            error: killProcessFailed
                        });
                        XNAT.ui.dialog.closeAll();
                    }
                }
            ]
        });
    };

    XNAT.plugin.batchLaunch.dismissNotification = function(id, st, callback) {
        callback = isFunction(callback) ? callback : function(){};
        function workflowUpdate() {
            XNAT.ui.dialog.loading.open();

            function workflowUpdateOK() {
                XNAT.ui.dialog.loading.close();
                XNAT.ui.dialog.message('Success', 'Successfully updated workflow status to "<b>' + st + '</b>".');
                callback();
            }

            function workflowUpdateFailed(o, status, error) {
                XNAT.ui.dialog.loading.close();
                XNAT.ui.dialog.message('Error', 'An unexpected error has occurred. Please contact your administrator.');
                console.log('Status: ' + status + '. Error: ' + error);
            }

            var url = '/data/workflows/' + id + '?' + 'wrk:workflowData/status=' + st;

            jQuery.ajax({
                type: 'PUT',
                url: XNAT.url.csrfUrl(url),
                success: workflowUpdateOK,
                error: workflowUpdateFailed
            });
        }

        var confirmation_message =
            '<p>Are you sure you want to change the status of this ' +
            'workflow to "<b>' + st + '</b>"?</p>' +
            '<div class="message" style="margin-top:20px;"><b>Note:</b> ' +
            'This will not affect the actual pipeline. If the pipeline ' +
            'is still running, it may change the status.</div>';

        XNAT.ui.dialog.confirm({
            content: confirmation_message,
            okAction: workflowUpdate,
            cancelAction: function () {},
            width: 420,
            height: 240
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
                        label: 'Close',
                        isDefault: true,
                        close: true
                    },
                    {
                        label: 'Download',
                        isDefault: false,
                        close: true,
                        action: function() {
                            var paths = $.map($("#buildDirZipTree").fancytree('getTree').getSelectedNodes(), function(node){
                                if (!node.folder) return node.data.path;
                            });
                            if (!paths || paths.length === 0) {
                                XNAT.ui.dialog.alert("Nothing selected for download");
                                return false;
                            }
                            $("form#buildDirZipForm").append($('<input>').attr({
                                type: 'hidden',
                                id: 'inputPaths',
                                name: 'inputPaths',
                                value: paths
                            })).submit();
                        }
                    }
                ]
            }).ready(function(){
                function expandAndCollapse(node, expand) {
                    if (node.folder) {
                        node.children.forEach(expandAndCollapse, expand);
                        node.setExpanded(expand);
                    }
                }
                $("#buildDirZipTree").fancytree({
                    // Don't use fancytree loading bc if the REST call errors, we want to default to a nice error modal
                    source: buildEntry,
                    checkbox: true,
                    selectMode: 3,
                    click: function(event, data) {
                        var node = data.node, targetType = data.targetType;
                        if (node.folder && targetType !== 'expander') {
                            var expand = !node.expanded;
                            if (targetType === 'checkbox') {
                                expand = !node.selected;
                                expandAndCollapse(node, expand);
                            } else {
                                node.setExpanded(expand);
                            }
                        }
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

    XNAT.plugin.batchLaunch.cssToNumber = function($item, attrName) {
        var ws = $item.css(attrName) || "0";
        return Number(ws.replace(/[^\d\.]/g, ""));
    };

    XNAT.plugin.batchLaunch.resizeTableCols = function($table){
        var $headerCells = $table.find("thead tr:not(:hidden):first").children(":not(:hidden)"),
            $filterCells = $table.find("thead tr:not(:hidden):last").children(":not(:hidden)"),
            $bodyCells = $table.find("tbody tr:not(:hidden):first").children(":not(:hidden)");

        // Set common width for thead & tbody cells (needed for scrollable tbody)
        $bodyCells.each(function (i, v) {
            var wid = Math.max(
                XNAT.plugin.batchLaunch.cssToNumber($(v), "width"),
                XNAT.plugin.batchLaunch.cssToNumber($($headerCells[i]), "width")
            );
            $(v).css("width", wid);
            $($headerCells[i]).css("width", wid);
            $($filterCells[i]).css("width", wid);
        });
    };
}));
