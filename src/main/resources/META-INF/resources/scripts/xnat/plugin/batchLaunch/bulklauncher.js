/*
 * Copyright 2019 Radiologics, Inc
 */

var XNAT = getObject(XNAT || {});
XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.batchLaunch = getObject(XNAT.plugin.batchLaunch || {});

console.log('bulklauncher.js');

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
    XNAT.plugin.batchLaunch.launchTable = getObject(XNAT.plugin.batchLaunch.launchTable || {});

    var sessionPipelineWorkFlowStatus = {};
    var $dataRows = [];
    var $container;
    var tableId = 'xnat-table';
    var columnsToShow = {};
    // server time = client time + toServerTime
    // toServerTime = server time - client time
    var toServerTime = parseInt($('span#timezoneOffset').text()) - new Date(Date.now()).getTimezoneOffset()*60*1000*-1;

    // Similar to table.js, but no way to use it from there
    function cacheRows(){
        if (!$dataRows.length) {
            $dataRows = $('table#' + tableId + " tbody tr");
        }
        return $dataRows;
    }

    function filterRows(val, name){
        if (!val) { return; }
        val = val.toLowerCase();
        var filterClass = 'filter-' + name;
        // cache the rows if not cached yet
        cacheRows();
        $dataRows.addClass(filterClass).filter(function(){
            return $(this).find('td.' + name).containsNC(val).length;
        }).removeClass(filterClass);
    }

    function updateAfterFiltering($table) {
        XNAT.plugin.batchLaunch.resizeTableCols($table);
        setStateSelectAllToggle($('.selectable-select-all'));
        $("span#table-visible-count").text($('#' + tableId + ' tbody tr:not(:hidden)').length);
    }

    function exportTableToCSV($table, filename) {
        //https://stackoverflow.com/questions/7161113/how-do-i-export-html-table-data-as-csv-file
        var $rows = $table.find('tr:not(#xnat-table-header-row2):not(:hidden)'),

        // Temporary delimiter characters unlikely to be typed by keyboard
        // This is to avoid accidentally splitting the actual contents
        tmpColDelim = String.fromCharCode(11), // vertical tab character
        tmpRowDelim = String.fromCharCode(0), // null character

        // actual delimiter characters for CSV format
        colDelim = '","',
        rowDelim = '"\r\n"',

        // Grab text from table into CSV formatted string
        csv = '"' + $rows.map(function (i, row) {
            var $row = $(row), $cols = $row.find('td:not(.session-selector),th:not(.toggle-all)');

            return $cols.map(function (j, col) {
                var $col = $(col), text = $col.text();

                return text.replace(/ +/,'').replace(/"/g, '""'); // escape double quotes, whitespace

            }).get().join(tmpColDelim);

        }).get().join(tmpRowDelim)
            .split(tmpRowDelim).join(rowDelim)
            .split(tmpColDelim).join(colDelim) + '"';

        // Data URI
        //var csvData = 'data:application/csv;charset=utf-8,' + encodeURIComponent(csv);
        var blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });

        if (window.navigator.msSaveBlob) { // IE 10+
            //alert('IE' + csv);
            //window.navigator.msSaveOrOpenBlob(new Blob([csv], {type: "text/plain;charset=utf-8;"}), filename);
            window.navigator.msSaveOrOpenBlob(blob, filename);
        } else {
            // DOESNT WORK ON FIREFOX within XNAT, does seem to work in FF from other sites
            // Used to "hijack" this links's href, but then couldn't revoke object URL
            // so now we use a button and create a link
            //$(this).attr({'download': filename, 'href': csvData});
            //$(this).attr({'download': filename, 'href', URL.createObjectURL(blob)});
            var a = document.createElement('a');
            var url = URL.createObjectURL(blob);
            a.style.display = 'none';
            a.href = url;
            a.setAttribute('download', filename);
            if (typeof a.download === 'undefined') {
                a.setAttribute('target', '_blank');
            }
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);

            setTimeout(function() {
                // For Firefox it is necessary to delay revoking the ObjectURL
                window.URL.revokeObjectURL(url);
                }, 100);
        }
    }

    XNAT.plugin.batchLaunch.launchTable.init = function(reload) {
        var waitDialog = XNAT.ui.dialog.static.wait('Loading processing data...');
        $dataRows = [];
        $container = $('#selectable-table-bulk');

        var xml = $('#xss').val();
        var subjectLabelKey = "";
        var sessionLabelKey = "";
        var currentJob = $('span#currentJob').text();

        XNAT.xhr.post({
            url: XNAT.url.csrfUrl('REST/search?format=json'),
            data: xml,
            success: function (responseData) {
                var keyAndHeaderMap = {};

                //Add table
                var divContent = '<div class="data-table-titlerow">							';
                divContent += '	    <h3 class="data-table-title">Select experiments to launch processing</h3>		';
                divContent += '	    <div class="data-table-actionsrow clearfix">							';
                divContent += '	        <span  class="textlink-sm data-table-action">					';
                divContent += '	          <select id="actionsDropdown" class="data-table-action disabled"  disabled>	';
                divContent += '	          </select>									';
                divContent += '	        </span>										';
                divContent += ' 	    <button class="btn btn-sm data-table-action disabled" id="launch-container">' +
                    'Launch job</button>	';
                divContent += '		    <button class="btn btn-sm text-error data-table-action disabled" id="kill-container">' +
                    '<strong>Terminate job</strong></button>				';
                divContent += '		    <button class="btn btn-sm" type="submit" id="reload">Reload</button>';
                divContent += '		    <button class="btn btn-sm" id="download">Download csv</button>				';

                if (currentJob) {
                    divContent += '<a class="btn btn-sm" href="#" ' +
                        'onclick="XNAT.plugin.batchLaunch.launchTable.showAllJobsBtnAction()">Show all jobs</a>';
                }

                divContent += '	    </div>													';
                divContent += '    <span class="clear clearfix"></span>									';
                divContent += '	</div>														';
                divContent += '	<div class="data-table-wrapper" id="div-'+tableId+'-header">';
                divContent += '	       <table id="'+tableId+'" class="clean fixed-header selectable scrollable-table data-table xnat-table" style="width: auto;">';
                divContent += '	            <thead>												';
                divContent += '		            <tr id="xnat-table-header-row1">								';
                divContent += '		                <th class="toggle-all" style="width: 45px;">						';
                divContent += '		                    <input type="checkbox" class="selectable-select-all" ' +
                    'id="toggle-all-sessions" title="Toggle All Sessions" />	';
                divContent += '		                </th>															';
                divContent += '		            </tr>															';
                divContent += '		            <tr id="xnat-table-header-row2">								';
                divContent += '		                <td style="width: 45px;"></td>						        ';
                divContent += '		            </tr>															';
                divContent += '	            </thead>																';
                divContent += '	            <tbody id="xnat-table-datarows-tbody" style="height: 500px;">									';
                divContent += '             </tbody>																';
                divContent += '	        </table>																';
                divContent += '	</div>																		';
                $container.append(divContent);

                var dataType;
                XNAT.plugin.batchLaunch.dataType = dataType = responseData.ResultSet.rootElementName;
                $.each(responseData.ResultSet.Columns, function (i, d) {
                    var header, label;
                    header = label = d.header;
                    if (header) {
                        var showColumn = true;
                        if (header == "Scans" || header == "Age" || header == "Date" ||
                            header == "Scanner" || header == "Type" || header == "M/F") {
                            showColumn = false;
                        }
                        if (d.key.startsWith('xnat_subjectdata_sub_project_identifier') > 0 ||
                            d.key == "xnat_subjectdata_subjectid" || d.key == "xnat_subjectdata_subject_label") {
                            subjectLabelKey = d.key;
                        } else if (d.key.indexOf('_project_identifier_') > 0 || d.key == "session_id") {
                            sessionLabelKey = d.key;
                        } else if (d.key == "label") {
                            sessionLabelKey = d.key;
                        }
                        if (showColumn) {
                            keyAndHeaderMap[header] = d.key;
                            columnsToShow[header] = {
                                show: 0,
                                label: label,
                                labelClean: label.replace(' ','-'),
                                type: d.type,
                                pipeline: d.key.startsWith("wrk_status") && d.type === "string" &&
                                    d.xPATH.replace(/[^.]*./,'') === "WRK_STATUS" //Hack
                            };
                        }
                    }
                });

                columnsToShow['Project']['show'] = 0; //Only called on given project, so no reason to display this column
                columnsToShow['Session']['show'] = 1;
                columnsToShow['Subject']['show'] = 1;

                var rows = responseData.ResultSet.Result;

                // Dont add columns for workflows which have not been executed for the project at all
                for (var wrk_col in columnsToShow) {
                    if (columnsToShow[wrk_col]['show'] === 1) {
                        continue;
                    }
                    $.each(rows, function (i, d) {
                        if (columnsToShow[wrk_col]['show'] === 0) {
                            if (keyAndHeaderMap.hasOwnProperty(wrk_col)) {
                                var key = keyAndHeaderMap[wrk_col];
                                if (wrk_col != 'Project' && key != sessionLabelKey && key != subjectLabelKey && d[key]) {
                                    var workFlowStatusIndx = d[key].indexOf("#");
                                    var workFlowStatus = d[key].substring(0, workFlowStatusIndx);
                                    if (workFlowStatus || key.startsWith("res_file") || key.startsWith("wrk_status_launch")
                                        || key.startsWith("wrk_status_numrows") || key.startsWith("wrk_status_lastmod")
                                        || key.startsWith("scan_type_count")) {
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
                var filterIdPrefix = 'filter-by-';
                var filterClassPrefix = 'filter-';
                for (var header_col in columnsToShow) {
                    if (columnsToShow[header_col]['show'] === 1) {
                        label = columnsToShow[header_col]['label'];
                        labelClean = columnsToShow[header_col]['labelClean'];
                        var filterId = filterIdPrefix + labelClean;
                        var filterClass = filterClassPrefix + labelClean;
                        filterCssList.push(filterClass);
                        $('tr#xnat-table-header-row1').append($('<th class="left sort '+ labelClean +
                            '" style="word-wrap:break-word;">' + label + '<i class="arrows">&nbsp;</i></th>'));
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
                                    id: filterId,
                                    on: {
                                        change: function () {
                                            var filterId = this.id;
                                            var filterClass = filterId.replace(filterIdPrefix,filterClassPrefix);
                                            var colClass = filterId.replace(filterIdPrefix,'');
                                            var selectedValue = parseInt(this.value, 10);
                                            if (selectedValue === 0) {
                                                $dataRows.removeClass(filterClass);
                                            } else {
                                                cacheRows();
                                                var currentTimeServer = Date.now() + toServerTime;
                                                $dataRows.addClass(filterClass).filter(function () {
                                                    var timestamp = $(this).find('td.' + colClass).text(), date;
                                                    return timestamp && (date = new Date(timestamp)) &&
                                                        (selectedValue === date - 1 || selectedValue > (currentTimeServer - date));
                                                }).removeClass(filterClass);
                                            }
                                            updateAfterFiltering($(this).parents("table"));
                                        }
                                    }
                                }
                            }).element]);
                        } else {
                            $filterInput = $.spawn('input#' + filterId + '.filter-data', {
                                type: 'text',
                                title: labelClean + ':filter',
                                placeholder: 'Filter...',
                                style: 'width: 90%;'
                            });
                            $filterInput.on('keyup', function(e){
                                var filterId = this.id;
                                var colClass = filterId.replace(filterIdPrefix,'');
                                var filterClass = filterId.replace(filterIdPrefix,filterClassPrefix);
                                var val = this.value;
                                var key = e.which;
                                // don't do anything on 'tab' keyup
                                if (key === 9) return false;
                                if (key === 27){ // key 27 = 'esc'
                                    this.value = val = '';
                                }
                                if (!val || key === 8) {
                                    $dataRows.removeClass(filterClass);
                                }
                                filterRows(val, colClass);
                                updateAfterFiltering($(this).parents("table"));
                            });
                        }
                        $('tr#xnat-table-header-row2').append($("<td class='"+labelClean+"'></td>").append($filterInput));

                        //Toggle columns checkbox list
                        var dropdownItemContents = XNAT.plugin.batchLaunch.addColumnToggleContents(labelClean, label, true);
                        if (!currentJob && columnsToShow[header_col]['pipeline']) {
                            dropdownItemContents.push("&nbsp;");
                            dropdownItemContents.push($.spawn('a', {
                                id: label,
                                onclick: function() {
                                    var url = window.location.pathname;
                                    var job = $(this).prop('id');
                                    if (url.indexOf('BulkLaunchAction') > -1) {
                                        XNAT.plugin.batchLaunch.fakeFormPost(url, {
                                            job: job,
                                            search_xml: $('#xss').val()
                                        });
                                    } else {
                                        window.location.href = window.location.href.replace(/\/job\/[^\/]*/,'') +
                                        '/job/' + job;
                                    }
                                }
                            }, '[More details]'));
                        }
                        showHideList.push($.spawn("span.bl-dropdown-item", {}, dropdownItemContents));
                    }
                }

                // Toggle columns
                XNAT.plugin.batchLaunch.addColumnToggle(showHideList, $container);

                // Row counts
                var nres = rows.length.toString();
                var $count = $('<div class="counts">Showing <span id="table-visible-count">' +
                    nres + '</span> of <span id="table-overall-count">' + nres + '</span> experiments</div>');
                $('table#' + tableId).after($count);

                // Css for filtering
                $container.prepend($.spawn("style|type='text/css'", {},
                    $.map(filterCssList, function(e){ return "tr." + e + "{display:none;}"})));

                // AddDataTableRows:
                var multipleProjects = false;
                $.each(rows, function (i, d) {
                    var sessionId = d.session_id || d.expt_id;
                    var subjectId = d.xnat_subjectdata_subjectid;
                    var sessionProject = d.project;
                    var sessionLabel = d[sessionLabelKey];
                    var sessionWorkFlowStatus = {};

                    if (!multipleProjects) {
                        if (XNAT.plugin.batchLaunch.projectId && d.project !== XNAT.plugin.batchLaunch.projectId) {
                            multipleProjects = true;
                            XNAT.plugin.batchLaunch.projectId = '';
                        } else {
                            XNAT.plugin.batchLaunch.projectId = sessionProject;
                        }
                    }

                    var single_select_checkbox_id = "select-" + sessionId;
                    // var id_json = '{&quot;accession-id&quot;:&quot;' + session_id + '&quot;,&quot;label&quot;:&quot;' +
                    //     sessionLabel + '&quot;,&quot;project&quot;:&quot;' +
                    //     session_project + '&quot;,&quot;xsiType&quot;:&quot;' + dataType + '&quot;}';
                    var id_json = JSON.stringify({
                        uri: '/archive/experiments/' + sessionId,
                        id: sessionId,
                        label: sessionLabel,
                        project: sessionProject,
                        xsiType: dataType
                    });
                    var session_url = 'app/action/DisplayItemAction/search_element/' + dataType + '/search_field/' +
                        dataType + '.ID/search_value/' + sessionId + '/popup/$popup';
                    var subject_url = 'app/action/DisplayItemAction/search_element/xnat:subjectData/search_field/' +
                        'xnat:subjectData.ID/search_value/' + subjectId + '/popup/$popup';

                    var rowDataWithColumns = '<tr valign="top" id="session-' + sessionId + '">';
                    rowDataWithColumns += '<td class="session-actions-controls session-selector center" ' +
                        'style="width: 45px;">';
                    var inputCk = spawn('input', {
                        type: "checkbox",
                        className: "selectable-select-one",
                        id: single_select_checkbox_id,
                        value: JSON.stringify({
                            uri: '/archive/experiments/' + sessionId,
                            id: sessionId,
                            label: sessionLabel,
                            project: sessionProject,
                            xsiType: dataType
                        })
                    });
                    rowDataWithColumns += inputCk.html;
                    rowDataWithColumns += '</td>';

                    for (var hdr in columnsToShow) {
                        if (keyAndHeaderMap.hasOwnProperty(hdr) && columnsToShow[hdr]['show'] === 1) {
                            var key = keyAndHeaderMap[hdr];
                            label = columnsToShow[hdr]['label'];
                            labelClean = columnsToShow[hdr]['labelClean'];
                            if (hdr === 'Project') {
                                rowDataWithColumns += '<td class="' + labelClean + ' session-' + sessionId + '-' +
                                    d[key] + '"><span  title="' + label + '">' + d[key] + '</span></td>';
                            } else if (key === sessionLabelKey) {
                                var url = XNAT.url.rootUrl(session_url);
                                rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + url +
                                    '"  target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';

                            } else if (key === subjectLabelKey) {
                                var url = XNAT.url.rootUrl(subject_url);
                                rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + url +
                                    '" target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';
                            } else if (key.startsWith("res_file") || key.startsWith("wrk_status_launch")
                                || key.startsWith("wrk_status_numrows") || key.startsWith("wrk_status_lastmod")
                                || key.startsWith("scan_type_count")) {
                                rowDataWithColumns += '<td class="' + labelClean + '" >' + d[key] + '</td>';
                            } else {
                                // d.key contains status#workflow id
                                var entryMap = {};
                                var workFlowStatusIndx = d[key].indexOf("#");
                                var workFlowStatus = d[key].substring(0, workFlowStatusIndx);
                                sessionWorkFlowStatus[hdr] = workFlowStatus; //Needs to be empty if no status yet
                                workFlowStatus = workFlowStatus || "Ready";
                                var workFlowId = d[key].substring(workFlowStatusIndx + 1);
                                var containerId = d[key.replace("wrk_status", "wrk_status_cid")];
                                entryMap['wfid'] = workFlowId;
                                entryMap['status'] = workFlowStatus;
                                entryMap['comments'] = containerId;
                                entryMap['justification'] = (containerId) ? "Container launch" : "";
                                rowDataWithColumns += '<td class="' + labelClean + '">';
                                rowDataWithColumns += XNAT.plugin.batchLaunch.spawnStatusCell(workFlowStatus).html;
                                rowDataWithColumns += XNAT.plugin.batchLaunch.spawnInlineActions(entryMap).html;
                                rowDataWithColumns += '</td>';

                            }
                        }
                    }
                    sessionPipelineWorkFlowStatus[sessionLabel] = sessionWorkFlowStatus;
                    rowDataWithColumns += '</tr>';
                    $('tbody#xnat-table-datarows-tbody').append(rowDataWithColumns);

                });
                if (reload) {
                    // Clear cached history info
                    XNAT.plugin.batchLaunch.containerInfo = {};
                } else {
                    addActions();
                }
                XNAT.plugin.batchLaunch.resizeTableCols($container.find("table#" + tableId));
                populateBreadCrumbs();
                // Now get the actions associated with the datatype
                renderActionOptions();
            },
            error: function (o) {
                XNAT.dialog.open({
                    title: 'Error',
                    content: 'Could not GET the search results: ' + o.responseText,
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
                waitDialog.close();
            }
        });
    };

    function addActions() {
        // Since $container is not destroyed on reload, we don't want to re-run this
        $container.on('click', 'button#download', function(){
            exportTableToCSV($('table#' + tableId), "processing_data.csv");
            return false;
        });
        $container.on('click', 'button#reload', function(){
            $container.empty();
            XNAT.plugin.batchLaunch.launchTable.init(true);
        });
        $container.on('click', 'button#launch-container', function(){
            launchContainer();
        });
        $container.on('click', 'button#kill-container', function(){
            killContainer();
        });
        XNAT.plugin.batchLaunch.addClickActions($container);
    }

    function populateBreadCrumbs() {
        var projectId = XNAT.plugin.batchLaunch.projectId;
        if (!projectId) return;

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
        var $actionsDropdown = $('#actionsDropdown');
        $actionsDropdown
            .find('option')
            .remove()
            .end()
            .append('<option value="Select" selected="true">Select job</option>');
        var data = {xsiType: XNAT.plugin.batchLaunch.dataType};
        var url = '/xapi/commands/available';
        if (XNAT.plugin.batchLaunch.projectId) {
            data['project'] = XNAT.plugin.batchLaunch.projectId;
        } else {
            url += '/site';
        }
        var loadingDialog = XNAT.ui.dialog.loading;
        loadingDialog.open();
        XNAT.xhr.getJSON({
            url: XNAT.url.rootUrl(url),
            data: data,
            success: function (responseData) {
                loadingDialog.close();
                responseData.forEach(function (availableCommand) {
                    var pipelineName = availableCommand['wrapper-name'];
                    if (availableCommand.enabled) {
                        $('#actionsDropdown').append(spawn('option', {
                            value: JSON.stringify({
                                'root-element-name': availableCommand['root-element-name'],
                                'wrapper-id': availableCommand['wrapper-id'],
                                'command-id': availableCommand['command-id'],
                                'wrapper-name': availableCommand['wrapper-name']
                            })
                        }, pipelineName).html);
                    } else {
                        var info = columnsToShow[pipelineName];
                        var currentJob = $('span#currentJob').text();
                        if (info && info['show']===1 && !currentJob) {
                            //Hide this column
                            //$('.show-hide-columns-list input#show-' + info['labelClean']).prop("checked", false);
                            //XNAT.plugin.batchLaunch.toggleColumn(info['labelClean'], false);
                            $('.show-hide-columns-list input#show-' + info['labelClean']).click();
                        }
                    }
                });
                $actionsDropdown.removeClass('disabled');
                $actionsDropdown.prop("disabled", false);
            },
            error: function (o) {
                loadingDialog.close();
                XNAT.dialog.open({
                    title: 'Error',
                    content: 'Could not get actions associated with ' + XNAT.plugin.batchLaunch.dataType + ': '
                        + o.responseText,
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

		//Load site wide pipelines for the datatype
		XNAT.xhr.getJSON({
            url: XNAT.url.rootUrl('/xapi/pipelines/site?xsiType='+XNAT.plugin.batchLaunch.dataType),
	    success: function(responseData) {
			responseData.ResultSet.Result.forEach(function(configuredPipeline) {
	        		   var pipelineName = configuredPipeline['Name'];
	        		   console.log("Adding " + pipelineName);
	        		   $('#actionsDropdown').append(spawn('option', {
			                            value: JSON.stringify({
			                                'pipeline_name': pipelineName,
			                                'pipeline_path': configuredPipeline['Path']
			                            })
                        }, pipelineName).html);
	        });
	    },
	    error : function(o) {
			console.log("Encouneterd error " + o);
		    XNAT.dialog.open({
	    		    title: 'Error',
	    		    content: 'Could not get pipelines for data type: ' + o,
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
    }

    function getSelectedExperiments() {
        var sel = {targets: [], targetLabels: []};
        $('input.selectable-select-one:checkbox:checked').each(function() {
            // Get the JSON
            var jsonData = JSON.parse($(this).val());
            sel['targets'].push(jsonData['uri']);
            sel['targetLabels'].push(jsonData['label']);
        });
        return sel;
    }

    function getSelectedContainer() {
        var commandDetails = $('#actionsDropdown').find(":selected").val();
        console.log("CommandDetails: " + commandDetails);
        if (commandDetails === "Select") {
            XNAT.dialog.open({
                title: 'Please select a container',
                content: 'You must select a container first',
                width: 400,
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });
            return undefined;
        }
        return JSON.parse(commandDetails);
    }

	function killContainer() {
        var commandDetailsJsonObj = getSelectedContainer();
        if (!commandDetailsJsonObj) return false;

		if (pipelineIsSelected(commandDetailsJsonObj)) {
			killPipelineJob(commandDetailsJsonObj);
		}else {
			killXnatContainer(commandDetailsJsonObj);
		}
	}

    function killPipelineJob(commandDetailsJsonObj) {
        var projectId = XNAT.plugin.batchLaunch.projectId;
        var pipelineName = commandDetailsJsonObj['pipeline_name'];
	    var pipelinePath = commandDetailsJsonObj['pipeline_path'];

        // Experiments
        var sel = getSelectedExperiments();
        var targets = sel['targets'], targetLabels = sel['targetLabels'];
	    var dataToPost = {};
	    dataToPost['Experiments']=JSON.stringify(targets);
	    dataToPost['pipelinePath']=pipelinePath;
        XNAT.ui.dialog.open({
            title: 'Terminate process confirmation',
            content: spawn('div', {}, [
                spawn('p', {}, 'Are you SURE you want to terminate the <strong>' + pipelineName +
                    '</strong> job for the following <strong>' + targets.length +
                    '</strong> experiments?'),
                spawn('p', {}, '<em>Note: REVIEW THEM, this cannot be undone!</em>'),
                spawn('ul', {}, $.map(targetLabels, function(e){return spawn('li', {}, e);}))
            ]),
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
                    action: function() {
                        if (!targets || targets.length === 0) return false;
                        $.post({
                            beforeSend: function() {
                                XNAT.ui.dialog.alert("Jobs are being terminated in the background. " +
                                    "You may continue to work, refreshing the dashboard to see updated progress.");
                                return true;
                            },
                            url: XNAT.url.restUrl('/xapi/pipelines/terminate/'+pipelineName+'/project/'+projectId),
                            data: JSON.stringify(dataToPost),
                            contentType:"application/json; charset=utf-8",
                            success: function(data){
                                var messageContent = [],
                                    totalAttempts = data.successes.concat(data.failures).length,
                                    successMsg = 'successfully queued to be terminated. If statuses don\'t update ' +
                                        'shortly, your admin will need to review the logs to determine what went wrong.';
                                if (data.failures.length > 0) {
                                    messageContent.push( spawn('div.message', data.successes.length + ' of ' +
                                        totalAttempts + ' containers ') );
                                } else if(data.successes.length > 0) {
                                    messageContent.push( spawn('div.success','All containers ' + successMsg) );
                                } else {
                                    messageContent.push( spawn('div.warning','No containers terminated.'));
                                }

                                if (data.failures.length > 0){
                                    messageContent.push( spawn('h3',{'style': {'margin-top': '2em' }}, 'Failed termination attempts') );
                                    data.failures.forEach(function(failure){
                                        messageContent.push( spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error message:') );
                                        messageContent.push( spawn('pre.json', failure) );
                                    });
                                }

                                XNAT.ui.dialog.open({
                                    title: 'Job termination report',
                                    content: spawn('div', messageContent ),
                                    buttons: [
                                        {
                                            label: 'OK',
                                            isDefault: true,
                                            close: XNAT.ui.dialog.closeAll()
                                        }
                                    ]
                                });
                            },
                            error: function(e) {
                                XNAT.ui.dialog.open({
                                    title: 'Job termination failed',
                                    content: spawn("p", {}, e.status + " error: " + e.responseText),
                                    buttons: [
                                        {
                                            label: 'OK',
                                            isDefault: true,
                                            close: XNAT.ui.dialog.closeAll()
                                        }
                                    ]
                                });
                            }
                        });
                    }
                }
            ]
        });
	}

    function killXnatContainer(commandDetailsJsonObj) {
        var pipelineName = commandDetailsJsonObj['wrapper-name'];

        var sel = getSelectedExperiments();
        var targets = sel['targets'], targetLabels = sel['targetLabels'];

        XNAT.ui.dialog.open({
            title: 'Terminate process confirmation',
            content: spawn('div', {}, [
                spawn('p', {}, 'Are you SURE you want to terminate the <strong>' + pipelineName +
                    '</strong> container for the following <strong>' + targets.length +
                    '</strong> experiments?'),
                spawn('p', {}, '<em>Note: REVIEW THEM, this cannot be undone!</em>'),
                spawn('ul', {}, $.map(targetLabels, function(e){return spawn('li', {}, e);}))
            ]),
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
                    action: function() {
                        if (!targets || targets.length === 0) return false;

                        // Experiments
                        var cannotTerminate = checkSelectedSessionsForTermination(targetLabels, pipelineName);
                        if (cannotTerminate && cannotTerminate.length > 0) {
                            var sessionList = "";
                            cannotTerminate.forEach(function (sessionId) {
                                sessionList += "<p>" + sessionId + "</p>";
                            });
                            XNAT.dialog.open({
                                title: 'Error',
                                content: 'Containers for the following session(s) are not in a state that can be terminated:<br/><br/>' +
                                    sessionList + 'Please exclude them and try again.',
                                width: 400,
                                buttons: [
                                    {
                                        label: 'OK',
                                        isDefault: true,
                                        close: true
                                    }
                                ]
                            });
                            return false;
                        }

                        $.post({
                            beforeSend: function() {
                                XNAT.ui.dialog.alert("Containers are being terminated in the background. " +
                                    "You may continue to work, refreshing the dashboard to see updated progress.");
                                return true;
                            },
                            url: XNAT.url.restUrl('/xapi/workflows/'+pipelineName+'/killactive'),
                            data: {'experiments': targets},
                            dataType : 'json',
                            success: function(data){
                                var messageContent = [],
                                    totalAttempts = data.successes.concat(data.failures).length,
                                    successMsg = 'successfully queued to be terminated. If statuses don\'t update ' +
                                        'shortly, your admin will need to review the logs to determine what went wrong.';
                                if (data.failures.length > 0) {
                                    messageContent.push( spawn('div.message', data.successes.length + ' of ' +
                                        totalAttempts + ' containers queued to be terminated') );
                                } else if(data.successes.length > 0) {
                                    messageContent.push( spawn('div.success','All containers ' + successMsg) );
                                } else {
                                    messageContent.push( spawn('div.warning','No containers terminated.'));
                                }

                                if (data.failures.length > 0){
                                    messageContent.push( spawn('h3',{'style': {'margin-top': '2em' }}, 'Failed termination attempts') );
                                    data.failures.forEach(function(failure){
                                        messageContent.push( spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error message:') );
                                        messageContent.push( spawn('pre.json', failure) );
                                    });
                                }

                                XNAT.ui.dialog.open({
                                    title: 'Container termination report',
                                    content: spawn('div', messageContent ),
                                    buttons: [
                                        {
                                            label: 'OK',
                                            isDefault: true,
                                            close: XNAT.ui.dialog.closeAll()
                                        }
                                    ]
                                });
                            },
                            error: function(e) {
                                XNAT.ui.dialog.open({
                                    title: 'Container termination failed',
                                    content: spawn("p", {}, e.status + " error: " + e.responseText),
                                    buttons: [
                                        {
                                            label: 'OK',
                                            isDefault: true,
                                            close: XNAT.ui.dialog.closeAll()
                                        }
                                    ]
                                });
                            }
                        });
                    }
                }
            ]
        });
    }

    function pipelineIsSelected(commandDetailsJsonObj) {
        var pipelineName = commandDetailsJsonObj['pipeline_name'];
        var pipelinePath = commandDetailsJsonObj['pipeline_path'];
		if (pipelineName == null && pipelinePath == null) {
			return false;
		}else if (pipelineName != null && pipelinePath != null) {
			return true;
		}else {
		    return false;
		}
	}

    function launchXnatContainer(commandDetailsJsonObj) {
       if (!commandDetailsJsonObj) return false;
        // Experiments
        var sel = getSelectedExperiments();
        var targets = sel['targets'], targetLabels = sel['targetLabels'];

        var projectId = XNAT.plugin.batchLaunch.projectId;
        var rootElementName = commandDetailsJsonObj['root-element-name'];
        var wrapperId = commandDetailsJsonObj['wrapper-id'];
        var commandId = commandDetailsJsonObj['command-id'];
        var pipelineName = commandDetailsJsonObj['wrapper-name'];

        //Are there any sessions in the selected list which are in any state other than Failed or Complete?
        //If this change the selected sessions
        var sessionsBeingProcessed = checkSelectedSessionsForLaunch(targetLabels, pipelineName);
        if (sessionsBeingProcessed && sessionsBeingProcessed.length > 0) {
            var sessionList = "";
            sessionsBeingProcessed.forEach(function (sessionId) {
                sessionList += "<p>" + sessionId + "</p>";
            });
            XNAT.dialog.open({
                title: 'Error',
                content: 'The following sessions cannot be launched because they are already actively running this job:<br/><br/>'
                    + sessionList + 'Please exclude them and relaunch.',
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
            XNAT.plugin.containerService.launcher.bulkLaunchDialog(wrapperId,
                rootElementName, targets, targetLabels, projectId, commandId);
        }
	}

    function launchXnatPipeline(commandDetailsJsonObj) {
       if (!commandDetailsJsonObj) return false;
        // Experiments
        var sel = getSelectedExperiments();
        var targets = sel['targets'], targetLabels = sel['targetLabels'];

        var projectId = XNAT.plugin.batchLaunch.projectId;
        var pipelineName = commandDetailsJsonObj['pipeline_name'];
        var pipelinePath = commandDetailsJsonObj['pipeline_path'];

        //Are there any sessions in the selected list which are in any state other than Failed or Complete?
        //If this change the selected sessions
        var sessionsBeingProcessed = checkSelectedSessionsForLaunch(targetLabels, pipelineName);
        if (sessionsBeingProcessed && sessionsBeingProcessed.length > 0) {
            var sessionList = "";
            sessionsBeingProcessed.forEach(function (sessionId) {
                sessionList += "<p>" + sessionId + "</p>";
            });
            XNAT.dialog.open({
                title: 'Error',
                content: 'The following sessions cannot be launched because they are already actively running this job:<br/><br/>'
                    + sessionList + 'Please exclude them and relaunch.',
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
            XNAT.plugin.pipelineLaunchService.launcher.bulkLaunchPipelineDialog(pipelineName, targets, targetLabels, projectId);
        }
	}


    function launchContainer() {
		//At this point we decide if the user selected a container or a pipeline to launch
        // Container
        var commandDetailsJsonObj = getSelectedContainer();
        if (pipelineIsSelected(commandDetailsJsonObj)) {
			launchXnatPipeline(commandDetailsJsonObj)
		}else {
			launchXnatContainer(commandDetailsJsonObj);
		}
     }

    function checkSelectedSessionsForLaunch(targets, pipelineName) {
        var sessionsBeingProcessed = [];
        targets.forEach(function (sessionId) {
            if (sessionPipelineWorkFlowStatus.hasOwnProperty(sessionId)) {
                var wrkFlowStatus = sessionPipelineWorkFlowStatus[sessionId];
                if (wrkFlowStatus && wrkFlowStatus.hasOwnProperty(pipelineName)) {
                    var status = wrkFlowStatus[pipelineName];
                    if (status && !XNAT.plugin.batchLaunch.isWorkflowFailed(status)
                        && !XNAT.plugin.batchLaunch.isWorkflowComplete(status)) {
                        sessionsBeingProcessed.push(sessionId);
                    }
                }
            }
        });
        return sessionsBeingProcessed;
    }

    function checkSelectedSessionsForTermination(targets, pipelineName) {
        var interminableSessions = [];
        targets.forEach(function (sessionId) {
            if (sessionPipelineWorkFlowStatus.hasOwnProperty(sessionId)) {
                var wrkFlowStatus = sessionPipelineWorkFlowStatus[sessionId];
                if (wrkFlowStatus && wrkFlowStatus.hasOwnProperty(pipelineName)) {
                    var status = wrkFlowStatus[pipelineName];
                    if (status && !XNAT.plugin.batchLaunch.canTerminateWorkflow(status)) {
                        interminableSessions.push(sessionId);
                    }
                }
            }
        });
        return interminableSessions;
    }

    XNAT.plugin.batchLaunch.launchTable.showAllJobsBtnAction = function() {
        var url = window.location.pathname;
        if (url.indexOf('BulkLaunchAction') > -1) {
            XNAT.plugin.batchLaunch.fakeFormPost(url, {
                search_xml: $('#xss').val()
            });
        } else {
            window.location.href = window.location.href.replace(/\/job\/[^\/]*/,'');
        }
    };

    XNAT.plugin.batchLaunch.fakeFormPost = function(url, fields) {
        var $form = $('<form>', {
            action: XNAT.url.csrfUrl(url),
            method: 'post'
        });
        $.each(fields, function(key, val) {
            $('<input>').attr({
                type: "hidden",
                name: key,
                value: val
            }).appendTo($form);
        });
        $form.appendTo('body').submit();
    }
}));

