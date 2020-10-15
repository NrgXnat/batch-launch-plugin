/*
 */

var XNAT = getObject(XNAT || {});
XNAT.app = getObject(XNAT.app || {});
XNAT.app.displayNames = getObject(XNAT.app.displayNames || {});
XNAT.app.displayNames.singular = getObject(XNAT.app.displayNames.singular || {});
XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.batchLaunch = getObject(XNAT.plugin.batchLaunch || {});

console.log('bulklauncher.js');

(function (factory) {
    if (typeof define === 'function' && define.amd) {
        define(factory);
    } else if (typeof exports === 'object') {
        module.exports = factory();
    } else {
        return factory();
    }
}(function () {
    XNAT.plugin.batchLaunch.launchTable = getObject(XNAT.plugin.batchLaunch.launchTable || {});

    var pipelineWorkFlowStatus = {};
    var $dataRows = [];
    var $container;
    var tableId = 'xnat-table';
    var columnsToShow = {};
    // server time = client time + toServerTime
    // toServerTime = server time - client time
    var toServerTime = parseInt($('span#timezoneOffset').text()) - new Date(Date.now()).getTimezoneOffset() * 60 * 1000 * -1;

    var url = window.location.pathname;
    var searchForm = url.indexOf('BulkLaunchAction') > -1;

    // Similar to table.js, but no way to use it from there
    function cacheRows() {
        if (!$dataRows.length) {
            $dataRows = $('table#' + tableId + " tbody tr");
        }
        return $dataRows;
    }

    function filterRows(val, name) {
        if (!val) {
            return;
        }
        val = val.toLowerCase();
        var filterClass = 'filter-' + name;
        // cache the rows if not cached yet
        cacheRows();
        $dataRows.addClass(filterClass).filter(function () {
            return $(this).find('td.' + name).containsNC(val).length;
        }).removeClass(filterClass);
    }

    function updateAfterFiltering($table) {
        XNAT.ui.ajaxTable.resizeTableCols($table);
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
                var $row = $(row), $cols = $row.find('td:not(.element-selector):not(:hidden),' +
                    'th:not(.toggle-all):not(:hidden)');

                return $cols.map(function (j, col) {
                    var $col = $(col), text = $col.text();

                    return text.replace(/ +/, '').replace(/"/g, '""'); // escape double quotes, whitespace

                }).get().join(tmpColDelim);

            }).get().join(tmpRowDelim)
                .split(tmpRowDelim).join(rowDelim)
                .split(tmpColDelim).join(colDelim) + '"';

        // Data URI
        //var csvData = 'data:application/csv;charset=utf-8,' + encodeURIComponent(csv);
        var blob = new Blob([csv], {type: 'text/csv;charset=utf-8;'});

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
            var objUrl = URL.createObjectURL(blob);
            a.style.display = 'none';
            a.href = objUrl;
            a.setAttribute('download', filename);
            if (typeof a.download === 'undefined') {
                a.setAttribute('target', '_blank');
            }
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);

            setTimeout(function () {
                // For Firefox it is necessary to delay revoking the ObjectURL
                window.URL.revokeObjectURL(objUrl);
            }, 100);
        }
    }

    XNAT.plugin.batchLaunch.launchTable.init = function (reload) {
        var waitDialog = XNAT.ui.dialog.static.wait('Loading processing data...');
        $dataRows = [];
        $container = $('#selectable-table-bulk');

        XNAT.plugin.batchLaunch.projectId = $('#projectId').text();
        var xml = $('#xss').val();
        var projectLabelKey = "id";
        var subjectLabelKey = "";
        var experimentLabelKey = "";
        var scanLabelKey = "id";
        var currentJob = $('span#currentJob').text();

        XNAT.xhr.post({
            url: XNAT.url.csrfUrl('REST/search?format=json'),
            data: xml,
            success: function (responseData) {
                var keyAndHeaderMap = {};

                //Add table
                var divContent = '<div class="data-table-titlerow">							';
                divContent += '	    <h3 class="data-table-title">Select elements to launch processing</h3>		';
                divContent += '	    <div class="data-table-actionsrow clearfix">							';
                divContent += '	        <span  class="textlink-sm data-table-action">					';
                divContent += '	          <select id="actionsDropdown" class="data-table-action disabled"  disabled>	';
                divContent += '	          </select>									';
                divContent += '	        </span>										';
                divContent += ' 	    <button class="btn btn-sm data-table-action disabled" id="launch-job">' +
                    'Launch job</button>	';
                divContent += '		    <button class="btn btn-sm text-error data-table-action disabled" id="kill-job">' +
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
                divContent += '	<div class="data-table-wrapper" id="div-' + tableId + '-header">';
                divContent += '	       <table id="' + tableId + '" class="clean fixed-header selectable scrollable-table data-table xnat-table" style="width: auto;">';
                divContent += '	            <thead>												';
                divContent += '		            <tr id="xnat-table-header-row1">								';
                divContent += '		                <th class="toggle-all" style="width: 45px;">						';
                divContent += '		                    <input type="checkbox" class="selectable-select-all" ' +
                    'id="toggle-all-elements" title="Toggle All" />	';
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
                let excluded = ["Scans", "Age", "Date", "Scanner", "Scans", "Type", "M/F", "Gender"];
                $.each(responseData.ResultSet.Columns, function (i, d) {
                    var header, label;
                    header = label = d.header;
                    if (!header || excluded.includes(header)) {
                        return;
                    }
                    var key = d.key.substring(0, 63);
                    if (d.key.startsWith('xnat_subjectdata_sub_project_identifier') ||
                        d.key.startsWith("sub_project_identifier") ||
                        d.key === "xnat_col_subjectdatalabel" ||
                        d.key === "xnat_subjectdata_subject_label") {
                        subjectLabelKey = key;
                    } else if (d.key.indexOf('_project_identifier_') > 0 || d.key === "label") {
                        experimentLabelKey = key;
                    }
                    // These may or may not be returned, if you need them, you'll want to modify SearchXMLBuilder
                    // else if (d.key === "xnat_subjectdata_subjectid" || d.key === "subject_id") {
                    //     subjectIdKey = key;
                    // } else if (d.key === "expt_id" || d.key === "session_id" || d.key.indexOf("session_id") > 0) {
                    //     experimentIdKey = key;
                    // }
                    keyAndHeaderMap[header] = key;
                    if (label.includes('/')) {
                        label = label.replace(/.*\//,'');
                    }
                    columnsToShow[header] = {
                        show: false,
                        label: label,
                        labelClean: label.replace(' ', '-'),
                        type: d.type,
                        pipeline: d.key.startsWith("wrk_status") && d.type === "string" &&
                            d.xPATH.replace(/[^.]*./, '') === "WRK_STATUS" //Hack
                    };
                });

                columnsToShow['Project']['show'] = !!XNAT.plugin.batchLaunch.projectId;
                columnsToShow['Project']['label'] = XNAT.app.displayNames.singular.project;
                if (columnsToShow.hasOwnProperty('Subject')) {
                    columnsToShow['Subject']['show'] = true;
                    columnsToShow['Subject']['label'] = XNAT.app.displayNames.singular.subject;
                }
                if (columnsToShow.hasOwnProperty('Experiment')) {
                    columnsToShow['Experiment']['show'] = true;
                }
                if (columnsToShow.hasOwnProperty('Scan')) {
                    columnsToShow['Scan']['show'] = true;
                }

                var rows = responseData.ResultSet.Result;

                // Dont add columns for workflows which have not been executed for the project at all
                for (var wrk_col in columnsToShow) {
                    if (columnsToShow[wrk_col]['show'] || !keyAndHeaderMap.hasOwnProperty(wrk_col)) {
                        continue;
                    }
                    $.each(rows, function (i, d) {
                        var key = keyAndHeaderMap[wrk_col], val = d[key];
                        if (val) {
                            var workFlowStatusIndx = val.indexOf("#");
                            var workFlowStatus = val.substring(0, workFlowStatusIndx);
                            if (workFlowStatus || key.startsWith("res_file") || key.startsWith("wrk_status_launch")
                                || key.startsWith("wrk_status_numrows") || key.startsWith("wrk_status_lastmod")
                                || key.startsWith("scan_type_count")) {
                                columnsToShow[wrk_col]['show'] = true;
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
                    if (!columnsToShow[header_col]['show']) {
                        continue;
                    }
                    label = columnsToShow[header_col]['label'];
                    labelClean = columnsToShow[header_col]['labelClean'];
                    var filterId = filterIdPrefix + labelClean;
                    var filterClass = filterClassPrefix + labelClean;
                    filterCssList.push(filterClass);
                    $('tr#xnat-table-header-row1').append($('<th class="left sort ' + labelClean +
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
                                        var filterClass = filterId.replace(filterIdPrefix, filterClassPrefix);
                                        var colClass = filterId.replace(filterIdPrefix, '');
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
                        $filterInput.on('keyup', function (e) {
                            var filterId = this.id;
                            var colClass = filterId.replace(filterIdPrefix, '');
                            var filterClass = filterId.replace(filterIdPrefix, filterClassPrefix);
                            var val = this.value;
                            var key = e.which;
                            // don't do anything on 'tab' keyup
                            if (key === 9) return false;
                            if (key === 27) { // key 27 = 'esc'
                                this.value = val = '';
                            }
                            if (!val || key === 8) {
                                $dataRows.removeClass(filterClass);
                            }
                            filterRows(val, colClass);
                            updateAfterFiltering($(this).parents("table"));
                        });
                    }
                    $('tr#xnat-table-header-row2').append($("<td class='" + labelClean + "'></td>").append($filterInput));

                    //Toggle columns checkbox list
                    var dropdownItemContents = XNAT.ui.ajaxTable.addColumnToggleContents(labelClean, label, true);
                    if (!currentJob && columnsToShow[header_col]['pipeline']) {
                        dropdownItemContents.push("&nbsp;");
                        dropdownItemContents.push($.spawn('a', {
                            id: label,
                            data: {job: header_col},
                            onclick: function () {
                                var job = $(this).data('job');
                                XNAT.plugin.batchLaunch.fakeFormPost({
                                    job: job,
                                    search_xml: $('#xss').val()
                                });
                            }
                        }, '[More details]'));
                    }
                    showHideList.push($.spawn("span.bl-dropdown-item", {}, dropdownItemContents));
                }

                // Toggle columns
                XNAT.ui.ajaxTable.addColumnToggle(showHideList, $container);

                // Row counts
                var nres = rows.length.toString();
                var $count = $('<div class="counts">Showing <span id="table-visible-count">' +
                    nres + '</span> of <span id="table-overall-count">' + nres + '</span> elements</div>');
                $('table#' + tableId).after($count);

                // Css for filtering
                $container.prepend($.spawn("style|type='text/css'", {},
                    $.map(filterCssList, function (e) {
                        return "tr." + e + "{display:none;}"
                    })));

                // AddDataTableRows:
                $.each(rows, function (i, d) {
                    var project, label, project_url, subject_url, expt_url;
                    var uri = d.uri, element_url = '/data' + uri + '?format=html';
                    var workflowStatus = {};
                    if (dataType === "xnat:projectData") {
                        label = d[projectLabelKey];
                        project = label;
                        project_url = element_url;
                    } else {
                        project = d.project;
                        project_url = '/data/archive/projects/' + project;
                        if (dataType === "xnat:subjectData") {
                            label = d[subjectLabelKey];
                            subject_url = element_url;
                        } else {
                            subject_url = project_url + '/subjects/' + d[subjectLabelKey];
                            if (dataType.includes("Scan")) {
                                label = d[scanLabelKey];
                                expt_url = '/data' + uri.replace(/\/scans.*$/, '?format=html');
                            } else {
                                label = d[experimentLabelKey];
                                expt_url = element_url;
                            }
                        }
                    }

                    var itemid = uri.replace(/\/archive\/[^\/]*\//, '').replace(/\/scans\//, '-');
                    var single_select_checkbox_id = "select-" + itemid;
                    var id_json = JSON.stringify({
                        uri: uri,
                        label: label,
                        project: project,
                        xsiType: dataType
                    });

                    var rowDataWithColumns = '<tr valign="top" id="element-' + itemid + '">';
                    rowDataWithColumns += '<td class="element-selector center" ' +
                        'style="width: 45px;">';
                    var inputCk = spawn('input', {
                        type: "checkbox",
                        className: "selectable-select-one",
                        id: single_select_checkbox_id,
                        value: id_json
                    });
                    rowDataWithColumns += inputCk.html;
                    rowDataWithColumns += '</td>';

                    for (var hdr in columnsToShow) {
                        if (!keyAndHeaderMap.hasOwnProperty(hdr) || !columnsToShow[hdr]['show']) {
                            continue;
                        }
                        var key = keyAndHeaderMap[hdr];
                        label = columnsToShow[hdr]['label'];
                        labelClean = columnsToShow[hdr]['labelClean'];
                        if (key === scanLabelKey && dataType.includes("Scan")) {
                            rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + XNAT.url.rootUrl(element_url) +
                                '"  target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';
                        } else if (hdr === 'Project') {
                            rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + XNAT.url.rootUrl(project_url) +
                                '" target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';
                        } else if (hdr === 'Subject') {
                            rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + XNAT.url.rootUrl(subject_url) +
                                '" target="_blank"><span  title="' + label + '">' + d[key] + '</span></a></td>';
                        } else if (hdr === 'Experiment') {
                            rowDataWithColumns += '<td class="' + labelClean + '" ><a href="' + XNAT.url.rootUrl(expt_url) +
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
                            workflowStatus[hdr] = workFlowStatus; //Needs to be empty if no status yet
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
                    pipelineWorkFlowStatus[label] = workflowStatus;
                    rowDataWithColumns += '</tr>';
                    $('tbody#xnat-table-datarows-tbody').append(rowDataWithColumns);

                });
                if (reload) {
                    // Clear cached history info
                    XNAT.plugin.batchLaunch.containerInfo = {};
                } else {
                    addActions();
                }
                XNAT.ui.ajaxTable.resizeTableCols($container.find("table#" + tableId));
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
            complete: function () {
                waitDialog.close();
            }
        });
    };

    function addActions() {
        // Since $container is not destroyed on reload, we don't want to re-run this
        $container.on('click', 'button#download', function () {
            exportTableToCSV($('table#' + tableId), "processing_data.csv");
            return false;
        });
        $container.on('click', 'button#reload', function () {
            $container.empty();
            XNAT.plugin.batchLaunch.launchTable.init(true);
        });
        $container.on('click', 'button#launch-job', function () {
            launchXnatJob();
        });
        $container.on('click', 'button#kill-job', function () {
            killXnatJob();
        });
        XNAT.plugin.batchLaunch.addClickActions($container);
    }

    function renderActionOptions() {
        var $actionsDropdown = $('#actionsDropdown');
        $actionsDropdown
            .find('option')
            .remove()
            .end()
            .append('<option value="Select">Select job</option>');
        var data = {xsiType: XNAT.plugin.batchLaunch.dataType};
        var availUrl = '/xapi/commands/available';
        if (XNAT.plugin.batchLaunch.projectId) {
            data['project'] = XNAT.plugin.batchLaunch.projectId;
        } else {
            availUrl += '/site';
        }
        var currentJob = $('span#currentJob').text();
        var loadingDialog = XNAT.ui.dialog.loading;
        loadingDialog.open();
        XNAT.xhr.getJSON({
            url: XNAT.url.rootUrl(availUrl),
            data: data,
            success: function (responseData) {
                responseData.forEach(function (availableCommand) {
                    var pipelineName = availableCommand['wrapper-name'];
                    var selected = pipelineName === currentJob;
                    var attr = selected ? {selected: 'selected'} : {};
                    if (availableCommand.enabled || selected) {
                        $('#actionsDropdown').append(spawn('option', {
                            attr: attr,
                            value: JSON.stringify({
                                'root-element-name': availableCommand['root-element-name'],
                                'wrapper-id': availableCommand['wrapper-id'],
                                'command-id': availableCommand['command-id'],
                                'wrapper-name': availableCommand['wrapper-name']
                            })
                        }, pipelineName).html);
                    } else {
                        var info = columnsToShow[pipelineName];
                        if (info && info['show'] === 1 && !currentJob) {
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
            },
            complete: function() {
                loadingDialog.close();
            }
        });

        //Load site wide pipelines for the datatype
        var loadingDialog2 = XNAT.ui.dialog.loading;
        loadingDialog2.open();
        var pipelineUrl = '/xapi/pipelines';
        if (XNAT.plugin.batchLaunch.projectId) {
            pipelineUrl += '/project/' + XNAT.plugin.batchLaunch.projectId;
        } else {
            pipelineUrl += '/site';
        }
        XNAT.xhr.getJSON({
            url: XNAT.url.rootUrl(pipelineUrl),
            data: {xsiType: XNAT.plugin.batchLaunch.dataType},
            success: function (responseData) {
                responseData.ResultSet.Result.forEach(function (configuredPipeline) {
                    var pipelineName = configuredPipeline['Name'];
                    var pipelineStepId = configuredPipeline['StepId'];
                    var attr = configuredPipeline.Path.replace(/\./g, '_') === currentJob ?
                        {selected: 'selected'} : {};
                    console.log("Adding " + pipelineName);
                    $('#actionsDropdown').append(spawn('option', {
                        attr: attr,
                        value: JSON.stringify({
                            'pipeline_name': pipelineStepId,
                            'pipeline_path': configuredPipeline['Path']
                        })
                    }, pipelineName).html);
                });
            },
            error: function (o) {
                console.log("Error " + o);
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
            },
            complete: function() {
                loadingDialog2.close();
            }
        });
    }

    function getSelectedElements() {
        var sel = {targets: [], targetLabels: []};
        $('input.selectable-select-one:checkbox:checked').each(function () {
            // Get the JSON
            var jsonData = JSON.parse($(this).val());
            sel['targets'].push(jsonData['uri']);
            sel['targetLabels'].push(jsonData['label']);
        });
        return sel;
    }

    function getSelectedJob() {
        var commandDetails = $('#actionsDropdown').find(":selected").val();
        console.log("CommandDetails: " + commandDetails);
        if (commandDetails === "Select") {
            XNAT.dialog.open({
                title: 'Please select a job',
                content: 'You must select a job',
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

    function killXnatJob() {
        var commandDetailsJsonObj = getSelectedJob();
        if (!commandDetailsJsonObj) return false;

        var sel = getSelectedElements();
        var targets = sel['targets'], targetLabels = sel['targetLabels'];

        var postConfig = {
            dataType: "json",
            beforeSend: function () {
                XNAT.ui.dialog.alert("Jobs are being terminated in the background. " +
                    "You may continue to work, refreshing the dashboard to see updated progress.");
                return true;
            },
            success: function (data) {
                var messageContent = [],
                    totalAttempts = data.successes.concat(data.failures).length,
                    successMsg = 'successfully queued to be terminated. If statuses don\'t update ' +
                        'shortly, your admin will need to review the logs to determine what went wrong.';
                if (data.failures.length > 0) {
                    messageContent.push(spawn('div.message', data.successes.length + ' of ' +
                        totalAttempts + ' jobs queued to be terminated'));
                } else if (data.successes.length > 0) {
                    messageContent.push(spawn('div.success', 'All jobs ' + successMsg));
                } else {
                    messageContent.push(spawn('div.warning', 'No jobs terminated.'));
                }

                if (data.failures.length > 0) {
                    messageContent.push(spawn('h3', {'style': {'margin-top': '2em'}}, 'Failed termination attempts'));
                    data.failures.forEach(function (failure) {
                        messageContent.push(spawn('p', {style: {'font-weight': 'bold'}}, 'Error message:'));
                        messageContent.push(spawn('pre.json', failure));
                    });
                }

                XNAT.ui.dialog.open({
                    title: 'Job termination report',
                    content: spawn('div', messageContent),
                    buttons: [
                        {
                            label: 'OK',
                            isDefault: true,
                            close: XNAT.ui.dialog.closeAll()
                        }
                    ]
                });
            },
            error: function (e) {
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
        };

        // Pipeline or container?
        let jobName;
        if (pipelineIsSelected(commandDetailsJsonObj)) {
            if (!XNAT.plugin.batchLaunch.projectId) {
                XNAT.dialog.alert('Pipelines cannot be terminated across projects');
                return false;
            }
            var pipelineName = jobName = commandDetailsJsonObj['pipeline_name'];
            postConfig['url'] = XNAT.url.restUrl('/xapi/pipelines/terminate/' + pipelineName + '/project/' +
                XNAT.plugin.batchLaunch.projectId);
            var pipelinePath = commandDetailsJsonObj['pipeline_path'];
            var dataToPost = {};
            dataToPost['Experiments'] = JSON.stringify(targets);
            dataToPost['pipelinePath'] = pipelinePath;
            postConfig['data'] = JSON.stringify(dataToPost);
            postConfig['contentType'] = "application/json; charset=utf-8";
        } else {
            jobName = commandDetailsJsonObj['wrapper-name'];
            postConfig['url'] = XNAT.url.restUrl('/xapi/workflows/' + jobName + '/killactive');
            postConfig['data'] = {'elements': targets};
        }

        // confirm dialog
        XNAT.ui.dialog.open({
            title: 'Terminate process confirmation',
            content: spawn('div', {}, [
                spawn('p', {}, 'Are you SURE you want to terminate the <strong>' + jobName +
                    '</strong> job for the following <strong>' + targets.length +
                    '</strong> elements?'),
                spawn('p', {}, '<em>Note: REVIEW THEM, this cannot be undone!</em>'),
                spawn('ul', {}, $.map(targetLabels, function (e) {
                    return spawn('li', {}, e);
                }))
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
                    action: function () {
                        if (!targets || targets.length === 0) return false;

                        // Experiments
                        var cannotTerminate = checkSelectedElementsForTermination(targetLabels, pipelineName);
                        if (cannotTerminate && cannotTerminate.length > 0) {
                            var list = "";
                            cannotTerminate.forEach(function (id) {
                                list += "<p>" + id + "</p>";
                            });
                            XNAT.dialog.open({
                                title: 'Error',
                                content: 'Jobs for the following elements(s) are not in a state that can be terminated:<br/><br/>' +
                                    list + 'Please exclude them and try again.',
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

                        $.post(postConfig);
                    }
                }
            ]
        });
    }

    function launchXnatJob() {
        var commandDetailsJsonObj = getSelectedJob();
        if (!commandDetailsJsonObj) return false;
        // Experiments
        var sel = getSelectedElements();
        var targets = sel['targets'], targetLabels = sel['targetLabels'];

        var projectId = XNAT.plugin.batchLaunch.projectId;
        var pipelineName;
        if (pipelineIsSelected(commandDetailsJsonObj)) {
            pipelineName = commandDetailsJsonObj['pipeline_name'];
        } else {
            pipelineName = commandDetailsJsonObj['wrapper-name'];
        }

        //Are there any elements in the selected list which are in any state other than Failed or Complete?
        //If this change the selected elements
        var elementsBeingProcessed = checkSelectedElementsForLaunch(targetLabels, pipelineName);
        if (elementsBeingProcessed && elementsBeingProcessed.length > 0) {
            var list = "";
            elementsBeingProcessed.forEach(function (id) {
                list += "<p>" + id + "</p>";
            });
            XNAT.dialog.open({
                title: 'Error',
                content: 'The following elements cannot be launched because they are already actively running this job:<br/><br/>'
                    + list + 'Please exclude them and relaunch.',
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
            if (pipelineIsSelected(commandDetailsJsonObj)) {
                XNAT.plugin.pipelineLaunchService.launcher.bulkLaunchPipelineDialog(pipelineName, targets, targetLabels, projectId);
            } else {
                var rootElementName = commandDetailsJsonObj['root-element-name'];
                var wrapperId = commandDetailsJsonObj['wrapper-id'];
                var commandId = commandDetailsJsonObj['command-id'];
                XNAT.plugin.containerService.launcher.bulkLaunchDialog(wrapperId,
                    rootElementName, targets, targetLabels, projectId, commandId);
            }
        }
    }

    function pipelineIsSelected(commandDetailsJsonObj) {
        var pipelineName = commandDetailsJsonObj['pipeline_name'];
        var pipelinePath = commandDetailsJsonObj['pipeline_path'];
        return pipelineName != null && pipelinePath != null;
    }

    function checkSelectedElementsForLaunch(targets, pipelineName) {
        var elementsBeingProcessed = [];
        targets.forEach(function (id) {
            if (pipelineWorkFlowStatus.hasOwnProperty(id)) {
                var wrkFlowStatus = pipelineWorkFlowStatus[id];
                if (wrkFlowStatus && wrkFlowStatus.hasOwnProperty(pipelineName)) {
                    var status = wrkFlowStatus[pipelineName];
                    if (status && !XNAT.plugin.batchLaunch.isWorkflowFailed(status)
                        && !XNAT.plugin.batchLaunch.isWorkflowComplete(status)) {
                        elementsBeingProcessed.push(id);
                    }
                }
            }
        });
        return elementsBeingProcessed;
    }

    function checkSelectedElementsForTermination(targets, pipelineName) {
        var interminableElements = [];
        targets.forEach(function (id) {
            if (pipelineWorkFlowStatus.hasOwnProperty(id)) {
                var wrkFlowStatus = pipelineWorkFlowStatus[id];
                if (wrkFlowStatus && wrkFlowStatus.hasOwnProperty(pipelineName)) {
                    var status = wrkFlowStatus[pipelineName];
                    if (status && !XNAT.plugin.batchLaunch.canTerminateWorkflow(status)) {
                        interminableElements.push(id);
                    }
                }
            }
        });
        return interminableElements;
    }

    XNAT.plugin.batchLaunch.launchTable.showAllJobsBtnAction = function () {
        XNAT.plugin.batchLaunch.fakeFormPost({
            search_xml: $('#xss').val()
        });
    };

    XNAT.plugin.batchLaunch.fakeFormPost = function (fields) {
        var $form = $('<form>', {
            action: searchForm ? XNAT.url.csrfUrl(url) : XNAT.url.rootUrl(url),
            method: 'post'
        });
        $.each(fields, function (key, val) {
            $('<input>').attr({
                type: "hidden",
                name: key,
                value: val
            }).appendTo($form);
        });
        $form.appendTo('body').submit();
    }
}));

