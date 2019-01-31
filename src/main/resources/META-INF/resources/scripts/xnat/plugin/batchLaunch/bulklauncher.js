var XNAT = getObject(XNAT || {});
XNAT.plugin = getObject(XNAT.plugin || {});
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
}(function() {
    var sessionPipelineWorkFlowStatus = {};
    var $dataRows = [];
    var tableId = 'xnat-table';

    // Similar to table.js, but no way to use it from there
    function cacheRows(){
        if (!$dataRows.length) {
            $dataRows = $('table#' + tableId + " tbody tr");
        }
        return $dataRows;
    }

    function filterRows(val, name){
        if (!val) { return false }
        val = val.toLowerCase();
        var filterClass = 'filter-' + name;
        // cache the rows if not cached yet
        cacheRows();
        $dataRows.addClass(filterClass).filter(function(){
            return $(this).find('td.' + name + ':containsNC("'+val+'")').length;
        }).removeClass(filterClass);
    }

    function isWorkflowFailed(status) {
        return status.startsWith("Killed") || status.startsWith("Failed") || status == "Failed" || status.startsWith("Error") || status == "Error";
    }
    function isWorkflowComplete(status) {
        return status == "Complete" || status == "Failed (Dismissed)";
    }

    function findLabel(key) {
        return key.indexOf('identifier') > 0;
    }

    function launcherTableInit() {
        xmodal.loading.open({title: 'Loading information...'});

        var xml = document.getElementById("xss").value;
        var identifierKey = "session_id";
        var subjectIdentifierKey = "xnat_subjectdata_subjectid";
        var subjectLabelKey = "";
        var sessionLabelKey = "";
        var isDetails = window.location.href.match(/\/job\/[^\/]*$/);

        XNAT.xhr.post({
            url: XNAT.url.restUrl('REST/search?format=json&XNAT_CSRF=' + window.csrfToken),
            data: xml,
            success: function (responseData) {
                //Add table
                var divContent = '	<div class="data-table-titlerow" id="data-table-titlerow">							';
                divContent += '	    <h3 class="data-table-title">Select experiments to launch processing</h3>		';
                divContent += '	    <div class="data-table-actionsrow" id="data-table-actionsrow">							';
                divContent += '	        <span  class="textlink-sm data-table-action">					';
                divContent += '	          <select id="actionsDropdown" class="data-table-action disabled"  disabled>	';
                divContent += '	          </select>									';
                divContent += '	        </span>										';
                divContent += ' 	<button class="btn btn-sm data-table-action disabled" id="launch-container">Launch container</button>	';
                //divContent +=     '		<button class="btn btn-sm data-table-action disabled" onclick="javascript:terminateContainers()">Terminate Containers</button>	';
                divContent += '		<button class="btn btn-sm" type="submit" id="reload">Reload</button>				';
                if (isDetails) {
                    divContent += '		<a class="btn btn-sm" href="'+window.location.href.replace(/\/job\/[^\/]*/,'')+'">Show all pipelines</a>';
                }
                divContent += '	    </div>													';
                divContent += '    <span class="clear clearfix"></span>									';
                divContent += '	</div>														';
                divContent += '	<div class="data-table-wrapper" id="div-"'+tableId+'"-header" style="overflow-x:scroll;overflow-y:scroll;padding-right:0;">						';
                divContent += '	       <table id="'+tableId+'" class="xnat-table clean  selectable" style="border:none;">		';
                divContent += '	            <thead>												';
                divContent += '		            <tr id="xnat-table-header-row1">								';
                divContent += '		                <th class="toggle-all" style="width: 45px;">						';
                divContent += '		                    <input type="checkbox" class="selectable-select-all" id="toggle-all-sessions" title="Toggle All Sessions" />	';
                divContent += '		                </th>															';
                divContent += '		            </tr>															';
                divContent += '		            <tr id="xnat-table-header-row2">								';
                divContent += '		                <td style="width: 45px;"></td>						        ';
                divContent += '		            </tr>															';
                divContent += '	            </thead>																';
                divContent += '	            <tbody id="xnat-table-datarows-tbody">												';
                divContent += '             </tbody>																';
                divContent += '	        </table>																';
                divContent += '	</div>																		';
                $('#selectable-table-bulk').append(divContent);


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
                            columnsToShow[header] = {
                                show: 0,
                                label: label,
                                labelClean: label.replace(' ','-'),
                                type: d.type,
                                pipeline: d.key.startsWith("wrk_status") && d.type === "string" && d.xPATH.replace(/[^.]*./,'') === "WRK_STATUS" //Hack
                            };
                        }
                    }
                });

                columnsToShow['Project']['show'] = 0;
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
                                    if (workFlowStatus || key.startsWith("res_file") || key.startsWith("wrk_status_launch") || key.startsWith("wrk_status_numrows") || key.startsWith("wrk_status_lastmod") || key.startsWith("scan_type_count")) {
                                        columnsToShow[wrk_col]['show'] = 1;
                                        return false;
                                    }
                                }
                            }
                        }
                    });
                }

                // Add thead with filters
                var $filterInput, label, labelClean;
                var showHideList = [];
                var filterCssList = [];
                for (var header_col in columnsToShow) {
                    if (columnsToShow[header_col]['show'] === 1) {
                        label = columnsToShow[header_col]['label'];
                        labelClean = columnsToShow[header_col]['labelClean'];
                        var filterClass = 'filter-' + labelClean;
                        filterCssList.push(filterClass);
                        $('tr#xnat-table-header-row1').append($('<th id="th-' + labelClean + '" class="left sort"  style="width:120px;word-wrap:break-word;">' + label + '</th>'));
                        //Filter for each column
                        if (columnsToShow[header_col]['type'] === 'date') {
                            var MIN = 60 * 1000;
                            var HOUR = MIN * 60;
                            var X8HRS = HOUR * 8;
                            var X24HRS = HOUR * 24;
                            var X7DAYS = X24HRS * 7;
                            var X30DAYS = X24HRS * 30;
                            $filterInput = $.spawn('div', [XNAT.ui.select.menu({
                                value: 0,
                                options: {
                                    all: {
                                        label: 'All',
                                        value: 0,
                                        selected: true
                                    },
                                    lastHour: {
                                        label: 'Last Hour',
                                        value: HOUR
                                    },
                                    last8hours: {
                                        label: 'Last 8 Hrs',
                                        value: X8HRS
                                    },
                                    last24hours: {
                                        label: 'Last 24 Hrs',
                                        value: X24HRS
                                    },
                                    lastWeek: {
                                        label: 'Last Week',
                                        value: X7DAYS
                                    },
                                    last30days: {
                                        label: 'Last 30 days',
                                        value: X30DAYS
                                    }
                                },
                                element: {
                                    id: filterClass,
                                    on: {
                                        change: function () {
                                            var filterClass = this.id;
                                            var colClass = filterClass.replace('filter-','');
                                            var selectedValue = parseInt(this.value, 10);
                                            var currentTime = Date.now();
                                            if (selectedValue === 0) {
                                                $dataRows.removeClass(filterClass);
                                            } else {
                                                cacheRows();
                                                $dataRows.addClass(filterClass).filter(function () {
                                                    var timestamp = $(this).find('td.' + colClass).text(), date;
                                                    return timestamp && (date = new Date(timestamp)) &&
                                                        (selectedValue === date - 1 || selectedValue > (currentTime - date));
                                                }).removeClass(filterClass);
                                            }
                                            resizeTableCols(tableId);
                                            setStateSelectAllToggle($('.selectable-select-all'));
                                            $("span#table-visible-count").text($('#'+tableId+' tbody tr:not(:hidden)').length);
                                        }
                                    }
                                }
                            }).element]);
                        } else {
                            $filterInput = $.spawn('input#filter-' + labelClean + '.filter-data', {
                                type: 'text',
                                title: labelClean + ':filter',
                                placeholder: 'Search...',
                                style: 'width: 90%;'
                            });
                            $filterInput.on('keyup', function(e){
                                var colClass = this.id.replace('filter-', '');
                                var val = this.value;
                                var key = e.which;
                                // don't do anything on 'tab' keyup
                                if (key == 9) return false;
                                if (key == 27){ // key 27 = 'esc'
                                    this.value = val = '';
                                }
                                if (!val || key == 8) {
                                    $dataRows.removeClass('filter-' + colClass);
                                }
                                if (!val) {
                                    // no value, no filter
                                    return false;
                                }
                                filterRows(val, colClass);
                                resizeTableCols(tableId);
                                setStateSelectAllToggle($('.selectable-select-all'));
                                $("span#table-visible-count").text($('#'+tableId+' tbody tr:not(:hidden)').length);
                            });
                        }

                        //Show/hide checkbox
                        $('tr#'+tableId+'-header-row2').append($("<td style='width:120px;'></td>").append($filterInput));
                        var dropdownItemContents = [
                            $.spawn("input|checked='checked'", {
                                id: "show-" + labelClean,
                                type: "checkbox"
                            }),
                            $.spawn("label|for='show-" + labelClean + "'", {}, label)
                        ];
                        if (!isDetails && columnsToShow[header_col]['pipeline']) {
                            dropdownItemContents.push("&nbsp;");
                            dropdownItemContents.push($.spawn('a|href="' +
                                window.location.href.replace(/\/job\/[^\/]*/,'') + '/job/' + label + '"',
                                {}, "[Details]"));
                        }
                        showHideList.push($.spawn("span.dropdown-item", {}, dropdownItemContents));
                    }
                }
                // show-hide columns
                var $button = $.spawn("button#show-hide-columns.pull-right",
                    {}, ["Toggle columns", "&nbsp;", $.spawn("i.fa.fa-caret-down")]);
                $('#data-table-actionsrow').append($button);
                var $dropdown = $.spawn("div#show-hide-columns-list.dropdown-menu", {}, showHideList);
                $('#data-table-actionsrow').append($dropdown);

                // row counts
                var nres = rows.length.toString();
                var $count = $('<div class="counts">Showing <span id="table-visible-count">' + nres + '</span> of <span id="table-overall-count">' + nres + '</span> experiments</div>')
                $('table#' + tableId).after($count);

                // css
                $("head").append($.spawn("style|type='text/css'", {},
                    $.map(filterCssList, function(e){ return "tr." + e + "{display:none;}"})));

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
                        if (keyAndHeaderMap.hasOwnProperty(hdr) && columnsToShow[hdr]['show'] === 1) {
                            var key = keyAndHeaderMap[hdr];
                            label = columnsToShow[hdr]['label'];
                            labelClean = columnsToShow[hdr]['labelClean'];
                            if (hdr == 'Project') {
                                rowDataWithColumns += '<td class="' + labelClean + ' session-' + session_id + '-' + d[key] + '"><span  title="' + label + '">' + d[key] + '</span></td>';
                            } else if (key == sessionLabelKey) {
                                var url = XNAT.url.rootUrl(session_url);
                                rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + url + '"  target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';

                            } else if (key == subjectLabelKey) {
                                var url = XNAT.url.rootUrl(subject_url);
                                rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + url + '" target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';
                            } else if (key.startsWith("res_file") || key.startsWith("wrk_status_launch") || key.startsWith("wrk_status_numrows") || key.startsWith("wrk_status_lastmod") || key.startsWith("scan_type_count")) {
                                rowDataWithColumns += '<td class="' + labelClean + '" >' + d[key] + '</td>';
                            } else {
                                // d.key contains status#workflow id
                                var workFlowStatusIndx = d[key].indexOf("#");
                                var workFlowStatus = d[key].substring(0, workFlowStatusIndx);
                                var workFlowId = d[key].substring(workFlowStatusIndx + 1);
                                var fontColor = "";
                                if (isWorkflowFailed(workFlowStatus)) {
                                    fontColor = 'color="red"';
                                } else if (workFlowStatus == "Queued" || workFlowStatus == "Created") {
                                    fontColor = 'color="orange"';
                                } else if (isWorkflowComplete(workFlowStatus)) {
                                    fontColor = 'color="green"';
                                }
                                rowDataWithColumns += '<td class="' + labelClean + '">';
                                if (workFlowStatus) {
                                    workFlowStatusFirstLetterCapital = workFlowStatus.charAt(0).toUpperCase() + workFlowStatus.slice(1);
                                    rowDataWithColumns += '<span  title="' + label + '"><font ' + fontColor + '>' + workFlowStatusFirstLetterCapital + '</font></span>';
                                    rowDataWithColumns += ' 	 <span class="inline-actions">';
                                    rowDataWithColumns += '          <i class="fa fa-eye view-details" title="View Details" data-id="'+workFlowId+'"></i>';
                                    // rowDataWithColumns += '          <i class="fa fa-eye"  title="View Std Log" onclick="viewWorkflowFile('+workFlowId+',\'stdout\')"></i>';
                                    // rowDataWithColumns += '          <i class="fa fa-eye"  title="View Std Error" onclick="viewWorkflowFile('+workFlowId+',\'stderr\')"></i>';
                                    if (!isWorkflowFailed(workFlowStatus) && !isWorkflowComplete(workFlowStatus)) {
                                        rowDataWithColumns += '          <i class="fa fa-trash terminate-process" title="Terminate Process" data-id="' + workFlowId + '"></i>';
                                    }
                                    rowDataWithColumns += '     </span>';
                                    sessionWorkFlowStatus[hdr] = workFlowStatus;
                                } else {
                                    fontColor = 'color="gray"';
                                    rowDataWithColumns += '<span><font ' + fontColor + '>Ready</font></span>';
                                }
                                //console.log('Added ' + sessionLabel + ' hdr' + hdr + ' Workflow ' + workFlowStatus);
                                rowDataWithColumns += '</td>';
                            }
                        }
                    }
                    sessionPipelineWorkFlowStatus[sessionLabel] = sessionWorkFlowStatus;
                    rowDataWithColumns += '</tr>';
                    $('tbody#xnat-table-datarows-tbody').append(rowDataWithColumns);

                });
                resizeTableCols(tableId);
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

            },
            complete: function(){
                xmodal.loading.close();
            }
        });
    }

    $(document).ready(function () {
        if (window.performance) {
            console.info("window.performance works fine on this browser");
        }

        launcherTableInit();

        $(document).on('click', 'button#show-hide-columns', function () {
            var $button = $(this);
            var $dropdown = $('div#show-hide-columns-list');

            if ($dropdown.css("visibility") === "visible") {
                $button.find("i").removeClass("fa-caret-up").addClass("fa-caret-down");
                $('div#show-hide-columns-list').css({
                    visibility: "hidden",
                    transform: "translate3d(0,0,0)"
                });
            } else {
                var coords = $button.offset();
                var listcoords = $dropdown.offset();
                var leftt = coords['left'] - listcoords['left'],
                    topt = coords['top'] - listcoords['top'] + cssToNumber($button, "height");
                $(this).find("i").removeClass("fa-caret-down").addClass("fa-caret-up");
                $('div#show-hide-columns-list').css({
                    visibility: "visible",
                    transform: "translate3d(" + leftt + "px, " + topt + "px, 0)"
                });
            }
            return false;
        });
        $(document).on('click', '#show-hide-columns-list input', function () {
            toggleColumn(this.id.replace("show-", ""), $(this).prop("checked"));
            $('button#show-hide-columns').click().click(); // keep it in view, but be sure to transform if table size changes
        });
        $(document).on('click', 'button#reload', function(){
            $('#selectable-table-bulk').children().detach();
            launcherTableInit();
        });
        $(document).on('click', 'button#launch-container', function(){
            launchContainer();
        });
        $(document).on('click', '.view-details', function(){
            viewContainerDetails($(this).data("id"));
        });
        $(document).on('click', '.terminate-process', function(){
            killProcess($(this).data("id"));
        });

    });

    function cssToNumber($item, attrName) {
        var ws = $item.css(attrName) || "0";
        return Number(ws.replace(/[^\d\.]/g, ""));
    }

    function toggleColumn(target, show) {
        var $columns = $("th#th-" + target + ", td." + target).add($("input#filter-" + target).parent());
        if (show) {
            $columns.show();
        } else {
            $columns.hide();
        }
        resizeTableCols(tableId);
    }

    function resizeTableCols(table_id) {
        var $table = $("table#" + table_id);

        var $headerCells = $table.find("thead tr:not(:hidden):first").children(":not(:hidden)"),
            $filterCells = $table.find("thead tr:not(:hidden):last").children(":not(:hidden)"),
            $bodyCells = $table.find("tbody tr:not(:hidden):first").children(":not(:hidden)");

        // Set common width for thead & tbody cells (needed for scrollable tbody)
        $bodyCells.each(function (i, v) {
            var wid = Math.max(
                cssToNumber($(v), "width"),
                cssToNumber($($headerCells[i]), "width")
            );
            $(v).css("width", wid);
            $($headerCells[i]).css("width", wid);
            $($filterCells[i]).css("width", wid);
        });
    }

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
        xmodal.loading.open({title: 'Loading details...'});
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
            },
            complete: function () {
                xmodal.loading.close();
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
                    content: 'Could not get actions associated with ' + data_type_val + ' encountered ' + o,
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
            if (sessionPipelineWorkFlowStatus.hasOwnProperty(sessionId)) {
                var wrkFlowStatus = sessionPipelineWorkFlowStatus[sessionId];
                if (wrkFlowStatus && wrkFlowStatus.hasOwnProperty(pipelineName)) {
                    var status = wrkFlowStatus[pipelineName];
                    if (status && (!status.includes(failedWorkflowStatus) && status != completeWorkflowStatus)) {
                        sessionsBeingProcessed.push(sessionId);
                    }
                }
            }
        });
        return sessionsBeingProcessed;
    }
}));